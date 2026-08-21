import { assertPluginSignature, handle, httpError, json, key, readJson, redis } from '../../lib/bridge.mjs';

export default handle(async (req) => {
  const body = await readJson(req);
  const tokenHash = String(body.tokenHash ?? '');
  if (!/^[A-Za-z0-9_-]{43}$/.test(tokenHash)) throw httpError(400, 'Invalid token digest.');
  assertPluginSignature(req, 'check', [tokenHash]);

  const store = redis();
  const requestId = await store.get(key('request-by-token', tokenHash));
  return json({ status: requestId ? 'claimed' : 'idle', requestId: requestId ?? null });
});
