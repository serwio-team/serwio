import { CalendarClock, Loader2, RefreshCw } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { ApiError, errorMessage } from '../api';
import { editableWeek, WEEKDAYS } from '../availability';
import { useCurrentTechnician, useServiceVisits, useUpdateServiceVisit } from '../queries';
import type { ServiceVisitResponse, ServiceVisitsResponse, VisitAction, VisitOutcome } from '../types';
import { EmptyState, ErrorState } from './DataState';
import { SelectField } from './Field';
import { VisitTimeForm } from './VisitTimeForm';

const STATUS_LABELS: Record<ServiceVisitResponse['status'], string> = {
  DRAFT: 'W uzgodnieniu',
  CONFIRMED: 'Potwierdzona',
  IN_PROGRESS: 'W realizacji',
  COMPLETED: 'Zakończona',
  CANCELLED: 'Anulowana',
};

const OUTCOME_LABELS: Record<VisitOutcome, string> = {
  COMPLETED: 'Wykonane',
  REVISIT_REQUIRED: 'Wymaga ponownej wizyty',
};

export function VisitScheduling({ orderId, userId, orderStatus, onRefreshOrder }: {
  orderId: string;
  userId: string;
  orderStatus: string;
  onRefreshOrder: () => void;
}) {
  const profile = useCurrentTechnician();
  const query = useServiceVisits(userId, orderId);
  const update = useUpdateServiceVisit(userId, orderId);
  const apiError = update.error instanceof ApiError ? update.error : undefined;
  const { data } = query;

  return (
    <section className="visit-scheduling" aria-labelledby="visit-scheduling-title">
      <header className="visit-heading">
        <h2 id="visit-scheduling-title">Wizyty serwisowe</h2>
        {data && (
          <button type="button" className="btn btn-secondary btn-sm"
            disabled={query.isFetching || update.isPending}
            onClick={() => {
              update.reset();
              onRefreshOrder();
              void query.refetch();
            }}>
            <RefreshCw size={14} aria-hidden /> Odśwież
          </button>
        )}
      </header>
      {update.isPending && <p role="status" className="inline-alert inline-alert-info">Zapisywanie zmian…</p>}
      {update.error && (
        <p role="alert" className="inline-alert inline-alert-error">
          {errorMessage(update.error)}
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
            {profile.isError && data.visits.some(({ status }) =>
              status === 'DRAFT' || status === 'CONFIRMED' || status === 'IN_PROGRESS') && (
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
              <VisitCard key={visit.id} visit={visit} timezone={data.timezone} orderStatus={orderStatus}
                isCoordinator={profile.isSuccess && profile.data.id === visit.coordinatorTechnicianId}
                pending={update.isPending}
                fieldErrors={update.variables?.visitId === visit.id ? apiError?.fieldErrors ?? {} : {}}
                onChange={update.reset} onAction={update.mutate} />
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
  visit: ServiceVisitResponse;
  timezone: string;
  orderStatus: string;
  isCoordinator: boolean;
  pending: boolean;
  fieldErrors: Record<string, string[]>;
  onChange: () => void;
  onAction: (action: VisitAction) => void;
}

function VisitCard({ visit, timezone, orderStatus, isCoordinator, pending, fieldErrors, onChange, onAction }: VisitCardProps) {
  const proposal = visit.currentProposal;
  const isDraft = visit.status === 'DRAFT';
  const canReply = isCoordinator && isDraft && proposal?.proposedBy === 'OWNER';
  const canPropose = isCoordinator && isDraft && !proposal;
  const canStart = isCoordinator && visit.status === 'CONFIRMED' && orderStatus === 'VISIT_CONFIRMED';
  const canComplete = isCoordinator && visit.status === 'IN_PROGRESS' && orderStatus === 'IN_PROGRESS';

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
      {visit.outcome && (
        <div>
          <h4>Wynik wizyty</h4>
          <p>{OUTCOME_LABELS[visit.outcome]}</p>
        </div>
      )}
      {canStart && (
        <div className="visit-actions">
          <button type="button" className="btn btn-primary" disabled={pending}
            onClick={() => onAction({ action: 'start', visitId: visit.id })}>
            Rozpocznij wizytę
          </button>
        </div>
      )}
      {canComplete && (
        <VisitCompletionForm pending={pending} errors={fieldErrors.outcome} onChange={onChange}
          onSubmit={(outcome) => onAction({ action: 'complete', visitId: visit.id, outcome })} />
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

function VisitCompletionForm({ pending, errors, onChange, onSubmit }: {
  pending: boolean;
  errors?: string[];
  onChange: () => void;
  onSubmit: (outcome: VisitOutcome) => void;
}) {
  const [outcome, setOutcome] = useState('');

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!pending && (outcome === 'COMPLETED' || outcome === 'REVISIT_REQUIRED')) onSubmit(outcome);
  }

  return (
    <form className="visit-form" onSubmit={submit}>
      <SelectField name="visitOutcome" label="Wynik wizyty" required disabled={pending} value={outcome} errors={errors}
        onChange={(value) => {
          setOutcome(value);
          onChange();
        }}>
        <option value="">Wybierz wynik</option>
        {Object.entries(OUTCOME_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </SelectField>
      <button type="submit" className="btn btn-primary" disabled={pending || !outcome}>Zakończ wizytę</button>
    </form>
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
