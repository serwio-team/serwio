import { CalendarPlus, Loader2, UserCog, XCircle } from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { ApiError, errorMessage } from '../api';
import { useAllBranches, useCancelVisit, useOrderVisits, useSaveVisitStaffing, useVisitCandidates } from '../queries';
import type { ServiceVisitResponse, VisitTechnicianResponse } from '../types';
import { ErrorState } from './DataState';
import { SelectField } from './Field';
import { ConfirmDialog, Modal } from './Modal';
import { useToast } from './Toast';
import { ProposalResponse } from './VisitProposal';
import { formatMoment, formatRange, offsetLabel } from '../visitTime';

const PLANNABLE_ORDER_STATUSES = ['ASSIGNED', 'REVISIT_REQUIRED'];
const OPEN_VISIT_STATUSES = ['DRAFT', 'CONFIRMED', 'IN_PROGRESS'];

const VISIT_STATUS: Record<string, { label: string; tone: string }> = {
  DRAFT: { label: 'Uzgadnianie terminu', tone: 'warning' },
  CONFIRMED: { label: 'Termin potwierdzony', tone: 'success' },
  IN_PROGRESS: { label: 'W trakcie', tone: 'info' },
  COMPLETED: { label: 'Zakończona', tone: 'neutral' },
  CANCELLED: { label: 'Anulowana', tone: 'neutral' },
};

const OUTCOME_LABELS: Record<string, string> = {
  COMPLETED: 'Wykonane',
  REVISIT_REQUIRED: 'Wymaga ponownej wizyty',
};

function VisitStatusBadge({ status }: { status: string }) {
  const { label, tone } = VISIT_STATUS[status] ?? { label: status, tone: 'neutral' };
  return <span className={`badge badge-${tone}`}>{label}</span>;
}

function termDescription(visit: ServiceVisitResponse, timeZone: string, owner: boolean): string {
  const proposal = visit.currentProposal;
  switch (visit.status) {
    case 'DRAFT': {
      if (!proposal) return owner ? 'Czekasz na propozycję terminu od koordynatora.' : 'Czeka na propozycję terminu od koordynatora.';
      const range = formatRange(proposal.startsAt, proposal.endsAt, timeZone);
      if (proposal.proposedBy === 'OWNER') {
        return owner
          ? `Twoja propozycja: ${range}. Czekasz na odpowiedź koordynatora.`
          : `Właściciel zaproponował: ${range}. Czeka na odpowiedź koordynatora.`;
      }
      return owner ? 'Czeka na Twoją odpowiedź.' : `Koordynator zaproponował: ${range}. Czeka na odpowiedź właściciela.`;
    }
    case 'CONFIRMED':
      return visit.startsAt && visit.endsAt ? formatRange(visit.startsAt, visit.endsAt, timeZone) : 'Termin potwierdzony.';
    case 'IN_PROGRESS':
      return visit.startedAt ? `Rozpoczęta ${formatMoment(visit.startedAt, timeZone)}.` : 'Wizyta trwa.';
    case 'COMPLETED': {
      const outcome = visit.outcome ? OUTCOME_LABELS[visit.outcome] ?? visit.outcome : 'Zakończona';
      return visit.completedAt ? `${outcome}, ${formatMoment(visit.completedAt, timeZone)}.` : `${outcome}.`;
    }
    default:
      return visit.startsAt && visit.endsAt
        ? `Anulowano termin ${formatRange(visit.startsAt, visit.endsAt, timeZone)}.`
        : 'Anulowano przed potwierdzeniem terminu.';
  }
}

