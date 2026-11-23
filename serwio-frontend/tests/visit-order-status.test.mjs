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

test('the owner keeps seeing orders while their visit is being scheduled', async (t) => {
  const { api } = await vite.ssrLoadModule('/src/api.ts');
  const { ACTIVE_ORDER_STATUSES, serviceOrderStatusLabel } = await vite.ssrLoadModule('/src/components/StatusBadge.tsx');
  const assigned = { id: 'assigned', currentStatus: 'ASSIGNED' };
  const scheduling = { id: 'scheduling', currentStatus: 'AWAITING_SCHEDULING' };
  const confirmed = { id: 'confirmed', currentStatus: 'VISIT_CONFIRMED' };
  const orders = [assigned, scheduling, confirmed];
  const filters = [];
  t.mock.method(globalThis, 'fetch', async (url) => {
    const status = new URL(url, 'http://serwio.test').searchParams.get('status');
    filters.push(status);
    return Response.json(status ? orders.filter((order) => order.currentStatus === status) : orders);
  });

  assert.deepEqual((await api.listServiceOrders(ACTIVE_ORDER_STATUSES, 0)).items, orders);
  for (const order of orders) {
    assert.deepEqual((await api.listServiceOrders([order.currentStatus], 0)).items, [order]);
  }
  assert.deepEqual(filters, [null, ...orders.map((order) => order.currentStatus)]);
  assert.equal(serviceOrderStatusLabel('AWAITING_SCHEDULING'), 'Uzgadnianie terminu');
  assert.equal(serviceOrderStatusLabel('VISIT_CONFIRMED'), 'Wizyta potwierdzona');
});
