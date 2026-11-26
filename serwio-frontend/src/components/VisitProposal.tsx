import { CalendarClock, Check, Loader2, X } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { ApiError, errorMessage } from '../api';
import { useRespondToProposal } from '../queries';
import type { AvailabilityDay, ServiceVisitResponse, VisitProposalResponse } from '../types';
import {
  addDays,
  availabilitySummary,
  fitsAvailability,
  formatRange,
  offsetLabel,
  todayIn,
  zonedDateTime,
  zonedIso,
} from '../visitTime';
import { Field } from './Field';
import { ConfirmDialog, Modal } from './Modal';
import { useToast } from './Toast';

const UNAVAILABLE_CODES = ['OUTSIDE_LOCATION_AVAILABILITY', 'TECHNICIAN_TIME_CONFLICT'];
const STALE_MESSAGE = 'Ta propozycja nie jest już aktualna. Wyświetlono aktualny stan uzgodnienia.';

const isStale = (e: unknown) => e instanceof ApiError && (e.code === 'STALE_PROPOSAL' || e.status === 404);
const isUnavailable = (e: unknown) => e instanceof ApiError && UNAVAILABLE_CODES.includes(e.code ?? '');

interface ProposalResponseProps {
  orderId: string;
  visit: ServiceVisitResponse;
  proposal: VisitProposalResponse;
  timeZone: string;
  availability: AvailabilityDay[];
}

export function ProposalResponse({ orderId, visit, proposal, timeZone, availability }: ProposalResponseProps) {
  const respond = useRespondToProposal(orderId);
  const toast = useToast();
  const [countering, setCountering] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const pendingAction = respond.isPending ? respond.variables?.action : undefined;

  function onError(e: unknown) {
    if (isStale(e)) {
      toast.error(STALE_MESSAGE);
    } else if (isUnavailable(e)) {
      setProblem(`Ten termin przestał być dostępny. ${(e as ApiError).message} Zaproponuj inny termin.`);
    } else {
      setProblem(errorMessage(e));
    }
  }

  function accept() {
    setProblem(null);
    respond.mutate({ visitId: visit.id, proposalId: proposal.id, action: 'accept' }, {
      onSuccess: () => toast.success('Zaakceptowano termin. Wizyta jest potwierdzona.'),
      onError,
    });
  }

  function reject() {
    setProblem(null);
    respond.mutate({ visitId: visit.id, proposalId: proposal.id, action: 'reject' }, {
      onSuccess: () => toast.success('Odrzucono propozycję. Koordynator zaproponuje nowy termin.'),
      onError,
      onSettled: () => setRejecting(false),
    });
  }

  return (
    <div className="proposal-box">
      <span className="order-preview-label">Propozycja koordynatora czeka na Twoją odpowiedź</span>
      <strong className="proposal-term">{formatRange(proposal.startsAt, proposal.endsAt, timeZone)}</strong>
      {problem && <div className="inline-alert inline-alert-warning" role="alert">{problem}</div>}
      <div className="visit-actions">
        <button type="button" className="btn btn-primary btn-sm" onClick={accept} disabled={respond.isPending}>
          {pendingAction === 'accept' ? <Loader2 size={14} className="spin" aria-hidden /> : <Check size={14} aria-hidden />}
          {pendingAction === 'accept' ? 'Akceptowanie…' : 'Akceptuj termin'}
        </button>
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => setCountering(true)} disabled={respond.isPending}>
          <CalendarClock size={14} aria-hidden /> Zaproponuj inny termin
        </button>
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => setRejecting(true)} disabled={respond.isPending}>
          <X size={14} aria-hidden /> Odrzuć
        </button>
      </div>
      {countering && (
        <CounterModal
          orderId={orderId}
          visit={visit}
          proposal={proposal}
          timeZone={timeZone}
          availability={availability}
          onClose={() => setCountering(false)}
        />
      )}
      {rejecting && (
        <ConfirmDialog
          title="Odrzucić propozycję?"
          message={<>Termin <strong>{formatRange(proposal.startsAt, proposal.endsAt, timeZone)}</strong> nie zostanie zarezerwowany. Koordynator będzie mógł zaproponować nowy.</>}
          confirmLabel="Odrzuć propozycję"
          pendingLabel="Odrzucanie…"
          cancelLabel="Wróć"
          pending={pendingAction === 'reject'}
          onConfirm={reject}
          onCancel={() => setRejecting(false)}
        />
      )}
    </div>
  );
}

