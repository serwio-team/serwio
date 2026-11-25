import type { AvailabilityDay, ServiceOrderResponse, ServiceReportResponse, ServiceVisitResponse } from '../types';

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
  reportFinalized: false,
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
  scheduling: {
    ...base,
    id: '00000000-0000-0000-0000-000000000603',
    orderType: 'REPAIR',
    description: 'Wentylator jednostki zewnętrznej hałasuje przy starcie.',
    currentStatus: 'AWAITING_SCHEDULING',
    createdAt: '2026-11-04T07:45:00Z',
  },
  completed: {
    ...base,
    id: '00000000-0000-0000-0000-000000000604',
    orderType: 'INSPECTION',
    description: 'Przegląd okresowy przed sezonem grzewczym.',
    currentStatus: 'COMPLETED',
    createdAt: '2026-11-05T08:00:00Z',
    reportFinalized: true,
  },
} satisfies Record<string, ServiceOrderResponse>;

export const visitAvailabilityFixture: AvailabilityDay[] = [
  ...[1, 2, 3, 4, 5].map((dayOfWeek) => ({ dayOfWeek, intervals: [{ from: '08:00', to: '18:00' }] })),
  { dayOfWeek: 6, intervals: [{ from: '09:00', to: '14:00' }] },
];

function nextWeekday(daysAhead: number, hourUtc: number): Date {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + daysAhead);
  while (date.getUTCDay() === 0 || date.getUTCDay() === 6) date.setUTCDate(date.getUTCDate() + 1);
  date.setUTCHours(hourUtc, 0, 0, 0);
  return date;
}

const proposedStart = nextWeekday(3, 9);

export const serviceVisitFixtures: Record<string, ServiceVisitResponse[]> = {
  '00000000-0000-0000-0000-000000000603': [{
    id: '00000000-0000-0000-0000-000000000901',
    status: 'DRAFT',
    coordinatorTechnicianId: '00000000-0000-0000-0000-000000000951',
    technicians: [
      { id: '00000000-0000-0000-0000-000000000951', displayName: 'Anna Kowalska', branchId: '00000000-0000-0000-0000-000000000961' },
      { id: '00000000-0000-0000-0000-000000000952', displayName: 'Marek Nowicki', branchId: '00000000-0000-0000-0000-000000000961' },
    ],
    currentProposal: {
      id: '00000000-0000-0000-0000-000000000911',
      proposedBy: 'COORDINATOR',
      startsAt: proposedStart.toISOString(),
      endsAt: new Date(proposedStart.getTime() + 2 * 60 * 60 * 1000).toISOString(),
    },
    startsAt: null,
    endsAt: null,
    startedAt: null,
    completedAt: null,
    outcome: null,
  }],
};

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
    reportFinalized: status === 'CLOSED',
  };
});

export function serviceReportFixture(order: ServiceOrderResponse): ServiceReportResponse {
  const finalizedAt = order.closedAt ?? '2026-11-06T15:30:00Z';
  return {
    id: `${order.id.slice(0, 24)}000000000999`,
    serviceOrderId: order.id,
    authorTechnicianId: '00000000-0000-0000-0000-000000000951',
    actionsDone: 'Oczyszczono filtry, sprawdzono szczelność instalacji i parametry pracy.',
    diagnosis: 'Zabrudzone filtry ograniczały przepływ powietrza.',
    resolution: 'Po czyszczeniu urządzenie pracuje poprawnie, parametry w normie.',
    usedMaterials: order.orderType === 'REPAIR' ? 'Filtr wstępny, 2 szt.' : null,
    recommendations: 'Kolejny przegląd za 6 miesięcy.',
    createdAt: '2026-11-06T13:00:00Z',
    updatedAt: finalizedAt,
    finalizedAt,
  };
}

export const serviceOrderProblemFixtures = {
  validation: {
    status: 400,
    detail: 'Popraw nieprawidłowe pola żądania.',
    errors: { description: ['nie może być odstępem'] },
  },
  forbidden: { status: 403, detail: 'Zlecenie może utworzyć tylko właściciel obiektu.' },
  objectNotFound: { status: 404, detail: 'Nie znaleziono aktywnego obiektu serwisowego.' },
  orderNotFound: { status: 404, detail: 'Nie znaleziono zlecenia.' },
  staleProposal: { status: 409, code: 'STALE_PROPOSAL', detail: 'Propozycja nie jest już aktualna.' },
  invalidOrderState: { status: 409, code: 'INVALID_ORDER_STATE', detail: 'Zlecenie można anulować przed rozpoczęciem realizacji.' },
  orderNotCompleted: { status: 409, code: 'INVALID_ORDER_STATE', detail: 'Zamknąć można tylko wykonane zlecenie.' },
  reportNotFinalized: { status: 409, code: 'REPORT_NOT_FINALIZED', detail: 'Przed zamknięciem zlecenia sfinalizuj raport.' },
  reportNotFound: { status: 404, detail: 'Nie znaleziono raportu.' },
  pastProposal: { status: 409, code: 'OUTSIDE_LOCATION_AVAILABILITY', detail: 'Termin jest poza dostępnym oknem lokalizacji.' },
  assignmentConflict: { status: 409, detail: 'Przydział został zmieniony. Odśwież dane i spróbuj ponownie.' },
  assignmentLocked: { status: 409, detail: 'Przydział można zmienić przed potwierdzeniem terminu wizyty.' },
  invalidQuery: { status: 400, detail: 'Nieprawidłowe parametry zapytania.' },
  companyUnavailable: { status: 503, detail: 'Firma serwisowa nie jest poprawnie skonfigurowana.' },
};
