import { serviceOrderFixtures, serviceOrderHistoryFixtures, serviceOrderProblemFixtures } from '../fixtures/serviceOrders';
import type {
  AssetOwnerResponse,
  LocationResponse,
  OrderAssignmentResponse,
  ServiceBranchResponse,
  ServiceObjectResponse,
  ServiceOrderListItemResponse,
  ServiceOrderResponse,
  ServiceVisitResponse,
  TechnicianResponse,
  UserAccountResponse,
} from '../types';

const STORAGE_KEY = 'serwio:mock-service-orders';
const ASSIGNMENT_KEY = 'serwio:mock-order-assignments';
const VISITS_KEY = 'serwio:mock-order-visits';
const ASSIGNABLE_STATUSES = ['NEW', 'ASSIGNED', 'AWAITING_SCHEDULING', 'REVISIT_REQUIRED'];
const ORDER_TYPES = ['REPAIR', 'INSPECTION'];
const QUERY_STATUSES = [
  'NEW', 'ASSIGNED', 'AWAITING_SCHEDULING', 'VISIT_CONFIRMED', 'IN_PROGRESS', 'REVISIT_REQUIRED', 'COMPLETED',
  'AWAITING_TECHNICIAN_REVIEW', 'AWAITING_OWNER_CONFIRMATION', 'CLOSED', 'CANCELLED',
];
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
    reportFinalized: false,
  };
  save(order);
  return json(201, order);
}

function allOrders(): ServiceOrderResponse[] {
  const byId = new Map<string, ServiceOrderResponse>();
  for (const order of [...Object.values(serviceOrderFixtures), ...serviceOrderHistoryFixtures, ...Object.values(readStore())]) {
    byId.set(order.id, order);
  }
  return [...byId.values()];
}

function find(id: string): Response {
  const order = allOrders().find((o) => o.id === id);
  return order ? json(200, order) : problem(serviceOrderProblemFixtures.orderNotFound);
}

function list(params: URLSearchParams): Response {
  const status = params.get('status');
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 20);
  if ((status !== null && !QUERY_STATUSES.includes(status)) || !Number.isInteger(page) || page < 0
      || !Number.isInteger(size) || size < 1 || size > 100) {
    return problem(serviceOrderProblemFixtures.invalidQuery);
  }
  const items: ServiceOrderListItemResponse[] = allOrders()
    .filter((o) => status === null || o.currentStatus === status)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || a.id.localeCompare(b.id))
    .slice(page * size, (page + 1) * size)
    .map((o) => ({
      id: o.id,
      serviceObjectId: o.serviceObjectId,
      serviceObjectName: o.serviceObjectName,
      locationId: o.locationId,
      locationName: o.locationName,
      orderType: o.orderType,
      currentStatus: o.currentStatus,
      createdAt: o.createdAt,
    }));
  return json(200, items);
}

function readAssignments(): Record<string, OrderAssignmentResponse> {
  try {
    return JSON.parse(sessionStorage.getItem(ASSIGNMENT_KEY) ?? '{}') as Record<string, OrderAssignmentResponse>;
  } catch {
    return {};
  }
}

function saveAssignment(assignment: OrderAssignmentResponse) {
  try {
    sessionStorage.setItem(ASSIGNMENT_KEY, JSON.stringify({ ...readAssignments(), [assignment.serviceOrderId]: assignment }));
  } catch {
    return;
  }
}

function currentAssignment(order: ServiceOrderResponse): OrderAssignmentResponse {
  const stored = readAssignments()[order.id];
  if (stored) return { ...stored, status: order.currentStatus };
  return {
    serviceOrderId: order.id,
    status: order.currentStatus,
    version: 0,
    branches: [],
    technicians: [],
    coordinatorTechnicianId: null,
  };
}

function invalidField(field: string, message: string): Response {
  return problem({ ...serviceOrderProblemFixtures.validation, errors: { [field]: [message] } });
}

function idList(value: unknown): string[] | null {
  return Array.isArray(value) && value.every((v) => typeof v === 'string' && v) ? (value as string[]) : null;
}

