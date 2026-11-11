import type { ServiceOrderResponse } from '../types';

const base = {
  serviceObjectId: '00000000-0000-0000-0000-000000000101',
  serviceObjectName: 'Klimatyzator — recepcja',
  locationId: '00000000-0000-0000-0000-000000000201',
  locationName: 'Biuro Kraków',
  addressLine: 'ul. Długa 10',
  city: 'Kraków',
  timezone: 'Europe/Warsaw',
  assetOwnerId: '00000000-0000-0000-0000-000000000301',
  assetOwnerName: 'Hotel Pod Wawelem',
  serviceCompanyId: '00000000-0000-0000-0000-000000000401',
  createdByUserId: '00000000-0000-0000-0000-000000000501',
  currentStatus: 'NEW',
  closedAt: null,
};

export const serviceOrderFixtures = {
  repair: {
    ...base,
    id: '00000000-0000-0000-0000-000000000601',
    orderType: 'REPAIR',
    description: 'Urządzenie nie chłodzi, z jednostki wewnętrznej kapie woda.',
    createdAt: '2026-11-02T08:15:00Z',
  },
  inspection: {
    ...base,
    id: '00000000-0000-0000-0000-000000000602',
    orderType: 'INSPECTION',
    description: 'Coroczny przegląd przed sezonem letnim.',
    createdAt: '2026-11-03T10:30:00Z',
  },
} satisfies Record<string, ServiceOrderResponse>;

const historyObjects = [
  { ...base },
  {
    ...base,
    serviceObjectId: '00000000-0000-0000-0000-000000000102',
    serviceObjectName: 'Pompa ciepła — zaplecze',
  },
  {
    ...base,
    serviceObjectId: '00000000-0000-0000-0000-000000000103',
    serviceObjectName: 'Centrala wentylacyjna',
    locationId: '00000000-0000-0000-0000-000000000202',
    locationName: 'Magazyn Wieliczka',
    addressLine: 'ul. Przemysłowa 4',
    city: 'Wieliczka',
  },
];

const historyStatuses = ['NEW', 'AWAITING_TECHNICIAN_REVIEW', 'AWAITING_OWNER_CONFIRMATION', 'CLOSED', 'CANCELLED'];

const historyDescriptions = {
  REPAIR: 'Urządzenie głośno pracuje i wyłącza się po kilku minutach.',
  INSPECTION: 'Okresowy przegląd: filtry, szczelność i parametry pracy.',
};

const DAY = 24 * 60 * 60 * 1000;
const historyStart = Date.parse('2026-11-01T09:00:00Z');

export const serviceOrderHistoryFixtures: ServiceOrderResponse[] = Array.from({ length: 60 }, (_, index) => {
  const status = historyStatuses[index % historyStatuses.length];
  const orderType = index % 3 === 0 ? 'INSPECTION' : 'REPAIR';
  const createdAt = historyStart - index * DAY;
  const finished = status === 'CLOSED' || status === 'CANCELLED';
  return {
    ...historyObjects[index % historyObjects.length],
    id: `00000000-0000-0000-0000-${String(700 + index).padStart(12, '0')}`,
    orderType,
    description: historyDescriptions[orderType],
    currentStatus: status,
    createdAt: new Date(createdAt).toISOString(),
    closedAt: finished ? new Date(createdAt + 2 * DAY).toISOString() : null,
  };
});

export const serviceOrderProblemFixtures = {
  validation: {
    status: 400,
    detail: 'Popraw nieprawidłowe pola żądania.',
    errors: { description: ['nie może być odstępem'] },
  },
  forbidden: { status: 403, detail: 'Zlecenie może utworzyć tylko właściciel obiektu.' },
  objectNotFound: { status: 404, detail: 'Nie znaleziono aktywnego obiektu serwisowego.' },
  orderNotFound: { status: 404, detail: 'Nie znaleziono zlecenia.' },
  invalidQuery: { status: 400, detail: 'Nieprawidłowe parametry zapytania.' },
  companyUnavailable: { status: 503, detail: 'Firma serwisowa nie jest poprawnie skonfigurowana.' },
};
