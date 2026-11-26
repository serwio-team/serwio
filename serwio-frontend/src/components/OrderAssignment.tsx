import { ChevronDown, ChevronRight, Loader2, Users } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { ApiError, errorMessage } from '../api';
import { useAllBranches, useAllTechnicians, useOrderAssignment, useSaveOrderAssignment } from '../queries';
import type { OrderAssignmentResponse, ServiceBranchResponse, TechnicianResponse } from '../types';
import { ErrorState } from './DataState';
import { SelectField } from './Field';
import { Modal } from './Modal';
import { serviceOrderStatusLabel } from './StatusBadge';
import { useToast } from './Toast';

const ASSIGNABLE_STATUSES = ['NEW', 'ASSIGNED', 'AWAITING_SCHEDULING', 'REVISIT_REQUIRED'];

const assignable = (t: TechnicianResponse) => t.active && t.accountStatus === 'ACTIVE';

function technicianIssue(t: TechnicianResponse): string | null {
  if (!t.active || t.accountStatus === 'INACTIVE') return 'Konto nieaktywne';
  if (t.accountStatus === 'PENDING') return 'Konto oczekuje na aktywację';
  return null;
}

function people(n: number): string {
  if (n === 1) return '1 serwisant';
  const few = n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14);
  return `${n} ${few ? 'serwisanci' : 'serwisantów'}`;
}

export function OrderAssignmentCard({ orderId, orderStatus }: { orderId: string; orderStatus: string }) {
  const assignment = useOrderAssignment(orderId);
  const technicians = useAllTechnicians();
  const branches = useAllBranches();
  const [editing, setEditing] = useState(false);
  const editable = ASSIGNABLE_STATUSES.includes(orderStatus);
  const data = assignment.data;
  const empty = data && data.branches.length === 0 && data.technicians.length === 0;

  const branchName = (id: string) =>
    data?.branches.find((b) => b.id === id)?.name ?? branches.data?.find((b) => b.id === id)?.name;
  const coordinator = data?.coordinatorTechnicianId
    ? data.technicians.find((t) => t.id === data.coordinatorTechnicianId)
      ?? technicians.data?.find((t) => t.id === data.coordinatorTechnicianId)
    : undefined;

  const action = data && editable ? (
    <button type="button" className={`btn ${empty ? 'btn-primary' : 'btn-secondary'} btn-sm`} onClick={() => setEditing(true)}>
      <Users size={14} aria-hidden /> {empty ? 'Przydziel' : 'Zmień przydział'}
    </button>
  ) : null;

  let body;
  if (assignment.isPending) {
    body = <span className="skeleton skeleton-line" />;
  } else if (assignment.isError) {
    body = <ErrorState message={errorMessage(assignment.error)} onRetry={() => void assignment.refetch()} />;
  } else if (empty) {
    body = <p className="muted">Zlecenie czeka na przydział oddziałów lub serwisantów.</p>;
  } else {
    body = (
      <dl className="details-list assignment-list">
        <div>
          <dt>Oddziały</dt>
          <dd>
            {data!.branches.length > 0
              ? <span className="chip-list">{data!.branches.map((b) => <span key={b.id} className="chip">{b.name}</span>)}</span>
              : <span className="muted">Brak</span>}
          </dd>
        </div>
        <div>
          <dt>Serwisanci</dt>
          <dd>
            {data!.technicians.length > 0 ? (
              <span className="chip-list">
                {data!.technicians.map((t) => (
                  <span key={t.id} className="chip">
                    {t.displayName}
                    {branchName(t.branchId) && <span className="chip-sub">{branchName(t.branchId)}</span>}
                  </span>
                ))}
              </span>
            ) : <span className="muted">Brak</span>}
          </dd>
        </div>
        <div>
          <dt>Koordynator</dt>
          <dd>{coordinator?.displayName ?? (technicians.isPending ? <span className="skeleton skeleton-line" /> : 'Nie wybrano')}</dd>
        </div>
      </dl>
    );
  }

  return (
    <section className="card details-card assignment-card">
      <header className="assignment-header">
        <h2 className="details-heading">Przydział</h2>
        {action}
      </header>
      {body}
      {data && !editable && (
        <p className="field-hint assignment-note">Przydział można zmienić tylko przed potwierdzeniem terminu wizyty.</p>
      )}
      {editing && data && <AssignmentModal assignment={data} onClose={() => setEditing(false)} />}
    </section>
  );
}

