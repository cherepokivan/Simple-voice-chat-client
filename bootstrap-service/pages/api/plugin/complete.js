import { assertPluginSignature, handle, httpError, json, key, readJson, redis, ttl } from '../../../lib/bridge.js';

export default handle(async (req) => {
  const body = await readJson(req);
  const tokenHash = String(body.tokenHash ?? '');
  const requestId = String(body.requestId ?? '');
  const bootstrap = body.bootstrap;
  if (!/^[A-Za-z0-9_-]{43}$/.test(tokenHash) || requestId.length < 20 || typeof bootstrap !== 'object' || bootstrap === null) {
    throw httpError(400, 'Invalid completion payload.');
  }
  const bootstrapJson = JSON.stringify(bootstrap);
  if (bootstrapJson.length > 8_192) throw httpError(413, 'Bootstrap response is too large.');
  assertPluginSignature(req, 'complete', [tokenHash, requestId, bootstrapJson]);

  const store = redis();
  const record = await store.get(key('request', requestId));
  if (!record || record.tokenHash !== tokenHash || record.state !== 'waiting') throw httpError(409, 'Pairing request is unavailable.');
  await store.set(key('response', requestId), bootstrap, { ex: ttl.response });
  await store.set(key('request', requestId), { ...record, state: 'ready' }, { ex: ttl.response });
  return json({ status: 'ready', expiresInSeconds: ttl.response });
});
