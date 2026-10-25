// Klient API. Wszystkie adresy są względne (/api/...), lokalnie przechodzą przez proxy Vite.
// Cookie JSESSIONID przeglądarka wysyła sama — nie odczytujemy go w JS.

import type {
  AssetOwnerResponse,
  LocationListItemResponse,
  LocationRequest,
  LocationResponse,
  PasswordChangeRequest,
  ServiceCompanyResponse,
  ServiceObjectListItemResponse,
  ServiceObjectRequest,
  ServiceObjectResponse,
  TechnicianProfileResponse,
  UserAccountResponse,
} from './types';

export const PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 100;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string[]> = {},
  ) {
    super(message);
  }
}

// SessionWatcher nasłuchuje tego zdarzenia i po 401 przenosi na stronę logowania.
export const UNAUTHORIZED_EVENT = 'serwio:unauthorized';

interface CsrfToken {
  headerName: string;
  parameterName: string;
  token: string;
}

let csrfCache: CsrfToken | null = null;

async function fetchCsrf(): Promise<CsrfToken> {
  const response = await fetch('/api/csrf');
  if (!response.ok) throw new ApiError(response.status, 'Nie udało się pobrać tokena CSRF.');
  csrfCache = (await response.json()) as CsrfToken;
  return csrfCache;
}

function defaultMessage(status: number): string {
  switch (status) {
    case 400: return 'Popraw zaznaczone pola formularza.';
    case 401: return 'Sesja wygasła lub nie jesteś zalogowany.';
    case 403: return 'Brak uprawnień do tej operacji.';
    case 404: return 'Nie znaleziono zasobu. Lista zostanie odświeżona.';
    case 409: return 'Operacja narusza spójność danych. Odśwież dane i spróbuj ponownie.';
    default: return `Serwer nie odpowiada poprawnie (${status}). Spróbuj za chwilę.`;
  }
}

// Odpowiedź błędu może nie zawierać JSON-a, więc parsujemy ostrożnie.
async function toApiError(response: Response): Promise<ApiError> {
  const body = (await response.json().catch(() => null)) as
    | { detail?: string; errors?: Record<string, string[]> }
    | null;
  if (response.status === 401) {
    csrfCache = null;
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }
  const message =
    (response.status === 400 || response.status === 409) && body?.detail ? body.detail : defaultMessage(response.status);
  return new ApiError(response.status, message, body?.errors ?? {});
}

