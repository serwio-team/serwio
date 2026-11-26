import { CalendarClock, Loader2, RefreshCw } from 'lucide-react';
import { ApiError, errorMessage } from '../api';
import { editableWeek, WEEKDAYS } from '../availability';
import { useCurrentTechnician, useNegotiateVisit, useServiceVisits } from '../queries';
import type { ServiceVisit, ServiceVisitsResponse, VisitNegotiation } from '../types';
import { EmptyState, ErrorState } from './DataState';
import { VisitTimeForm } from './VisitTimeForm';

const STATUS_LABELS: Record<ServiceVisit['status'], string> = {
  DRAFT: 'W uzgodnieniu',
  CONFIRMED: 'Potwierdzona',
  IN_PROGRESS: 'W realizacji',
  COMPLETED: 'Zakończona',
  CANCELLED: 'Anulowana',
};

export function VisitScheduling({ orderId, userId }: { orderId: string; userId: string }) {
  const profile = useCurrentTechnician();
  const query = useServiceVisits(userId, orderId);
  const negotiation = useNegotiateVisit(userId, orderId);
  const apiError = negotiation.error instanceof ApiError ? negotiation.error : undefined;
  const { data } = query;

  return (
    <section className="visit-scheduling" aria-labelledby="visit-scheduling-title">
      <header className="visit-heading">
        <h2 id="visit-scheduling-title">Uzgadnianie terminu wizyty</h2>
        {data && (
          <button type="button" className="btn btn-secondary btn-sm"
            disabled={query.isFetching || negotiation.isPending}
            onClick={() => {
              negotiation.reset();
              void query.refetch();
            }}>
            <RefreshCw size={14} aria-hidden /> Odśwież
          </button>
        )}
      </header>
      {negotiation.error && (
        <p role="alert" className="inline-alert inline-alert-error">
          {errorMessage(negotiation.error)}
          {apiError?.code === 'STALE_PROPOSAL' && ' Sprawdź bieżącą propozycję.'}
        </p>
      )}
      {query.isError ? (
        <div className="card">
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        </div>
      ) : !data ? (
        <div className="card details-card visit-loading" role="status">
          <Loader2 size={16} className="spin" aria-hidden /> Ładowanie wizyt i dostępności…
        </div>
      ) : (
        <div className="details-layout visit-layout">
          <WeeklyAvailability availability={data.availability} timezone={data.timezone} />
          <div className="visit-list">
            {profile.isError && data.visits.some((visit) => visit.status === 'DRAFT') && (
              <div className="card">
                <ErrorState message="Nie udało się sprawdzić, czy jesteś koordynatorem wizyty."
                  onRetry={() => void profile.refetch()} />
              </div>
            )}
            {data.visits.length === 0 && (
              <div className="card">
                <EmptyState icon={CalendarClock} title="Brak wizyt"
                  description="Wizyta pojawi się po zaplanowaniu obsady przez firmę serwisową." />
              </div>
            )}
            {data.visits.map((visit) => (
              <VisitCard key={visit.id} visit={visit} timezone={data.timezone}
                canNegotiate={profile.isSuccess && profile.data.id === visit.coordinatorTechnicianId}
                pending={negotiation.isPending}
                fieldErrors={negotiation.variables?.visitId === visit.id ? apiError?.fieldErrors ?? {} : {}}
                onChange={negotiation.reset} onAction={negotiation.mutate} />
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

function WeeklyAvailability({ availability, timezone }: Pick<ServiceVisitsResponse, 'availability' | 'timezone'>) {
  return (
    <section className="card details-card availability-summary">
      <h3 className="details-heading">Dostępność tygodniowa</h3>
      <p>Strefa czasowa: <strong>{timezone}</strong></p>
      <dl>
        {editableWeek(availability).map(({ dayOfWeek, intervals }) => (
          <div key={dayOfWeek}>
            <dt>{WEEKDAYS[dayOfWeek - 1]}</dt>
            <dd>{intervals.map(({ from, to }) => `${from}–${to}`).join(', ') || 'Niedostępne'}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

interface VisitCardProps {
  visit: ServiceVisit;
  timezone: string;
  canNegotiate: boolean;
  pending: boolean;
  fieldErrors: Record<string, string[]>;
  onChange: () => void;
  onAction: (action: VisitNegotiation) => void;
}

function VisitCard({ visit, timezone, canNegotiate, pending, fieldErrors, onChange, onAction }: VisitCardProps) {
  const proposal = visit.currentProposal;
  const isDraft = visit.status === 'DRAFT';
  const canReply = canNegotiate && isDraft && proposal?.proposedBy === 'OWNER';
  const canPropose = canNegotiate && isDraft && !proposal;

  return (
    <section className="card details-card visit-card">
      <div className="visit-heading">
        <h3>Wizyta</h3>
        <span className="badge badge-neutral">{STATUS_LABELS[visit.status]}</span>
      </div>
      {proposal && (
        <div>
          <h4>Bieżąca {proposal.proposedBy === 'OWNER' ? 'propozycja właściciela' : 'propozycja koordynatora'}</h4>
          <VisitRange startsAt={proposal.startsAt} endsAt={proposal.endsAt} timezone={timezone} />
          {isDraft && proposal.proposedBy === 'COORDINATOR' && (
            <p className="muted">Oczekuje na odpowiedź właściciela.</p>
          )}
        </div>
      )}
      {visit.startsAt && visit.endsAt && (
        <div>
          <h4>{visit.status === 'CANCELLED' ? 'Termin anulowanej wizyty' : 'Potwierdzony termin'}</h4>
          <VisitRange startsAt={visit.startsAt} endsAt={visit.endsAt} timezone={timezone} />
        </div>
      )}
      {isDraft && !proposal && !canPropose && <p className="muted">Oczekuje na propozycję koordynatora.</p>}
      {canReply && (
        <div className="visit-actions">
          <button type="button" className="btn btn-primary" disabled={pending}
            onClick={() => onAction({ action: 'accept', visitId: visit.id, proposalId: proposal.id })}>
            Zaakceptuj termin
          </button>
          <button type="button" className="btn btn-secondary" disabled={pending}
            onClick={() => onAction({ action: 'reject', visitId: visit.id, proposalId: proposal.id })}>
            Odrzuć termin
          </button>
        </div>
      )}
      {(canPropose || canReply) && (
        <VisitTimeForm key={proposal?.id ?? 'new'} timezone={timezone} counter={canReply}
          pending={pending} fieldErrors={fieldErrors} onChange={onChange}
          onSubmit={(time) => onAction(proposal
            ? { action: 'counter', visitId: visit.id, proposalId: proposal.id, time }
            : { action: 'propose', visitId: visit.id, time })} />
      )}
    </section>
  );
}

function VisitRange({ startsAt, endsAt, timezone }: { startsAt: string; endsAt: string; timezone: string }) {
  const formatter = new Intl.DateTimeFormat('pl-PL', {
    timeZone: timezone, year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', timeZoneName: 'shortOffset',
  });
  return (
    <dl className="visit-range">
      <div><dt>Od</dt><dd><time dateTime={startsAt}>{formatter.format(new Date(startsAt))}</time></dd></div>
      <div><dt>Do</dt><dd><time dateTime={endsAt}>{formatter.format(new Date(endsAt))}</time></dd></div>
    </dl>
  );
}