export function OrderVisitsCard({ orderId, orderStatus, role }: { orderId: string; orderStatus: string; role: string }) {
  const owner = role === 'ASSET_OWNER';
  const visits = useOrderVisits(orderId);
  const cancel = useCancelVisit(orderId);
  const toast = useToast();
  const [staffing, setStaffing] = useState<{ visit?: ServiceVisitResponse } | null>(null);
  const [toCancel, setToCancel] = useState<ServiceVisitResponse | null>(null);
  const data = visits.data;
  const list = data?.visits ?? [];
  const [current, ...history] = list;
  const hasOpenVisit = list.some((v) => OPEN_VISIT_STATUSES.includes(v.status));
  const canPlan = !owner && Boolean(data) && PLANNABLE_ORDER_STATUSES.includes(orderStatus) && !hasOpenVisit;
  const proposal = current?.status === 'DRAFT' && current.currentProposal?.proposedBy === 'COORDINATOR'
    ? current.currentProposal
    : null;

  function confirmCancel() {
    if (!toCancel) return;
    cancel.mutate(toCancel.id, {
      onSuccess: () => toast.success('Anulowano wizytę. Możesz zaplanować nową.'),
      onError: (e) => toast.error(e instanceof ApiError && e.status === 409
        ? `${e.message} Wyświetlono aktualne dane.`
        : errorMessage(e)),
      onSettled: () => setToCancel(null),
    });
  }

  let body;
  if (visits.isPending) {
    body = <span className="skeleton skeleton-line" />;
  } else if (visits.isError) {
    body = <ErrorState message={errorMessage(visits.error)} onRetry={() => void visits.refetch()} />;
  } else if (!current) {
    body = (
      <p className="muted">
        {owner
          ? 'Firma serwisowa nie zaplanowała jeszcze wizyty. Gdy koordynator zaproponuje termin, odpowiesz na niego tutaj.'
          : orderStatus === 'NEW' ? 'Najpierw przydziel zlecenie oddziałom lub serwisantom.' : 'Nie zaplanowano jeszcze wizyty.'}
      </p>
    );
  } else {
    body = (
      <>
        <VisitItem
          visit={current}
          number={list.length}
          timeZone={data!.timezone}
          owner={owner}
          onEdit={owner ? undefined : () => setStaffing({ visit: current })}
          onCancel={owner ? undefined : () => setToCancel(current)}
          response={proposal && owner ? (
            <ProposalResponse
              key={proposal.id}
              orderId={orderId}
              visit={current}
              proposal={proposal}
              timeZone={data!.timezone}
              availability={data!.availability}
            />
          ) : null}
        />
        {history.length > 0 && (
          <details className="visit-history">
            <summary>Wcześniejsze wizyty ({history.length})</summary>
            {history.map((v, index) => (
              <VisitItem key={v.id} visit={v} number={history.length - index} timeZone={data!.timezone} owner={owner} />
            ))}
          </details>
        )}
      </>
    );
  }

  return (
    <section className="card details-card assignment-card">
      <header className="assignment-header">
        <h2 className="details-heading">Wizyty</h2>
        {canPlan && (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setStaffing({})}>
            <CalendarPlus size={14} aria-hidden /> {list.length > 0 ? 'Zaplanuj kolejną wizytę' : 'Zaplanuj wizytę'}
          </button>
        )}
      </header>
      {data && (
        <p className="field-hint visit-zone">
          Godziny w strefie lokalizacji: {data.timezone} ({offsetLabel(data.timezone)})
        </p>
      )}
      {body}
      {staffing && <StaffingModal orderId={orderId} visit={staffing.visit} onClose={() => setStaffing(null)} />}
      {toCancel && (
        <ConfirmDialog
          title="Anulować wizytę?"
          message={toCancel.status === 'CONFIRMED' && toCancel.startsAt && toCancel.endsAt && data
            ? <>Termin <strong>{formatRange(toCancel.startsAt, toCancel.endsAt, data.timezone)}</strong> zostanie zwolniony. Potem zaplanujesz nową wizytę z inną obsadą lub terminem.</>
            : <>Uzgadnianie terminu zostanie przerwane. Potem zaplanujesz nową wizytę.</>}
          confirmLabel="Anuluj wizytę"
          pendingLabel="Anulowanie…"
          cancelLabel="Wróć"
          pending={cancel.isPending}
          onConfirm={confirmCancel}
          onCancel={() => setToCancel(null)}
        />
      )}
    </section>
  );
}

interface VisitItemProps {
  visit: ServiceVisitResponse;
  number: number;
  timeZone: string;
  owner: boolean;
  onEdit?: () => void;
  onCancel?: () => void;
  response?: ReactNode;
}

