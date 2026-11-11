import assert from 'node:assert/strict';
import test, { after } from 'node:test';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const vite = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false,
  envFile: false,
  server: { middlewareMode: true, watch: null, ws: false },
});
after(() => vite.close());

function loadApi(testCase) {
  return vite.ssrLoadModule(`/src/api.ts?case=${testCase}`);
}
const csrf = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'test-token' };
const saved = { locationId: 'location', timezone: 'Pacific/Auckland', days: [] };

test('availability uses existing GET/PUT client, CSRF and server response', async (t) => {
  const { api } = await loadApi('success');
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return Response.json(url === '/api/csrf' ? csrf : saved);
  });
  assert.deepEqual(await api.getAvailability('a/b'), saved);
  assert.deepEqual(await api.saveAvailability('a/b', { days: [] }), saved);
  assert.deepEqual(calls.map((call) => call.url), ['/api/locations/a%2Fb/availability', '/api/csrf', '/api/locations/a%2Fb/availability']);
  assert.equal(calls[2].options.method, 'PUT');
  assert.equal(calls[2].options.headers['X-CSRF-TOKEN'], csrf.token);
  assert.equal(calls[2].options.body, '{"days":[]}');
});

test('400 retains detail and field paths; 404 and network errors are propagated', async (t) => {
  const { api, ApiError } = await loadApi('errors');
  const errors = { 'days[0].intervals[0].from': ['Niepoprawna godzina.'] };
  for (const status of [400, 404]) {
    t.mock.method(globalThis, 'fetch', async () => Response.json({ detail: 'Niepoprawny harmonogram.', errors }, { status }));
    await assert.rejects(api.getAvailability('location'), (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, status);
      if (status === 400) {
        assert.equal(error.message, 'Niepoprawny harmonogram.');
        assert.deepEqual(error.fieldErrors, errors);
      }
      return true;
    });
    t.mock.restoreAll();
  }
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('offline'); });
  await assert.rejects(api.getAvailability('location'), TypeError);
});

test('403 refreshes CSRF once through existing session check', async (t) => {
  const { api } = await loadApi('csrf');
  const calls = [];
  let puts = 0;
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push(url);
    if (url === '/api/csrf') return Response.json(csrf);
    if (url === '/api') return Response.json({});
    if (options.method === 'PUT' && ++puts === 1) return Response.json({}, { status: 403 });
    return Response.json(saved);
  });
  assert.deepEqual(await api.saveAvailability('location', { days: [] }), saved);
  assert.deepEqual(calls, ['/api/csrf', '/api/locations/location/availability', '/api', '/api/csrf', '/api/locations/location/availability']);
});

test('401 dispatches existing session expiration event; persistent 403 remains an error', async (t) => {
  const { api, ApiError, UNAUTHORIZED_EVENT } = await loadApi('auth');
  const events = new EventTarget();
  globalThis.window = events;
  t.after(() => { delete globalThis.window; });
  let expired = false;
  events.addEventListener(UNAUTHORIZED_EVENT, () => { expired = true; });
  t.mock.method(globalThis, 'fetch', async () => Response.json({}, { status: 401 }));
  await assert.rejects(api.getAvailability('location'), (error) => error instanceof ApiError && error.status === 401);
  assert.equal(expired, true);
  t.mock.restoreAll();
  t.mock.method(globalThis, 'fetch', async (url) => Response.json(url === '/api/csrf' ? csrf : {}, { status: url.includes('availability') ? 403 : 200 }));
  await assert.rejects(api.saveAvailability('location', { days: [] }), (error) => error.status === 403);
});
