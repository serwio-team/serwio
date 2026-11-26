import { useState, type FormEvent } from 'react';
import type { VisitTimeRange } from '../types';
import { visitTimeOptions } from '../visitTime';
import { Field, SelectField } from './Field';

interface VisitTimeFormProps {
  timezone: string;
  counter: boolean;
  pending: boolean;
  fieldErrors: Record<string, string[]>;
  onChange: () => void;
  onSubmit: (time: VisitTimeRange) => void;
}

export function VisitTimeForm({ timezone, counter, pending, fieldErrors, onChange, onSubmit }: VisitTimeFormProps) {
  const [date, setDate] = useState('');
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [startOffset, setStartOffset] = useState('');
  const [endOffset, setEndOffset] = useState('');
  const [localErrors, setLocalErrors] = useState<Record<string, string[]>>({});
  const starts = visitTimeOptions(date, start, timezone);
  const ends = visitTimeOptions(date, end === '00:00' ? '24:00' : end, timezone);

  function change(setter: (value: string) => void, value: string) {
    setter(value);
    setLocalErrors({});
    onChange();
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;

    const startsAt = starts.length === 1 ? starts[0] : starts.find(({ offset }) => offset === startOffset);
    const endsAt = ends.length === 1 ? ends[0] : ends.find(({ offset }) => offset === endOffset);
    const errors: Record<string, string[]> = {};
    if (!startsAt) errors.startsAt = ['Wybierz godzinę, która występuje w tym dniu w strefie lokalizacji.'];
    if (!endsAt) errors.endsAt = ['Wybierz godzinę, która występuje w tym dniu w strefie lokalizacji.'];
    if (startsAt && endsAt && endsAt.instant <= startsAt.instant) {
      errors.endsAt = ['Koniec wizyty musi być późniejszy niż początek.'];
    }
    setLocalErrors(errors);
    if (startsAt && endsAt && !errors.endsAt) {
      onSubmit({ startsAt: startsAt.iso, endsAt: endsAt.iso });
    }
  }

  return (
    <form className="visit-form" onSubmit={submit}>
      <div className="field-row">
        <Field label="Data wizyty" name="visitDate" type="date" required disabled={pending} value={date}
          onChange={(value) => {
            change(setDate, value);
            setStartOffset('');
            setEndOffset('');
          }} />
        <Field label="Godzina rozpoczęcia" name="visitStart" type="time" required disabled={pending} value={start}
          errors={localErrors.startsAt ?? fieldErrors.startsAt}
          onChange={(value) => {
            change(setStart, value);
            setStartOffset('');
          }} />
        <Field label="Godzina zakończenia" name="visitEnd" type="time" required disabled={pending} value={end}
          errors={localErrors.endsAt ?? fieldErrors.endsAt}
          hint="00:00 oznacza północ po dniu wizyty."
          onChange={(value) => {
            change(setEnd, value);
            setEndOffset('');
          }} />
      </div>
      {starts.length > 1 && (
        <SelectField label="Przesunięcie UTC rozpoczęcia" name="visitStartOffset" required disabled={pending}
          value={startOffset} onChange={(value) => change(setStartOffset, value)}>
          <option value="">Wybierz wystąpienie godziny</option>
          {starts.map(({ offset }) => <option key={offset} value={offset}>UTC{offset}</option>)}
        </SelectField>
      )}
      {ends.length > 1 && (
        <SelectField label="Przesunięcie UTC zakończenia" name="visitEndOffset" required disabled={pending}
          value={endOffset} onChange={(value) => change(setEndOffset, value)}>
          <option value="">Wybierz wystąpienie godziny</option>
          {ends.map(({ offset }) => <option key={offset} value={offset}>UTC{offset}</option>)}
        </SelectField>
      )}
      <button type="submit" className="btn btn-primary" disabled={pending}>
        {counter ? 'Wyślij kontrpropozycję' : 'Zaproponuj termin'}
      </button>
    </form>
  );
}
