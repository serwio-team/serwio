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
