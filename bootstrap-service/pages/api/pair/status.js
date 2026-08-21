import { handle, httpError, json, key, readJson, redis, sha256 } from '../../../lib/bridge.js';

export default handle(async (req) => {
  const body = await readJson(req);
  const requestId = String(body.requestId ?? '');
  const readKey = String(body.readKey ?? '');
  if (requestId.length < 20 || readKey.length < 20) throw httpError(400, 'Invalid request credentials.');

  const store = redis();
  const record = await store.get(key('request', requestId));
  if (!record || record.readKeyHash !== sha256(readKey)) throw httpError(404, 'Pairing request is invalid or expired.');
  if (record.state === 'waiting') return json({ status: 'waiting' }, 202);
  if (record.state !== 'ready') throw httpError(409, 'Pairing request cannot be completed.');

  const bootstrap = await store.get(key('response', requestId));
  if (!bootstrap) throw httpError(410, 'Bootstrap response has expired.');
  await store.del(key('response', requestId), key('request', requestId), key('request-by-token', record.tokenHash), key('token', record.tokenHash));
  return json({ status: 'ready', bootstrap });
});
