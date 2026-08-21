import crypto from 'node:crypto';
import { Redis } from '@upstash/redis';

const OTP_PATTERN = /^[A-HJ-NP-Z2-9]{8,64}$/;
const CLAIM_TTL_SECONDS = 120;
const RESPONSE_TTL_SECONDS = 30;

export function json(response, status = 200) {
  return {
    statusCode: status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff'
    },
    body: JSON.stringify(response)
  };
}

export function redis() {
  if (!process.env.UPSTASH_REDIS_REST_URL || !process.env.UPSTASH_REDIS_REST_TOKEN) {
    throw new Error('Storage is not configured.');
  }
  return Redis.fromEnv();
}

export async function readJson(req) {
  if (req.method !== 'POST') {
    throw httpError(405, 'Method not allowed.');
  }
  const raw = typeof req.body === 'string' ? req.body : JSON.stringify(req.body ?? {});
  if (raw.length > 16_384) {
    throw httpError(413, 'Request body is too large.');
  }
  try {
    return JSON.parse(raw);
  } catch {
    throw httpError(400, 'Invalid JSON.');
  }
}

export function normalizeOtp(value) {
  const normalized = String(value ?? '').replace(/[\s-]/g, '').toUpperCase();
  if (!OTP_PATTERN.test(normalized)) {
    throw httpError(400, 'Invalid pairing code.');
  }
  return normalized;
}

export function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('base64url');
}

export function randomId(bytes = 24) {
  return crypto.randomBytes(bytes).toString('base64url');
}

export function httpError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}

export function key(kind, id) {
  return `svc-bridge:${kind}:${id}`;
}

export function configuredServerId() {
  const value = process.env.BRIDGE_SERVER_ID;
  if (!value || value.length < 3 || value.length > 128) {
    throw new Error('BRIDGE_SERVER_ID is not configured.');
  }
  return value;
}

export function assertPluginSignature(req, action, fields) {
  const serverId = String(req.headers['x-bridge-server'] ?? '');
  const timestamp = String(req.headers['x-bridge-timestamp'] ?? '');
  const signature = String(req.headers['x-bridge-signature'] ?? '');
  const sharedSecret = process.env.BRIDGE_SHARED_SECRET;

  if (!sharedSecret || sharedSecret.length < 32) {
    throw new Error('BRIDGE_SHARED_SECRET is not configured.');
  }
  if (serverId !== configuredServerId()) {
    throw httpError(401, 'Unknown bridge server.');
  }
  const numericTimestamp = Number(timestamp);
  if (!Number.isInteger(numericTimestamp) || Math.abs(Date.now() - numericTimestamp) > 60_000) {
    throw httpError(401, 'Expired bridge request.');
  }

  const canonical = [action, serverId, timestamp, ...fields.map(String)].join('\n');
  const expected = crypto.createHmac('sha256', sharedSecret).update(canonical).digest('base64url');
  const actual = Buffer.from(signature);
  const expectedBuffer = Buffer.from(expected);
  if (actual.length !== expectedBuffer.length || !crypto.timingSafeEqual(actual, expectedBuffer)) {
    throw httpError(401, 'Invalid bridge signature.');
  }
  return serverId;
}

export function assertUuid(value) {
  const uuid = String(value ?? '').toLowerCase();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(uuid)) {
    throw httpError(400, 'Invalid player identity.');
  }
  return uuid;
}

export function handle(handler) {
  return async (req, res) => {
    try {
      const output = await handler(req);
      res.status(output.statusCode).set(output.headers).send(output.body);
    } catch (error) {
      const status = Number.isInteger(error?.status) ? error.status : 503;
      if (status >= 500) console.error('Bridge request failed:', error?.message ?? 'unknown error');
      res.status(status).set({
        'content-type': 'application/json; charset=utf-8',
        'cache-control': 'no-store',
        'x-content-type-options': 'nosniff'
      }).send(JSON.stringify({ error: status >= 500 ? 'Service unavailable.' : error.message }));
    }
  };
}

export const ttl = { claim: CLAIM_TTL_SECONDS, response: RESPONSE_TTL_SECONDS };
