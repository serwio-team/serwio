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

export const serviceOrderProblemFixtures = {
  validation: {
    status: 400,
    detail: 'Popraw nieprawidłowe pola żądania.',
    errors: { description: ['nie może być odstępem'] },
  },
  forbidden: { status: 403, detail: 'Zlecenie może utworzyć tylko właściciel obiektu.' },
  objectNotFound: { status: 404, detail: 'Nie znaleziono aktywnego obiektu serwisowego.' },
  orderNotFound: { status: 404, detail: 'Nie znaleziono zlecenia.' },
  companyUnavailable: { status: 503, detail: 'Firma serwisowa nie jest poprawnie skonfigurowana.' },
};