interface CounterModalProps extends ProposalResponseProps {
  onClose: () => void;
}

function CounterModal({ orderId, visit, proposal, timeZone, availability, onClose }: CounterModalProps) {
  const respond = useRespondToProposal(orderId);
  const toast = useToast();
  const initialStart = zonedDateTime(proposal.startsAt, timeZone);
  const initialEnd = zonedDateTime(proposal.endsAt, timeZone);
  const [values, setValues] = useState({ date: initialStart.date, from: initialStart.time, to: initialEnd.time });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string[]>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const windows = availabilitySummary(availability);

  const complete = Boolean(values.date && values.from && values.to);
  const overnight = complete && values.to <= values.from;
  const start = complete ? zonedIso(values.date, values.from, timeZone) : null;
  const end = complete ? zonedIso(overnight ? addDays(values.date, 1) : values.date, values.to, timeZone) : null;

  const set = (key: keyof typeof values) => (value: string) => {
    setValues((v) => ({ ...v, [key]: value }));
    setFieldErrors({});
    setProblem(null);
  };

  function validate(): Record<string, string[]> {
    const errors: Record<string, string[]> = {};
    if (!values.date) errors.date = ['Wybierz dzień.'];
    if (!values.from) errors.from = ['Podaj godzinę rozpoczęcia.'];
    if (!values.to) errors.to = ['Podaj godzinę zakończenia.'];
    if (!start || !end) return errors;
    if (values.from === values.to) errors.to = ['Koniec musi być późniejszy niż początek.'];
    else if (start.ms <= Date.now()) errors.from = ['Termin musi zaczynać się w przyszłości.'];
    else if (availability.length === 0) errors.date = ['Lokalizacja nie ma ustawionych godzin dostępności.'];
    else if (!fitsAvailability(availability, values.date, values.from, values.to)) {
      errors.date = ['Termin musi mieścić się w jednym oknie dostępności lokalizacji.'];
    }
    return errors;
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    respond.mutate({
      visitId: visit.id,
      proposalId: proposal.id,
      action: 'counter',
      range: { startsAt: start!.iso, endsAt: end!.iso },
    }, {
      onSuccess: () => {
        toast.success('Wysłano nową propozycję. Czekasz na odpowiedź koordynatora.');
        onClose();
      },
      onError: (e) => {
        if (isStale(e)) {
          toast.error(STALE_MESSAGE);
          onClose();
        } else if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) {
          setFieldErrors({
            from: e.fieldErrors.startsAt ?? [],
            to: e.fieldErrors.endsAt ?? [],
          });
        } else if (isUnavailable(e)) {
          setProblem(`${(e as ApiError).message} Wybierz inny termin.`);
        } else {
          setProblem(errorMessage(e));
        }
      },
    });
  }

  return (
    <Modal
      title="Zaproponuj inny termin"
      description={`Godziny w strefie lokalizacji: ${timeZone} (${offsetLabel(timeZone)}).`}
      onClose={onClose}
    >
      <form onSubmit={submit} noValidate>
        <div className="modal-body">
          {problem && <div className="inline-alert inline-alert-error" role="alert">{problem}</div>}
          <div className="assign-summary">
            <span className="order-preview-label">Dostępność lokalizacji</span>
            {windows.length > 0
              ? windows.map((line) => <span key={line}>{line}</span>)
              : <span className="muted">Brak ustawionych godzin dostępności.</span>}
          </div>
          <Field label="Dzień" name="date" type="date" value={values.date} onChange={set('date')}
            errors={fieldErrors.date} required min={todayIn(timeZone)} autoFocus />
          <div className="field-row">
            <Field label="Od" name="from" type="time" value={values.from} onChange={set('from')}
              errors={fieldErrors.from} required />
            <Field label="Do" name="to" type="time" value={values.to} onChange={set('to')}
              errors={fieldErrors.to} required hint={overnight ? 'Kończy się następnego dnia.' : undefined} />
          </div>
          {start && end && values.from !== values.to && (
            <p className="proposal-preview" aria-live="polite">
              Proponujesz: <strong>{formatRange(start.iso, end.iso, timeZone)}</strong>
            </p>
          )}
        </div>
        <footer className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={respond.isPending}>Anuluj</button>
          <button type="submit" className="btn btn-primary" disabled={respond.isPending}>
            {respond.isPending && <Loader2 size={14} className="spin" aria-hidden />}
            {respond.isPending ? 'Wysyłanie…' : 'Wyślij propozycję'}
          </button>
        </footer>
      </form>
    </Modal>
  );
}