async function replaceAssignment(id: string, body: Record<string, unknown>): Promise<Response> {
  const branchIds = idList(body.branchIds);
  const technicianIds = idList(body.technicianIds);
  const coordinatorId = typeof body.coordinatorTechnicianId === 'string' ? body.coordinatorTechnicianId : '';
  if (!branchIds || !technicianIds || !coordinatorId || typeof body.expectedVersion !== 'number') {
    return problem({ status: 400, detail: 'Popraw nieprawidłowe pola żądania.' });
  }
  if (new Set(branchIds).size !== branchIds.length) return invalidField('branchIds', 'Identyfikatory nie mogą się powtarzać.');
  if (new Set(technicianIds).size !== technicianIds.length) {
    return invalidField('technicianIds', 'Identyfikatory nie mogą się powtarzać.');
  }
  if (branchIds.length === 0 && technicianIds.length === 0) {
    return invalidField('branchIds', 'Wybierz co najmniej jeden oddział lub serwisanta.');
  }
  const order = allOrders().find((o) => o.id === id);
  if (!order) return problem(serviceOrderProblemFixtures.orderNotFound);
  if (!ASSIGNABLE_STATUSES.includes(order.currentStatus)) return problem(serviceOrderProblemFixtures.assignmentLocked);
  const current = currentAssignment(order);
  if (body.expectedVersion !== current.version) return problem(serviceOrderProblemFixtures.assignmentConflict);

  const [branches, technicians] = await Promise.all([
    load<ServiceBranchResponse[]>('/api/branches?page=0&size=100'),
    load<TechnicianResponse[]>('/api/technicians?page=0&size=100'),
  ]);
  if (branches.failure) return branches.failure;
  if (technicians.failure) return technicians.failure;
  const selectedBranches = branches.data!.filter((b) => b.active && branchIds.includes(b.id));
  if (selectedBranches.length !== branchIds.length) {
    return invalidField('branchIds', 'Oddział jest nieaktywny lub nie należy do firmy.');
  }
  const usable = technicians.data!.filter((t) => t.active && t.accountStatus !== 'INACTIVE');
  const selectedTechnicians = usable.filter((t) => technicianIds.includes(t.id));
  if (selectedTechnicians.length !== technicianIds.length) {
    return invalidField('technicianIds', 'Serwisant jest nieaktywny lub nie należy do firmy.');
  }
  const coordinator = usable.find((t) => t.id === coordinatorId);
  if (!coordinator) return invalidField('coordinatorTechnicianId', 'Koordynator jest nieaktywny lub nie należy do firmy.');
  if (!technicianIds.includes(coordinator.id) && !branchIds.includes(coordinator.branchId)) {
    return invalidField('coordinatorTechnicianId', 'Koordynator musi być objęty przydziałem.');
  }

  const unchanged = current.coordinatorTechnicianId === coordinatorId
    && current.branches.length === branchIds.length && current.branches.every((b) => branchIds.includes(b.id))
    && current.technicians.length === technicianIds.length && current.technicians.every((t) => technicianIds.includes(t.id));
  if (unchanged) return json(200, current);

  const status = order.currentStatus === 'NEW' ? 'ASSIGNED' : order.currentStatus;
  if (status !== order.currentStatus) save({ ...order, currentStatus: status });
  const saved: OrderAssignmentResponse = {
    serviceOrderId: order.id,
    status,
    version: current.version + 1,
    branches: selectedBranches.map((b) => ({ id: b.id, name: b.name })).sort((a, b) => a.name.localeCompare(b.name)),
    technicians: selectedTechnicians
      .map((t) => ({ id: t.id, displayName: t.displayName, branchId: t.branchId }))
      .sort((a, b) => a.displayName.localeCompare(b.displayName)),
    coordinatorTechnicianId: coordinatorId,
  };
  saveAssignment(saved);
  return json(200, saved);
}

function readVisits(): Record<string, ServiceVisitResponse[]> {
  try {
    return JSON.parse(sessionStorage.getItem(VISITS_KEY) ?? '{}') as Record<string, ServiceVisitResponse[]>;
  } catch {
    return {};
  }
}

