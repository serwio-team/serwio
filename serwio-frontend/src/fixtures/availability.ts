import type { AvailabilityResponse } from '../types';

const base = { locationId: '00000000-0000-0000-0000-000000000001', timezone: 'Europe/Warsaw' };

export const availabilityFixtures = {
  empty: { ...base, days: [] },
  multiple: { ...base, days: [{ dayOfWeek: 1, intervals: [{ from: '08:00', to: '12:00' }, { from: '14:00', to: '18:00' }] }] },
  overnight: { ...base, days: [{ dayOfWeek: 3, intervals: [{ from: '22:00', to: '02:00' }] }] },
  fullDay: { ...base, days: [{ dayOfWeek: 5, intervals: [{ from: '00:00', to: '24:00' }] }] },
  weekBoundary: { ...base, days: [{ dayOfWeek: 7, intervals: [{ from: '22:00', to: '02:00' }] }] },
  beforeNormalization: { ...base, days: [{ dayOfWeek: 7, intervals: [{ from: '22:00', to: '24:00' }] }, { dayOfWeek: 1, intervals: [{ from: '00:00', to: '23:00' }] }] },
  normalized: { ...base, days: [{ dayOfWeek: 1, intervals: [{ from: '00:00', to: '23:00' }] }, { dayOfWeek: 7, intervals: [{ from: '22:00', to: '24:00' }] }] },
  mergedNight: { ...base, days: [{ dayOfWeek: 7, intervals: [{ from: '22:00', to: '02:00' }] }] },
} satisfies Record<string, AvailabilityResponse>;