function AssignmentModal({ assignment, onClose }: { assignment: OrderAssignmentResponse; onClose: () => void }) {
  const branches = useAllBranches();
  const technicians = useAllTechnicians();
  const loadError = branches.error ?? technicians.error;

  let body;
  if (branches.isPending || technicians.isPending) {
    body = <div className="modal-loading"><Loader2 size={18} className="spin" aria-label="Ładowanie" /></div>;
  } else if (loadError) {
    body = (
      <div className="modal-body">
        <ErrorState
          message={errorMessage(loadError)}
          onRetry={() => { void branches.refetch(); void technicians.refetch(); }}
        />
      </div>
    );
  } else {
    body = <AssignmentForm assignment={assignment} branches={branches.data!} technicians={technicians.data!} onClose={onClose} />;
  }

  return (
    <Modal
      title="Przydział zlecenia"
      description="Wybierz całe oddziały lub pojedynczych serwisantów i wskaż koordynatora."
      onClose={onClose}
    >
      {body}
    </Modal>
  );
}

interface AssignmentFormProps {
  assignment: OrderAssignmentResponse;
  branches: ServiceBranchResponse[];
  technicians: TechnicianResponse[];
  onClose: () => void;
}

function AssignmentForm({ assignment, branches, technicians, onClose }: AssignmentFormProps) {
  const [branchIds, setBranchIds] = useState(() => new Set(assignment.branches.map((b) => b.id)));
  const [technicianIds, setTechnicianIds] = useState(() => new Set(assignment.technicians.map((t) => t.id)));
  const [coordinatorId, setCoordinatorId] = useState(assignment.coordinatorTechnicianId ?? '');
  const [expanded, setExpanded] = useState(() => new Set(assignment.technicians.map((t) => t.branchId)));
  const [fieldErrors, setFieldErrors] = useState<Record<string, string[]>>({});
  const save = useSaveOrderAssignment(assignment.serviceOrderId);
  const toast = useToast();

  const visibleBranches = branches
    .filter((b) => b.active || branchIds.has(b.id))
    .sort((a, b) => a.name.localeCompare(b.name, 'pl'));
  const staffOf = (branchId: string) =>
    technicians
      .filter((t) => t.branchId === branchId && (t.active || technicianIds.has(t.id)))
      .sort((a, b) => a.displayName.localeCompare(b.displayName, 'pl'));

  const covered = technicians.filter((t) => assignable(t) && (technicianIds.has(t.id) || branchIds.has(t.branchId)));
  const candidates = [...covered].sort((a, b) => a.displayName.localeCompare(b.displayName, 'pl'));
  const coordinator = candidates.some((t) => t.id === coordinatorId)
    ? coordinatorId
    : candidates.length === 1 ? candidates[0].id : '';
  const branchById = new Map(branches.map((b) => [b.id, b]));
  const inactiveBranches = [...branchIds].filter((id) => !branchById.get(id)?.active);
  const blockedTechnicians = technicians.filter((t) => technicianIds.has(t.id) && !assignable(t));

  const toggle = (set: Set<string>, id: string) => {
    const next = new Set(set);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  };

  function clearErrors() {
    setFieldErrors((f) => ({ ...f, branchIds: [], technicianIds: [] }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const errors: Record<string, string[]> = {};
    if (branchIds.size === 0 && technicianIds.size === 0) {
      errors.branchIds = ['Wybierz co najmniej jeden oddział lub serwisanta.'];
    } else if (inactiveBranches.length > 0) {
      errors.branchIds = ['Usuń nieaktywne oddziały z przydziału.'];
    }
    if (blockedTechnicians.length > 0) errors.technicianIds = ['Usuń z przydziału serwisantów z nieaktywnym kontem.'];
    if (!coordinator) errors.coordinatorTechnicianId = ['Wybierz koordynatora spośród osób objętych przydziałem.'];
    if (Object.keys(errors).length > 0) {
      save.reset();
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    save.mutate({
      expectedVersion: assignment.version,
      branchIds: [...branchIds],
      technicianIds: [...technicianIds],
      coordinatorTechnicianId: coordinator,
    }, {
      onSuccess: (saved) => {
        toast.success(`Zapisano przydział. Status zlecenia: ${serviceOrderStatusLabel(saved.status)}.`);
        onClose();
      },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 409) {
          toast.error('Przydział lub status zlecenia zmienił się w międzyczasie. Wyświetlono aktualne dane, spróbuj ponownie.');
          onClose();
        } else if (e instanceof ApiError && e.status === 404) {
          toast.error('To zlecenie już nie istnieje albo nie należy do Twojej firmy.');
          onClose();
        } else if (e instanceof ApiError) {
          setFieldErrors(e.fieldErrors);
        }
      },
    });
  }

  const pickerErrors = [...(fieldErrors.branchIds ?? []), ...(fieldErrors.technicianIds ?? [])];
  const showAlert = save.isError && Object.values(fieldErrors).every((list) => list.length === 0);
  const coordinatorName = candidates.find((t) => t.id === coordinator)?.displayName;

  return (
    <form onSubmit={submit} noValidate>
      <div className="modal-body">
        {showAlert && <div className="inline-alert inline-alert-error" role="alert">{errorMessage(save.error)}</div>}

        <div className={`assign-field${pickerErrors.length ? ' assign-field-invalid' : ''}`}>
          <span className="field-label" id="assignment-picker-label">Oddziały i serwisanci</span>
          {visibleBranches.length === 0 ? (
            <p className="muted">Firma nie ma aktywnych oddziałów.</p>
          ) : (
            <ul className="assign-tree" aria-labelledby="assignment-picker-label">
              {visibleBranches.map((b) => {
                const staff = staffOf(b.id);
                const open = expanded.has(b.id);
                const whole = branchIds.has(b.id);
                const picked = staff.filter((t) => technicianIds.has(t.id)).length;
                return (
                  <li key={b.id} className={`assign-branch${whole ? ' assign-selected' : ''}`}>
                    <div className="assign-row">
                      <button
                        type="button"
                        className="btn-icon btn-icon-sm"
                        aria-expanded={open}
                        aria-controls={`assign-staff-${b.id}`}
                        aria-label={`${open ? 'Zwiń' : 'Rozwiń'} serwisantów oddziału ${b.name}`}
                        onClick={() => setExpanded((s) => toggle(s, b.id))}
                      >
                        {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                      </button>
                      <label className="assign-check">
                        <input
                          type="checkbox"
                          checked={whole}
                          disabled={!b.active && !whole}
                          onChange={() => { setBranchIds((s) => toggle(s, b.id)); clearErrors(); }}
                        />
                        <span className="assign-text">
                          <span className="assign-name">{b.name}</span>
                          <span className="assign-hint">
                            {b.active ? `Cały oddział · ${people(staff.filter(assignable).length)}` : 'Oddział nieaktywny, usuń go z przydziału'}
                          </span>
                        </span>
                      </label>
                      {picked > 0 && !open && <span className="badge">Wybrano: {picked}</span>}
                    </div>
                    {open && (
                      <ul className="assign-staff" id={`assign-staff-${b.id}`}>
                        {staff.length === 0 && <li className="assign-empty muted">Brak serwisantów w tym oddziale.</li>}
                        {staff.map((t) => {
                          const issue = technicianIssue(t);
                          const checked = technicianIds.has(t.id);
                          return (
                            <li key={t.id}>
                              <label className="assign-check">
                                <input
                                  type="checkbox"
                                  checked={checked}
                                  disabled={Boolean(issue) && !checked}
                                  onChange={() => { setTechnicianIds((s) => toggle(s, t.id)); clearErrors(); }}
                                />
                                <span className="assign-text">
                                  <span className="assign-name">{t.displayName}</span>
                                  <span className={`assign-hint${issue ? ' assign-warning' : ''}`}>
                                    {issue ?? (whole ? 'Ma już dostęp przez cały oddział' : t.email)}
                                  </span>
                                </span>
                              </label>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
          {pickerErrors.length > 0 && <span className="field-error" role="alert">{pickerErrors.join(' ')}</span>}
        </div>

        <SelectField
          label="Koordynator"
          name="coordinatorTechnicianId"
          value={coordinator}
          onChange={(v) => { setCoordinatorId(v); setFieldErrors((f) => ({ ...f, coordinatorTechnicianId: [] })); }}
          errors={fieldErrors.coordinatorTechnicianId}
          required
          disabled={candidates.length === 0}
        >
          <option value="" disabled>
            {candidates.length === 0 ? 'Najpierw wybierz oddział lub serwisanta' : 'Wybierz koordynatora'}
          </option>
          {candidates.map((t) => (
            <option key={t.id} value={t.id}>{t.displayName} — {branchById.get(t.branchId)?.name ?? t.branchName}</option>
          ))}
        </SelectField>

        <div className="assign-summary" aria-live="polite">
          <span className="order-preview-label">Podsumowanie</span>
          <span>
            Oddziały: <strong>{branchIds.size}</strong> · Serwisanci wybrani osobno: <strong>{technicianIds.size}</strong>
          </span>
          <span>Dostęp do zlecenia: <strong>{people(covered.length)}</strong></span>
          <span>Koordynator: <strong>{coordinatorName ?? 'nie wybrano'}</strong></span>
        </div>
      </div>
      <footer className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onClose} disabled={save.isPending}>Anuluj</button>
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending && <Loader2 size={14} className="spin" aria-hidden />}
          {save.isPending ? 'Zapisywanie…' : 'Zapisz przydział'}
        </button>
      </footer>
    </form>
  );
}
