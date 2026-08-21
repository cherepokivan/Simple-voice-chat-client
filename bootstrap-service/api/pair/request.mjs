import { handle, httpError, json, key, normalizeOtp, randomId, readJson, redis, sha256, ttl } from '../../lib/bridge.mjs';

export default handle(async (req) => {
  const body = await readJson(req);
  const tokenHash = sha256(normalizeOtp(body.code));
  const store = redis();
  const registration = await store.get(key('token', tokenHash));
  if (!registration) throw httpError(404, 'Pairing code is invalid or expired.');

  const existingRequest = await store.get(key('request-by-token', tokenHash));
  if (existingRequest) throw httpError(409, 'This pairing code is already being used.');

  const requestId = randomId();
  const readKey = randomId();
  const record = { tokenHash, readKeyHash: sha256(readKey), state: 'waiting' };
  const created = await store.set(key('request', requestId), record, { nx: true, ex: ttl.claim });
  if (!created) throw httpError(503, 'Could not create pairing request.');
  await store.set(key('request-by-token', tokenHash), requestId, { ex: ttl.claim });

  return json({ requestId, readKey, expiresInSeconds: ttl.claim, status: 'waiting' }, 202);
});
