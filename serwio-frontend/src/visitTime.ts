import type { AvailabilityDay } from './types';

const DAY_MINUTES = 24 * 60;
const DAY_NAMES = ['Pon', 'Wt', 'Śr', 'Czw', 'Pt', 'Sob', 'Nd'];

function formatter(options: Intl.DateTimeFormatOptions, timeZone: string) {
  try {
    return new Intl.DateTimeFormat('pl-PL', { ...options, timeZone });
  } catch {
    return new Intl.DateTimeFormat('pl-PL', options);
  }
}

export function formatRange(startsAt: string, endsAt: string, timeZone: string): string {
  const day = formatter({ dateStyle: 'full' }, timeZone);
  const time = formatter({ timeStyle: 'short' }, timeZone);
  const start = new Date(startsAt);
  const end = new Date(endsAt);
  if (day.format(start) === day.format(end)) return `${day.format(start)}, ${time.format(start)}–${time.format(end)}`;
  const full = formatter({ dateStyle: 'medium', timeStyle: 'short' }, timeZone);
  return `${full.format(start)} – ${full.format(end)}`;
}

export function formatMoment(value: string, timeZone: string): string {
  return formatter({ dateStyle: 'medium', timeStyle: 'short' }, timeZone).format(new Date(value));
}

function parts(ms: number, timeZone: string): Record<string, number> {
  const values: Record<string, number> = {};
  const format = formatter({
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }, timeZone);
  for (const part of format.formatToParts(new Date(ms))) {
    if (part.type !== 'literal') values[part.type] = Number(part.value);
  }
  return values;
}

function offsetMinutes(ms: number, timeZone: string): number {
  const p = parts(ms, timeZone);
  return Math.round((Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) - ms) / 60000);
}

function formatOffset(minutes: number): string {
  const sign = minutes < 0 ? '-' : '+';
  const abs = Math.abs(minutes);
  return `${sign}${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`;
}

export function offsetLabel(timeZone: string, at = Date.now()): string {
  return `UTC${formatOffset(offsetMinutes(at, timeZone))}`;
}

const pad = (n: number) => String(n).padStart(2, '0');

export function addDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number);
  const next = new Date(Date.UTC(y, m - 1, d + days));
  return `${next.getUTCFullYear()}-${pad(next.getUTCMonth() + 1)}-${pad(next.getUTCDate())}`;
}

export function zonedIso(date: string, time: string, timeZone: string): { iso: string; ms: number } {
  const [y, m, d] = date.split('-').map(Number);
  const [h, mi] = time.split(':').map(Number);
  const local = Date.UTC(y, m - 1, d, h, mi);
  let offset = offsetMinutes(local, timeZone);
  let ms = local - offset * 60000;
  const corrected = offsetMinutes(ms, timeZone);
  if (corrected !== offset) {
    offset = corrected;
    ms = local - offset * 60000;
  }
  return { iso: `${date}T${pad(h)}:${pad(mi)}:00${formatOffset(offset)}`, ms };
}

export function zonedDateTime(value: string, timeZone: string): { date: string; time: string } {
  const p = parts(new Date(value).getTime(), timeZone);
  return { date: `${p.year}-${pad(p.month)}-${pad(p.day)}`, time: `${pad(p.hour)}:${pad(p.minute)}` };
}

export function todayIn(timeZone: string): string {
  return zonedDateTime(new Date().toISOString(), timeZone).date;
}

const minutes = (time: string) => {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
};

function isoDay(date: string): number {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).getUTCDay() || 7;
}

export function fitsAvailability(availability: AvailabilityDay[], date: string, from: string, to: string): boolean {
  const day = isoDay(date);
  const start = minutes(from);
  let end = minutes(to);
  if (end <= start) end += DAY_MINUTES;
  const windows = (dayOfWeek: number) => availability.find((d) => d.dayOfWeek === dayOfWeek)?.intervals ?? [];
  const sameDay = windows(day).some((w) => {
    const wStart = minutes(w.from);
    let wEnd = minutes(w.to);
    if (wEnd <= wStart) wEnd += DAY_MINUTES;
    return start >= wStart && end <= wEnd;
  });
  if (sameDay) return true;
  const previous = day === 1 ? 7 : day - 1;
  return windows(previous).some((w) => {
    const wStart = minutes(w.from);
    const wEnd = minutes(w.to);
    return wEnd <= wStart && wEnd !== 0 && end <= wEnd;
  });
}

export function availabilitySummary(availability: AvailabilityDay[]): string[] {
  return [...availability]
    .sort((a, b) => a.dayOfWeek - b.dayOfWeek)
    .filter((d) => d.intervals.length > 0)
    .map((d) => `${DAY_NAMES[d.dayOfWeek - 1]} ${d.intervals.map((i) => `${i.from}–${i.to}`).join(', ')}`);
}

const DAY_MS = 86_400_000;

export function visitTimeOptions(date: string, time: string, timezone: string) {
  const wallTime = Date.parse(`${date}T${time}:00Z`);
  if (!Number.isFinite(wallTime)) return [];

  const formatter = new Intl.DateTimeFormat('en-US', { timeZone: timezone, timeZoneName: 'longOffset' });
  const offsetAt = (instant: number) =>
    formatter.formatToParts(instant).find((part) => part.type === 'timeZoneName')!.value.slice(3) || '+00:00';

  // Check both sides of the date so a repeated local time can offer both offsets.
  const offsets = new Set([wallTime - DAY_MS, wallTime, wallTime + DAY_MS].map(offsetAt));
  const local = new Date(wallTime).toISOString().slice(0, -5);

  return [...offsets].flatMap((offset) => {
    const iso = local + offset;
    const instant = Date.parse(iso);
    return Number.isFinite(instant) && offsetAt(instant) === offset ? [{ offset, iso, instant }] : [];
  }).sort((left, right) => left.instant - right.instant);
}
