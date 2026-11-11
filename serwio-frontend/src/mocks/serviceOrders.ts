import { serviceOrderFixtures, serviceOrderProblemFixtures } from '../fixtures/serviceOrders';
import type {
  AssetOwnerResponse,
  LocationResponse,
  ServiceObjectResponse,
  ServiceOrderResponse,
  UserAccountResponse,
} from '../types';

const STORAGE_KEY = 'serwio:mock-service-orders';
const ORDER_TYPES = ['REPAIR', 'INSPECTION'];
const MAX_DESCRIPTION_LENGTH = 100000;

function json(status: number, body: unknown): Response {
  const type = status >= 400 ? 'application/problem+json' : 'application/json';
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': type } });
}

function problem({ status, ...rest }: { status: number; detail: string; errors?: Record<string, string[]> }): Response {
  return json(status, { status, ...rest });
}

function readStore(): Record<string, ServiceOrderResponse> {
  try {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '{}') as Record<string, ServiceOrderResponse>;
  } catch {
    return {};
  }
}

function save(order: ServiceOrderResponse) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ ...readStore(), [order.id]: order }));
  } catch {
    return;
  }
}

async function load<T>(url: string): Promise<{ data?: T; failure?: Response }> {
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) return { failure: response };
  return { data: (await response.json()) as T };
}

function validate(body: Record<string, unknown>): Record<string, string[]> {
  const errors: Record<string, string[]> = {};
  if (typeof body.serviceObjectId !== 'string' || !body.serviceObjectId) {
    errors.serviceObjectId = ['nie może mieć wartości null'];
  }
  if (typeof body.orderType !== 'string' || !body.orderType) errors.orderType = ['nie może mieć wartości null'];
  const description = typeof body.description === 'string' ? body.description : '';
  if (description.trim() === '') errors.description = ['nie może być odstępem'];
  else if (description.length > MAX_DESCRIPTION_LENGTH) {
    errors.description = [`wielkość musi należeć do zakresu od 0 do ${MAX_DESCRIPTION_LENGTH}`];
  }
  return errors;
}

async function create(body: Record<string, unknown>): Promise<Response> {
  const errors = validate(body);
  if (Object.keys(errors).length > 0) return problem({ ...serviceOrderProblemFixtures.validation, errors });
  if (!ORDER_TYPES.includes(body.orderType as string)) {
    return problem({ status: 400, detail: 'Failed to read request' });
  }

  const user = await load<UserAccountResponse>('/api/users/me');
  if (user.failure) return user.failure;
  if (user.data!.role !== 'ASSET_OWNER') return problem(serviceOrderProblemFixtures.forbidden);

  const object = await load<ServiceObjectResponse>(`/api/service-objects/${encodeURIComponent(body.serviceObjectId as string)}`);
  if (object.failure?.status === 404 || (object.data && !object.data.active)) {
    return problem(serviceOrderProblemFixtures.objectNotFound);
  }
  if (object.failure) return object.failure;

  const [location, owner] = await Promise.all([
    load<LocationResponse>(`/api/locations/${encodeURIComponent(object.data!.locationId)}`),
    load<AssetOwnerResponse>('/api/owners/me'),
  ]);
  if (location.failure) return location.failure;
  if (owner.failure) return owner.failure;

  const order: ServiceOrderResponse = {
    id: crypto.randomUUID(),
    serviceObjectId: object.data!.id,
    serviceObjectName: object.data!.name,
    locationId: location.data!.id,
    locationName: location.data!.name,
    addressLine: location.data!.addressLine,
    city: location.data!.city,
    timezone: location.data!.timezone,
    assetOwnerId: owner.data!.id,
    assetOwnerName: owner.data!.name,
    serviceCompanyId: serviceOrderFixtures.repair.serviceCompanyId,
    orderType: body.orderType as string,
    description: (body.description as string).trim(),
    currentStatus: 'NEW',
    createdByUserId: user.data!.id,
    createdAt: new Date().toISOString(),
    closedAt: null,
  };
  save(order);
  return json(201, order);
}

function find(id: string): Response {
  const fixtures: Record<string, ServiceOrderResponse> = Object.fromEntries(
    Object.values(serviceOrderFixtures).map((order) => [order.id, order]),
  );
  const order = readStore()[id] ?? fixtures[id];
  return order ? json(200, order) : problem(serviceOrderProblemFixtures.orderNotFound);
}

export async function mockServiceOrderFetch(url: string, init: RequestInit = {}): Promise<Response> {
  await new Promise((resolve) => setTimeout(resolve, 400));
  const path = url.split('?')[0];
  const method = (init.method ?? 'GET').toUpperCase();

  if (path === '/api/service-orders' && method === 'POST') {
    const body = typeof init.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : {};
    return create(body);
  }
  const match = /^\/api\/service-orders\/([^/]+)$/.exec(path);
  if (match && method === 'GET') return find(decodeURIComponent(match[1]));
  return problem({ status: 404, detail: 'Brak takiego zasobu w mocku zleceń.' });
}