async function readBody<T>(response: Response): Promise<T> {
  // 204 nie ma treści — nie wywołujemy response.json().
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

async function get<T>(url: string): Promise<T> {
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw await toApiError(response);
  return readBody<T>(response);
}

async function send(method: string, url: string, body: unknown, csrf: CsrfToken): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json', [csrf.headerName]: csrf.token };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  return fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

// POST/PUT/DELETE z tokenem CSRF. Po 403 sprawdzamy sesję przez GET /api:
// 401 → trzeba się zalogować, 200 → pobieramy świeży CSRF i ponawiamy raz.
async function mutate<T>(method: string, url: string, body?: unknown): Promise<T> {
  let response = await send(method, url, body, csrfCache ?? (await fetchCsrf()));
  if (response.status === 403) {
    const session = await fetch('/api', { headers: { Accept: 'application/json' } });
    if (session.status === 401) throw await toApiError(session);
    if (session.ok) response = await send(method, url, body, await fetchCsrf());
  }
  if (!response.ok) throw await toApiError(response);
  return readBody<T>(response);
}

function pageQuery(page: number, size: number, extra: Record<string, string | undefined> = {}): string {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  for (const [key, value] of Object.entries(extra)) if (value) params.set(key, value);
  return params.toString();
}

// Pobiera wszystkie strony (np. do listy wyboru). Krótsza strona oznacza koniec.
async function getAllPages<T>(path: string): Promise<T[]> {
  const all: T[] = [];
  for (let page = 0; ; page++) {
    const items = await get<T[]>(`${path}?${pageQuery(page, MAX_PAGE_SIZE)}`);
    all.push(...items);
    if (items.length < MAX_PAGE_SIZE) return all;
  }
}

export const api = {
  currentUser: () => get<UserAccountResponse>('/api/users/me'),
  currentOwner: () => get<AssetOwnerResponse>('/api/owners/me'),
  currentCompany: () => get<ServiceCompanyResponse>('/api/companies/me'),
  currentTechnician: () => get<TechnicianProfileResponse>('/api/technicians/me'),
  changeOwnPassword: (data: PasswordChangeRequest) => mutate<void>('PUT', '/api/users/me/password', data),

  // Formularz logowania Spring Security z nagłówkiem Accept: application/json:
  // 200 = zalogowano, 401 z polem detail = złe dane. 401 bez detail lub 403 oznacza
  // nieaktualny token CSRF — wtedy pobieramy nowy i próbujemy jeszcze raz.
  async login(username: string, password: string): Promise<void> {
    for (let attempt = 0; attempt < 2; attempt++) {
      const csrf = attempt === 0 && csrfCache ? csrfCache : await fetchCsrf();
      const response = await fetch('/login', {
        method: 'POST',
        headers: { Accept: 'application/json' },
        body: new URLSearchParams({ username, password, [csrf.parameterName]: csrf.token }),
      });
      if (response.ok) {
        // Po zalogowaniu sesja i token CSRF są nowe.
        csrfCache = null;
        return;
      }
      const body = (await response.json().catch(() => null)) as { detail?: string } | null;
      if (response.status === 401 && body?.detail) throw new ApiError(401, body.detail);
      if (response.status !== 401 && response.status !== 403) {
        throw new ApiError(response.status, defaultMessage(response.status));
      }
    }
    throw new ApiError(403, 'Nie udało się zalogować. Odśwież stronę i spróbuj ponownie.');
  },

  // Spring odpowiada przekierowaniem na /login?logout — nie potrzebujemy tej strony.
  async logout(): Promise<void> {
    const csrf = csrfCache ?? (await fetchCsrf());
    await fetch('/logout', { method: 'POST', headers: { [csrf.headerName]: csrf.token } });
    csrfCache = null;
  },

  listLocations: (page: number) =>
    get<LocationListItemResponse[]>(`/api/locations?${pageQuery(page, PAGE_SIZE)}`),
  allLocations: () => getAllPages<LocationListItemResponse>('/api/locations'),
  getLocation: (id: string) => get<LocationResponse>(`/api/locations/${encodeURIComponent(id)}`),
  createLocation: (data: LocationRequest) => mutate<LocationResponse>('POST', '/api/locations', data),
  updateLocation: (id: string, data: LocationRequest) =>
    mutate<LocationResponse>('PUT', `/api/locations/${encodeURIComponent(id)}`, data),
  deleteLocation: (id: string) => mutate<void>('DELETE', `/api/locations/${encodeURIComponent(id)}`),

  listServiceObjects: (page: number, locationId?: string) =>
    get<ServiceObjectListItemResponse[]>(`/api/service-objects?${pageQuery(page, PAGE_SIZE, { locationId })}`),
  getServiceObject: (id: string) => get<ServiceObjectResponse>(`/api/service-objects/${encodeURIComponent(id)}`),
  createServiceObject: (data: ServiceObjectRequest) => mutate<ServiceObjectResponse>('POST', '/api/service-objects', data),
  updateServiceObject: (id: string, data: ServiceObjectRequest) =>
    mutate<ServiceObjectResponse>('PUT', `/api/service-objects/${encodeURIComponent(id)}`, data),
  deleteServiceObject: (id: string) => mutate<void>('DELETE', `/api/service-objects/${encodeURIComponent(id)}`),
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof TypeError) return 'Brak połączenia z serwerem. Czy backend działa?';
  return 'Wystąpił nieoczekiwany błąd.';
}