function VisitItem({ visit, number, timeZone, owner, onEdit, onCancel, response }: VisitItemProps) {
  const upcoming = visit.status === 'CONFIRMED' && visit.startsAt !== null && new Date(visit.startsAt) > new Date();
  const staff = [...visit.technicians].sort((a, b) =>
    Number(b.id === visit.coordinatorTechnicianId) - Number(a.id === visit.coordinatorTechnicianId)
    || a.displayName.localeCompare(b.displayName, 'pl'));
  const actions = onEdit && onCancel && (visit.status === 'DRAFT' || upcoming);

  return (
    <div className="visit">
      <div className="visit-head">
        <span className="visit-title">Wizyta {number}</span>
        <VisitStatusBadge status={visit.status} />
      </div>
      <dl className="details-list visit-grid">
        <div>
          <dt>Obsada</dt>
          <dd>
            <span className="chip-list">
              {staff.map((t) => (
                <span key={t.id} className="chip">
                  {t.displayName}
                  {t.id === visit.coordinatorTechnicianId && <span className="chip-sub">Koordynator</span>}
                </span>
              ))}
            </span>
          </dd>
        </div>
        <div>
          <dt>{visit.status === 'CONFIRMED' ? 'Uzgodniony termin' : 'Termin'}</dt>
          <dd className="visit-term">{termDescription(visit, timeZone, owner)}</dd>
        </div>
      </dl>
      {response}
      {actions && (
        <div className="visit-actions">
          {visit.status === 'DRAFT' && (
            <button type="button" className="btn btn-secondary btn-sm" onClick={onEdit}>
              <UserCog size={14} aria-hidden /> Zmień obsadę
            </button>
          )}
          <button type="button" className="btn btn-secondary btn-sm" onClick={onCancel}>
            <XCircle size={14} aria-hidden /> Anuluj wizytę
          </button>
        </div>
      )}
      {onEdit && upcoming && (
        <p className="field-hint">Obsada i termin są zablokowane. Aby je zmienić, anuluj wizytę i zaplanuj nową.</p>
      )}
      {onEdit && visit.status === 'CONFIRMED' && !upcoming && (
        <p className="field-hint">Termin już się rozpoczął, więc wizyty nie można anulować.</p>
      )}
    </div>
  );
}

function StaffingModal({ orderId, visit, onClose }: { orderId: string; visit?: ServiceVisitResponse; onClose: () => void }) {
  const candidates = useVisitCandidates(orderId);

  let body;
  if (candidates.isPending) {
    body = <div className="modal-loading"><Loader2 size={18} className="spin" aria-label="Ładowanie" /></div>;
  } else if (candidates.isError) {
    body = (
      <div className="modal-body">
        <ErrorState message={errorMessage(candidates.error)} onRetry={() => void candidates.refetch()} />
      </div>
    );
  } else {
    body = <StaffingForm orderId={orderId} visit={visit} candidates={candidates.data} onClose={onClose} />;
  }

  return (
    <Modal
      title={visit ? 'Obsada wizyty' : 'Zaplanuj wizytę'}
      description="Wybierz wykonawców spośród uprawnionych serwisantów i wskaż koordynatora. Koordynator zaproponuje termin."
      onClose={onClose}
    >
      {body}
    </Modal>
  );
}

interface StaffingFormProps {
  orderId: string;
  visit?: ServiceVisitResponse;
  candidates: VisitTechnicianResponse[];
  onClose: () => void;
}

