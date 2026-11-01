import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

async function load(path) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 } });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

const { validateAvailability, availabilityRequest, editableWeek } = await load('../src/availability.ts');
const { availabilityFixtures } = await load('../src/fixtures/availability.ts');
const day = (dayOfWeek, ...intervals) => ({ dayOfWeek, intervals: intervals.map(([from, to]) => ({ from, to })) });
const valid = (days) => assert.deepEqual(validateAvailability(days), {});
const invalid = (days) => assert.ok(Object.keys(validateAvailability(days)).length > 0);

test('all contract fixtures validate and survive an editable-week round trip', () => {
  for (const fixture of Object.values(availabilityFixtures)) {
    valid(fixture.days);
    const week = editableWeek(fixture.days);
    assert.equal(week.length, 7);
    assert.deepEqual(week.map((entry) => entry.dayOfWeek), [1, 2, 3, 4, 5, 6, 7]);
    valid(availabilityRequest(week).days);
  }
  assert.deepEqual(availabilityRequest(editableWeek([])), { days: [] });
});

test('hours use strict HH:mm, allow 24:00 only as end, and reject equal ends', () => {
  for (const interval of [['24:00', '02:00'], ['08:00', '08:00'], ['8:00', '09:00'], ['12:60', '13:00'], ['-1:00', '01:00'], ['00:00', '24:01'], ['', '12:00'], ['09:00', '10:00:00']]) {
    invalid([day(1, interval)]);
  }
  valid([day(1, ['00:00', '24:00'])]);
  valid([day(1, ['22:00', '00:00'])]);
});

test('touching intervals are valid and nested or overlapping intervals mark both fields', () => {
  valid([day(1, ['08:00', '12:00'], ['12:00', '18:00'])]);
  for (const interval of [['09:00', '10:00'], ['11:00', '14:00'], ['08:00', '12:00']]) {
    const errors = validateAvailability([day(1, ['08:00', '12:00'], interval)]);
    assert.ok(errors['days[0].intervals[0].from']);
    assert.ok(errors['days[0].intervals[1].from']);
  }
});

test('overnight overlaps include adjacent days and wrap from Sunday to Monday', () => {
  valid([day(7, ['22:00', '02:00'])]);
  invalid([day(7, ['22:00', '02:00']), day(1, ['00:00', '02:00'])]);
  valid([day(7, ['22:00', '02:00']), day(1, ['02:00', '23:00'])]);
  invalid([day(3, ['22:00', '02:00']), day(4, ['01:00', '03:00'])]);
  valid([day(7, ['22:00', '24:00']), day(1, ['00:00', '23:00'])]);
  invalid([day(1, ['00:00', '24:00'], ['23:00', '01:00'])]);
});

test('day numbers are unique integers within the week and interval limit is 100', () => {
  for (const number of [0, 8, 1.5]) invalid([day(number)]);
  invalid([day(1), day(1)]);
  const intervals = Array.from({ length: 100 }, (_, i) => {
    const time = (minute) => `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`;
    return [time(i * 2), time(i * 2 + 1)];
  });
  valid([day(1, ...intervals)]);
  invalid([day(1, ...intervals, ['08:00', '09:00'])]);
});

test('requests contain only contract fields and preserve midnight normalization', () => {
  const fixture = availabilityFixtures.normalized;
  const week = editableWeek(fixture.days);
  week[0].intervals[0].endsAtMidnight = true;
  const request = availabilityRequest(week);
  assert.deepEqual(request, { days: fixture.days });
  assert.ok(!JSON.stringify(request).includes('endsAtMidnight'));
  valid(request.days);
  assert.deepEqual(availabilityRequest(editableWeek(request.days)), request);
});