function saveVisits(orderId: string, visits: ServiceVisitResponse[]) {
  try {
    sessionStorage.setItem(VISITS_KEY, JSON.stringify({ ...readVisits(), [orderId]: visits }));
  } catch {
    return;
  }
}

function visitConflict(code: string, detail: string): Response {
  return json(409, { status: 409, detail, code });
}

async function visitCandidates(order: ServiceOrderResponse): Promise<{ data?: TechnicianResponse[]; failure?: Response }> {
  const assignment = currentAssignment(order);
  const technicians = await load<TechnicianResponse[]>('/api/technicians?page=0&size=100');
  if (technicians.failure) return technicians;
  const branchIds = assignment.branches.map((b) => b.id);
  const technicianIds = assignment.technicians.map((t) => t.id);
  return {
    data: technicians.data!.filter((t) => t.active && t.accountStatus !== 'INACTIVE'
      && (branchIds.includes(t.branchId) || technicianIds.includes(t.id))),
  };
}

async function staffVisit(order: ServiceOrderResponse, body: Record<string, unknown>, visitId?: string): Promise<Response> {
  const technicianIds = idList(body.technicianIds);
  const coordinatorId = typeof body.coordinatorTechnicianId === 'string' ? body.coordinatorTechnicianId : '';
  if (!technicianIds || technicianIds.length === 0) return invalidField('technicianIds', 'nie może być puste');
  if (new Set(technicianIds).size !== technicianIds.length) {
    return invalidField('technicianIds', 'Identyfikatory nie mogą się powtarzać.');
  }
  if (!coordinatorId) return invalidField('coordinatorTechnicianId', 'nie może mieć wartości null');
  const visits = readVisits()[order.id] ?? [];
  const existing = visitId ? visits.find((v) => v.id === visitId) : undefined;
  if (visitId && !existing) return problem({ status: 404, detail: 'Nie znaleziono wizyty.' });
  if (existing && existing.status !== 'DRAFT') {
    return visitConflict('INVALID_VISIT_STATE', 'Obsadę można zmienić podczas uzgadniania terminu.');
  }
  if (!existing && (!['ASSIGNED', 'REVISIT_REQUIRED'].includes(order.currentStatus)
      || visits.some((v) => ['DRAFT', 'CONFIRMED', 'IN_PROGRESS'].includes(v.status)))) {
    return visitConflict('INVALID_VISIT_STATE', 'Nie można utworzyć wizyty dla tego zlecenia.');
  }
  const candidates = await visitCandidates(order);
  if (candidates.failure) return candidates.failure;
  const staff = candidates.data!.filter((t) => technicianIds.includes(t.id));
  if (staff.length !== technicianIds.length) {
    return invalidField('technicianIds', 'Serwisant nie ma uprawnień do tego zlecenia.');
  }
  if (!technicianIds.includes(coordinatorId)) {
    return invalidField('coordinatorTechnicianId', 'Koordynator musi być jednym z wykonawców.');
  }
  const technicians = staff
    .map((t) => ({ id: t.id, displayName: t.displayName, branchId: t.branchId }))
    .sort((a, b) => a.displayName.localeCompare(b.displayName));
  if (existing) {
    const same = existing.coordinatorTechnicianId === coordinatorId
      && existing.technicians.length === technicians.length
      && existing.technicians.every((t) => technicianIds.includes(t.id));
    const updated = same ? existing : { ...existing, technicians, coordinatorTechnicianId: coordinatorId, currentProposal: null };
    saveVisits(order.id, visits.map((v) => (v.id === existing.id ? updated : v)));
    return json(200, updated);
  }
  const created: ServiceVisitResponse = {
    id: crypto.randomUUID(),
    status: 'DRAFT',
    coordinatorTechnicianId: coordinatorId,
    technicians,
    currentProposal: null,
    startsAt: null,
    endsAt: null,
    startedAt: null,
    completedAt: null,
    outcome: null,
  };
  saveVisits(order.id, [created, ...visits]);
  save({ ...order, currentStatus: 'AWAITING_SCHEDULING' });
  return json(201, created);
}

