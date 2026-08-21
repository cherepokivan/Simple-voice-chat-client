# Simple Voice Bootstrap Service

This isolated Vercel service relays an **authorised, one-time** pairing exchange between a standalone client and the Paper plugin. It is deliberately not a general voice-chat API and never accepts a UUID, SVC secret, or bootstrap from an unauthenticated client.

## Security model

1. The Paper plugin issues an OTP only through a Minecraft session that has already been authenticated by the server.
2. The plugin registers only the SHA-256 digest of that OTP and the authenticated player UUID.
3. A client submits the OTP over HTTPS. The service stores only its digest and creates a random request ID plus a separate read key.
4. The plugin polls by OTP digest using HMAC authentication. It consumes the local OTP, produces the server-authorised bootstrap, and submits it with an HMAC signature.
5. The client reads the bootstrap exactly once using the request ID and read key. The response is immediately deleted.

The service never logs OTPs, UUIDs, session secrets, request read keys, request bodies, or bootstrap contents.

## Required Vercel environment variables

| Variable | Purpose |
|---|---|
| `UPSTASH_REDIS_REST_URL` | Redis REST URL for short-lived state. |
| `UPSTASH_REDIS_REST_TOKEN` | Redis REST token. |
| `BRIDGE_SERVER_ID` | Opaque identifier of the one Minecraft server, such as `main-eu-1`. |
| `BRIDGE_SHARED_SECRET` | At least 32 random bytes encoded as Base64URL; identical to the Paper plugin configuration. |

The Redis database must be private. All records expire automatically; the service is not a source of truth for player identities.

## API surface

| Route | Caller | Purpose |
|---|---|---|
| `GET /api/health` | Operators | Verifies configuration without revealing secrets. |
| `POST /api/pair/request` | Standalone client | Creates a short-lived claim from an OTP. |
| `POST /api/pair/status` | Standalone client | Reads the claim state and consumes a ready bootstrap once. |
| `POST /api/plugin/register` | Paper plugin | Registers an OTP digest for an authenticated player. |
| `POST /api/plugin/check` | Paper plugin | Checks whether a client has claimed one active OTP. |
| `POST /api/plugin/complete` | Paper plugin | Stores a single bootstrap response for a claimed OTP. |

Plugin routes require `X-Bridge-Server`, `X-Bridge-Timestamp`, and `X-Bridge-Signature`. The signature is HMAC-SHA-256 over the canonical request fields implemented in `lib/bridge.mjs`.
