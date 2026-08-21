import { configuredServerId, handle, json, redis } from '../../lib/bridge.js';

export default handle(async (req) => {
  if (req.method !== 'GET') return json({ error: 'Method not allowed.' }, 405);
  const store = redis();
  await store.ping();
  return json({ status: 'ok', serverId: configuredServerId() });
});