function cancelVisit(order: ServiceOrderResponse, visitId: string): Response {
  const visits = readVisits()[order.id] ?? [];
  const visit = visits.find((v) => v.id === visitId);
  if (!visit) return problem({ status: 404, detail: 'Nie znaleziono wizyty.' });
  const draft = visit.status === 'DRAFT';
  const confirmed = visit.status === 'CONFIRMED';
  if (!draft && !confirmed) return visitConflict('INVALID_VISIT_STATE', 'Tej wizyty nie można anulować.');
  if (confirmed && (!visit.startsAt || new Date(visit.startsAt) <= new Date())) {
    return visitConflict('INVALID_VISIT_STATE', 'Potwierdzoną wizytę można anulować przed jej rozpoczęciem.');
  }
  const cancelled: ServiceVisitResponse = { ...visit, status: 'CANCELLED', currentProposal: null };
  saveVisits(order.id, visits.map((v) => (v.id === visitId ? cancelled : v)));
  const revisit = visits.some((v) => v.status === 'COMPLETED' && v.outcome === 'REVISIT_REQUIRED');
  save({ ...order, currentStatus: revisit ? 'REVISIT_REQUIRED' : 'ASSIGNED' });
  return json(200, cancelled);
}

async function visitsRoute(orderId: string, rest: string, method: string, init: RequestInit): Promise<Response> {
  const order = allOrders().find((o) => o.id === orderId);
  if (!order) return problem(serviceOrderProblemFixtures.orderNotFound);
  const body = typeof init.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : {};
  if (rest === '' && method === 'GET') {
    return json(200, { timezone: order.timezone, availability: [], visits: readVisits()[order.id] ?? [] });
  }
  if (rest === '' && method === 'POST') return staffVisit(order, body);
  if (rest === '/candidates' && method === 'GET') {
    const candidates = await visitCandidates(order);
    if (candidates.failure) return candidates.failure;
    return json(200, candidates.data!.map((t) => ({ id: t.id, displayName: t.displayName, branchId: t.branchId })));
  }
  const staffing = /^\/([^/]+)\/staffing$/.exec(rest);
  if (staffing && method === 'PUT') return staffVisit(order, body, decodeURIComponent(staffing[1]));
  const cancel = /^\/([^/]+)\/cancel$/.exec(rest);
  if (cancel && method === 'POST') return cancelVisit(order, decodeURIComponent(cancel[1]));
  return problem({ status: 404, detail: 'Brak takiego zasobu w mocku zleceń.' });
}

export async function mockServiceOrderFetch(url: string, init: RequestInit = {}): Promise<Response> {
  await new Promise((resolve) => setTimeout(resolve, 400));
  const [path, query = ''] = url.split('?');
  const method = (init.method ?? 'GET').toUpperCase();

  if (path === '/api/service-orders' && method === 'POST') {
    const body = typeof init.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : {};
    return create(body);
  }
  if (path === '/api/service-orders' && method === 'GET') return list(new URLSearchParams(query));
  const visitsMatch = /^\/api\/service-orders\/([^/]+)\/visits(\/.*)?$/.exec(path);
  if (visitsMatch) return visitsRoute(decodeURIComponent(visitsMatch[1]), visitsMatch[2] ?? '', method, init);
  const assignmentMatch = /^\/api\/service-orders\/([^/]+)\/assignment$/.exec(path);
  if (assignmentMatch) {
    const id = decodeURIComponent(assignmentMatch[1]);
    if (method === 'PUT') {
      const body = typeof init.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : {};
      return replaceAssignment(id, body);
    }
    const order = allOrders().find((o) => o.id === id);
    return order ? json(200, currentAssignment(order)) : problem(serviceOrderProblemFixtures.orderNotFound);
  }
  const match = /^\/api\/service-orders\/([^/]+)$/.exec(path);
  if (match && method === 'GET') return find(decodeURIComponent(match[1]));
  return problem({ status: 404, detail: 'Brak takiego zasobu w mocku zleceń.' });
}