function StaffingForm({ orderId, visit, candidates, onClose }: StaffingFormProps) {
  const branches = useAllBranches();
  const [selected, setSelected] = useState(() => new Set(visit?.technicians.map((t) => t.id) ?? []));
  const [coordinatorId, setCoordinatorId] = useState(visit?.coordinatorTechnicianId ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string[]>>({});
  const save = useSaveVisitStaffing(orderId);
  const toast = useToast();

  const eligible = new Set(candidates.map((c) => c.id));
  const people = [...candidates, ...(visit?.technicians.filter((t) => !eligible.has(t.id)) ?? [])]
    .sort((a, b) => a.displayName.localeCompare(b.displayName, 'pl'));
  const chosen = people.filter((p) => selected.has(p.id) && eligible.has(p.id));
  const blocked = people.filter((p) => selected.has(p.id) && !eligible.has(p.id));
  const coordinator = chosen.some((p) => p.id === coordinatorId) ? coordinatorId : chosen.length === 1 ? chosen[0].id : '';
  const branchName = (id: string) => branches.data?.find((b) => b.id === id)?.name;

  function toggle(id: string) {
    setSelected((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
    setFieldErrors((f) => ({ ...f, technicianIds: [] }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const errors: Record<string, string[]> = {};
    if (selected.size === 0) errors.technicianIds = ['Wybierz co najmniej jednego wykonawcę.'];
    else if (blocked.length > 0) errors.technicianIds = ['Usuń z obsady osoby bez uprawnień do zlecenia.'];
    if (!coordinator) errors.coordinatorTechnicianId = ['Wybierz koordynatora spośród wykonawców.'];
    if (Object.keys(errors).length > 0) {
      save.reset();
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    save.mutate({ visitId: visit?.id, data: { technicianIds: [...selected], coordinatorTechnicianId: coordinator } }, {
      onSuccess: () => {
        toast.success(visit ? 'Zapisano obsadę wizyty.' : 'Zaplanowano wizytę. Koordynator zaproponuje termin.');
        onClose();
      },
      onError: (e) => {
        if (e instanceof ApiError && (e.status === 409 || e.status === 404)) {
          toast.error(`${e.message} Wyświetlono aktualne dane.`);
          onClose();
        } else if (e instanceof ApiError) {
          setFieldErrors(e.fieldErrors);
        }
      },
    });
  }

  const showAlert = save.isError && Object.values(fieldErrors).every((list) => list.length === 0);
  const listErrors = fieldErrors.technicianIds ?? [];

  return (
    <form onSubmit={submit} noValidate>
      <div className="modal-body">
        {showAlert && <div className="inline-alert inline-alert-error" role="alert">{errorMessage(save.error)}</div>}
        {visit?.currentProposal && (
          <div className="inline-alert inline-alert-info">
            Zmiana obsady lub koordynatora wycofa bieżącą propozycję terminu.
          </div>
        )}

        <div className={`assign-field${listErrors.length ? ' assign-field-invalid' : ''}`}>
          <span className="field-label" id="visit-staff-label">Wykonawcy</span>
          {people.length === 0 ? (
            <p className="muted">
              Nikt nie ma jeszcze uprawnień do tego zlecenia. Przydziel zlecenie oddziałom lub serwisantom z aktywnym kontem.
            </p>
          ) : (
            <ul className="assign-tree assign-flat" aria-labelledby="visit-staff-label">
              {people.map((p) => {
                const allowed = eligible.has(p.id);
                const checked = selected.has(p.id);
                return (
                  <li key={p.id} className={checked ? 'assign-selected' : undefined}>
                    <label className="assign-check">
                      <input type="checkbox" checked={checked} disabled={!allowed && !checked} onChange={() => toggle(p.id)} />
                      <span className="assign-text">
                        <span className="assign-name">{p.displayName}</span>
                        <span className={`assign-hint${allowed ? '' : ' assign-warning'}`}>
                          {allowed ? branchName(p.branchId) ?? ' ' : 'Brak uprawnień do zlecenia, usuń z obsady'}
                        </span>
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
          {listErrors.length > 0 && <span className="field-error" role="alert">{listErrors.join(' ')}</span>}
        </div>

        <SelectField
          label="Koordynator wizyty"
          name="coordinatorTechnicianId"
          value={coordinator}
          onChange={(v) => { setCoordinatorId(v); setFieldErrors((f) => ({ ...f, coordinatorTechnicianId: [] })); }}
          errors={fieldErrors.coordinatorTechnicianId}
          required
          disabled={chosen.length === 0}
        >
          <option value="" disabled>{chosen.length === 0 ? 'Najpierw wybierz wykonawców' : 'Wybierz koordynatora'}</option>
          {chosen.map((p) => <option key={p.id} value={p.id}>{p.displayName}</option>)}
        </SelectField>

        <div className="assign-summary" aria-live="polite">
          <span className="order-preview-label">Podsumowanie</span>
          <span>Wykonawcy: <strong>{chosen.length > 0 ? chosen.map((p) => p.displayName).join(', ') : 'nie wybrano'}</strong></span>
          <span>Koordynator: <strong>{chosen.find((p) => p.id === coordinator)?.displayName ?? 'nie wybrano'}</strong></span>
        </div>
      </div>
      <footer className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onClose} disabled={save.isPending}>Anuluj</button>
        <button type="submit" className="btn btn-primary" disabled={save.isPending || people.length === 0}>
          {save.isPending && <Loader2 size={14} className="spin" aria-hidden />}
          {save.isPending ? 'Zapisywanie…' : visit ? 'Zapisz obsadę' : 'Zaplanuj wizytę'}
        </button>
      </footer>
    </form>
  );
}
