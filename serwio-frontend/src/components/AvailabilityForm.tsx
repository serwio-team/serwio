import { Loader2, Plus, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { ApiError, errorMessage } from '../api';
import { availabilityRequest, editableWeek, validateAvailability, WEEKDAYS } from '../availability';
import { useAvailability, useSaveAvailability } from '../queries';
import type { AvailabilityDay, AvailabilityResponse, LocationListItemResponse } from '../types';
import { ErrorState } from './DataState';
import { Field } from './Field';
import { Modal } from './Modal';

function availabilityError(error: unknown) {
  return error instanceof ApiError && error.status === 404
    ? 'Ta lokalizacja jest niedostępna. Nie można odczytać ani zapisać jej dostępności.'
    : errorMessage(error);
}

export function AvailabilityModal({ location, onClose }: { location: LocationListItemResponse; onClose: () => void }) {
  const schedule = useAvailability(location.id);
  const save = useSaveAvailability(location.id);
  return (
    <Modal title={`Dostępność: ${location.name}`} description="Godziny wizyt serwisowych w lokalnej strefie czasowej."
      onClose={() => !save.isPending && onClose()} trapFocus>
      {schedule.isPending ? (
        <div className="modal-loading"><Loader2 className="spin" size={18} aria-label="Ładowanie dostępności" /></div>
      ) : schedule.isError && !schedule.data ? (
        <div className="modal-body">
          <ErrorState message={availabilityError(schedule.error)} onRetry={() => void schedule.refetch()} />
        </div>
      ) : (
        <>
          {schedule.isError && <div className="modal-body"><div className="inline-alert inline-alert-error" role="alert">
            {availabilityError(schedule.error)}
          </div></div>}
          <AvailabilityForm initial={schedule.data!} save={save} onClose={onClose} />
        </>
      )}
    </Modal>
  );
}

function AvailabilityForm({ initial, save, onClose }: {
  initial: AvailabilityResponse;
  save: ReturnType<typeof useSaveAvailability>;
  onClose: () => void;
}) {
  const [days, setDays] = useState(() => editableWeek(initial.days));
  const [timezone, setTimezone] = useState(initial.timezone);
  const [errors, setErrors] = useState<Record<string, string[]>>({});
  const [saved, setSaved] = useState(false);

  function change(index: number, update: (day: AvailabilityDay) => AvailabilityDay) {
    setDays((current) => current.map((day, i) => i === index ? update(day) : day));
    setErrors({});
    setSaved(false);
    save.reset();
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (save.isPending) return;
    setSaved(false);
    const validation = validateAvailability(days);
    setErrors(validation);
    if (Object.keys(validation).length) return;
    const request = availabilityRequest(days);
    save.mutate(request, {
      onSuccess: (response) => {
        setDays(editableWeek(response.days));
        setTimezone(response.timezone);
        setErrors({});
        setSaved(true);
      },
      onError: (error) => {
        if (!(error instanceof ApiError)) return;
        const mapped: Record<string, string[]> = {};
        for (const [path, messages] of Object.entries(error.fieldErrors)) {
          const match = /^days\[(\d+)\]/.exec(path);
          const day = match ? request.days[Number(match[1])] : undefined;
          mapped[day ? path.replace(/^days\[\d+\]/, `days[${day.dayOfWeek - 1}]`) : path] = messages;
        }
        setErrors(mapped);
      },
    });
  }

  return (
    <form onSubmit={submit} noValidate>
      <div className="modal-body availability-body" aria-busy={save.isPending}>
        <p>Strefa czasowa lokalizacji: <strong>{timezone}</strong></p>
        <p className="muted">Format HH:mm. Koniec wcześniejszy niż początek oznacza następny dzień. Pełna doba: 00:00–24:00.</p>
        {saved && <div className="inline-alert inline-alert-info" role="status">Zapisano dostępność lokalizacji.</div>}
        {save.isError && <div className="inline-alert inline-alert-error" role="alert">{availabilityError(save.error)}</div>}
        {Object.keys(errors).length > 0 && (
          <div className="inline-alert inline-alert-error" role="alert">
            <div>Popraw harmonogram przed zapisem.
              {Object.entries(errors).filter(([path]) => !/^days\[\d+\]\.intervals\[\d+\]\.(from|to)$/.test(path))
                .map(([path, messages]) => <p key={path}>{messages.join(' ')}</p>)}
            </div>
          </div>
        )}
        <fieldset className="availability-fields" disabled={save.isPending}>
          <legend className="sr-only">Tygodniowy harmonogram</legend>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => {
            setDays(editableWeek([])); setErrors({}); setSaved(false); save.reset();
          }}>Zamknij cały tydzień</button>
          {days.map((day, dayIndex) => (
            <fieldset className="availability-day" key={day.dayOfWeek}>
              <legend>{WEEKDAYS[dayIndex]}</legend>
              {day.intervals.length === 0 && <p className="muted">Zamknięte</p>}
              {day.intervals.map((interval, index) => (
                <div className="availability-interval" key={index}>
                  {(['from', 'to'] as const).map((endpoint) => (
                    <Field key={endpoint} label={endpoint === 'from' ? 'Od' : 'Do'}
                      aria-label={`${WEEKDAYS[dayIndex]}, przedział ${index + 1}, ${endpoint === 'from' ? 'od' : 'do'}`}
                      name={`days[${dayIndex}].intervals[${index}].${endpoint}`} value={interval[endpoint]}
                      onChange={(value) => change(dayIndex, (current) => ({ ...current,
                        intervals: current.intervals.map((item, i) => i === index ? { ...item, [endpoint]: value } : item),
                      }))} errors={errors[`days[${dayIndex}].intervals[${index}].${endpoint}`]}
                      required placeholder={endpoint === 'from' ? '08:00' : '16:00'} />
                  ))}
                  <button type="button" className="btn-icon btn-icon-danger"
                    aria-label={`Usuń przedział ${index + 1}: ${WEEKDAYS[dayIndex]}`}
                    onClick={() => change(dayIndex, (current) => ({ ...current, intervals: current.intervals.filter((_, i) => i !== index) }))}>
                    <Trash2 size={16} aria-hidden />
                  </button>
                </div>
              ))}
              <button type="button" className="btn btn-secondary btn-sm" disabled={day.intervals.length >= 100}
                aria-label={`Dodaj przedział: ${WEEKDAYS[dayIndex]}`}
                onClick={() => change(dayIndex, (current) => ({ ...current, intervals: [...current.intervals, { from: '', to: '' }] }))}>
                <Plus size={14} aria-hidden /> Dodaj przedział
              </button>
              {day.intervals.length >= 100 && <p className="field-hint">Osiągnięto limit 100 przedziałów.</p>}
            </fieldset>
          ))}
        </fieldset>
        <section className="availability-summary" aria-label="Podsumowanie harmonogramu">
          <h3>Podsumowanie {saved ? 'zapisanego harmonogramu' : 'harmonogramu'}</h3>
          <p>Strefa czasowa: {timezone}</p>
          {days.every((day) => day.intervals.length === 0) && <p>Brak dostępności w całym tygodniu.</p>}
          <dl>{days.map((day, index) => (
            <div key={day.dayOfWeek}><dt>{WEEKDAYS[index]}</dt>
              <dd>{day.intervals.length ? day.intervals.map(({ from, to }) =>
                `${from || '…'}–${to || '…'}${to && from && to < from ? ' (następny dzień)' : ''}`).join(', ') : 'Zamknięte'}</dd>
            </div>
          ))}</dl>
        </section>
      </div>
      <footer className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onClose} disabled={save.isPending}>Zamknij</button>
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending && <Loader2 size={14} className="spin" aria-hidden />}
          {save.isPending ? 'Zapisywanie…' : 'Zapisz dostępność'}
        </button>
      </footer>
    </form>
  );
}
