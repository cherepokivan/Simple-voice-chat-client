import { assertPluginSignature, assertUuid, handle, httpError, json, key, readJson, redis, ttl } from '../../lib/bridge.mjs';

export default handle(async (req) => {
  const body = await readJson(req);
  const tokenHash = String(body.tokenHash ?? '');
  const playerUuid = assertUuid(body.playerUuid);
  if (!/^[A-Za-z0-9_-]{43}$/.test(tokenHash)) throw httpError(400, 'Invalid token digest.');
  assertPluginSignature(req, 'register', [tokenHash, playerUuid]);

  const store = redis();
  await store.set(key('token', tokenHash), { playerUuid }, { ex: ttl.claim });
  return json({ status: 'registered', expiresInSeconds: ttl.claim });
});
