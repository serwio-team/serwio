import type { AvailabilityDay, AvailabilityRequest } from './types';

export const WEEKDAYS = ['Poniedziałek', 'Wtorek', 'Środa', 'Czwartek', 'Piątek', 'Sobota', 'Niedziela'];

export function editableWeek(days: AvailabilityDay[]): AvailabilityDay[] {
  return WEEKDAYS.map((_, index) => ({
    dayOfWeek: index + 1,
    intervals: (days.find((day) => day.dayOfWeek === index + 1)?.intervals ?? [])
      .map(({ from, to }) => ({ from, to })),
  }));
}

export function availabilityRequest(days: AvailabilityDay[]): AvailabilityRequest {
  return {
    days: days.filter((day) => day.intervals.length > 0).map(({ dayOfWeek, intervals }) => ({
      dayOfWeek,
      intervals: intervals.map(({ from, to }) => ({ from, to })),
    })),
  };
}

export function validateAvailability(days: AvailabilityDay[]): Record<string, string[]> {
  const errors: Record<string, string[]> = {};
  const seen = new Set<number>();
  const segments: { start: number; end: number; path: string }[] = [];
  const week = 7 * 1440;
  const add = (path: string, message: string) => {
    errors[path] = [...new Set([...(errors[path] ?? []), message])];
  };
  if (days.length > 7) add('days', 'Tydzień może zawierać najwyżej siedem dni.');
  days.forEach((day, dayIndex) => {
    const path = `days[${dayIndex}]`;
    if (!Number.isInteger(day.dayOfWeek) || day.dayOfWeek < 1 || day.dayOfWeek > 7) {
      add(`${path}.dayOfWeek`, 'Wybierz dzień od poniedziałku do niedzieli.');
      return;
    }
    if (seen.has(day.dayOfWeek)) add(`${path}.dayOfWeek`, 'Dzień tygodnia nie może się powtarzać.');
    seen.add(day.dayOfWeek);
    if (day.intervals.length > 100) add(`${path}.intervals`, 'Maksymalnie 100 przedziałów na dzień.');
    day.intervals.forEach(({ from, to }, index) => {
      const intervalPath = `${path}.intervals[${index}]`;
      const validFrom = /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(from);
      const validTo = /^(?:(?:[01]\d|2[0-3]):[0-5]\d|24:00)$/.test(to);
      if (!validFrom) add(`${intervalPath}.from`, 'Podaj godzinę od 00:00 do 23:59 w formacie HH:mm.');
      if (!validTo) add(`${intervalPath}.to`, 'Podaj godzinę w formacie HH:mm; koniec doby to 24:00.');
      if (!validFrom || !validTo) return;
      if (from === to) {
        add(`${intervalPath}.to`, 'Początek i koniec muszą być różne.');
        return;
      }
      const minutes = (value: string) => Number(value.slice(0, 2)) * 60 + Number(value.slice(3));
      const start = (day.dayOfWeek - 1) * 1440 + minutes(from);
      let end = (day.dayOfWeek - 1) * 1440 + minutes(to);
      if (end < start) end += 1440;
      segments.push({ start, end: Math.min(end, week), path: intervalPath });
      if (end > week) segments.push({ start: 0, end: end - week, path: intervalPath });
    });
  });
  segments.sort((a, b) => a.start - b.start);
  for (let i = 0; i < segments.length; i++) {
    for (let j = i + 1; j < segments.length && segments[j].start < segments[i].end; j++) {
      add(`${segments[i].path}.from`, 'Przedziały nakładają się, także po przejściu na następny dzień.');
      add(`${segments[j].path}.from`, 'Przedziały nakładają się, także po przejściu na następny dzień.');
    }
  }
  return errors;
}
