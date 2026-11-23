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

test('the owner sees scheduled and completed work until the order is closed or cancelled', async (t) => {
  const { api } = await vite.ssrLoadModule('/src/api.ts');
  const { ACTIVE_ORDER_STATUSES, FINISHED_ORDER_STATUSES, serviceOrderStatusLabel } =
    await vite.ssrLoadModule('/src/components/StatusBadge.tsx');
  const assigned = { id: 'assigned', currentStatus: 'ASSIGNED' };
  const scheduling = { id: 'scheduling', currentStatus: 'AWAITING_SCHEDULING' };
  const confirmed = { id: 'confirmed', currentStatus: 'VISIT_CONFIRMED' };
  const inProgress = { id: 'inProgress', currentStatus: 'IN_PROGRESS' };
  const revisit = { id: 'revisit', currentStatus: 'REVISIT_REQUIRED' };
  const completed = { id: 'completed', currentStatus: 'COMPLETED' };
  const closed = { id: 'closed', currentStatus: 'CLOSED' };
  const cancelled = { id: 'cancelled', currentStatus: 'CANCELLED' };
  const active = [assigned, scheduling, confirmed, inProgress, revisit, completed];
  const finished = [closed, cancelled];
  const orders = [...active, ...finished];
  const filters = [];
  t.mock.method(globalThis, 'fetch', async (url) => {
    const status = new URL(url, 'http://serwio.test').searchParams.get('status');
    filters.push(status);
    return Response.json(status ? orders.filter((order) => order.currentStatus === status) : orders);
  });

  assert.deepEqual((await api.listServiceOrders(ACTIVE_ORDER_STATUSES, 0)).items, active);
  assert.deepEqual((await api.listServiceOrders(FINISHED_ORDER_STATUSES, 0)).items, finished);
  for (const order of orders) {
    assert.deepEqual((await api.listServiceOrders([order.currentStatus], 0)).items, [order]);
  }
  assert.deepEqual(filters, [null, null, ...orders.map((order) => order.currentStatus)]);
  assert.equal(serviceOrderStatusLabel('AWAITING_SCHEDULING'), 'Uzgadnianie terminu');
  assert.equal(serviceOrderStatusLabel('VISIT_CONFIRMED'), 'Wizyta potwierdzona');
  assert.equal(serviceOrderStatusLabel('IN_PROGRESS'), 'W trakcie realizacji');
  assert.equal(serviceOrderStatusLabel('REVISIT_REQUIRED'), 'Wymaga ponownej wizyty');
  assert.equal(serviceOrderStatusLabel('COMPLETED'), 'Wykonane');
});
