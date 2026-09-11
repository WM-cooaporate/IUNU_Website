# Deployment environment variables

Backend (Spring Boot) and frontend (Vite/React) both deploy to **Render** from
the `render.yaml` Blueprint at the repository root. Everything below is either
wired up by that file or typed into the Render dashboard — no secret value
belongs in this repository, and every example here is a dummy.

The backend reads its configuration from `backend/src/main/resources/application.yml`
(defaults for local development) plus `application-prod.yml`, which is active
only when `SPRING_PROFILES_ACTIVE=prod`. **In the `prod` profile the required
variables have no defaults**: if one is missing the container fails at startup
instead of quietly falling back to a localhost database or an empty JWT secret.

Applying the Blueprint creates three things: the managed PostgreSQL database
`iunu-db`, the Docker web service `iunu-api` (root directory `backend`), and the
static site `iunu-web` (built from the repository root).

---

## `iunu-api` — backend service

### Wired automatically by `render.yaml`

Nothing to type. Listed so the mapping is visible.

| Variable | Source |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Literal `prod`. |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Pulled off `iunu-db`. The `prod` profile assembles `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` from them, so no connection string is duplicated by hand. |
| `JWT_SECRET` | `generateValue: true` — Render generates a 256-bit value on first sync and keeps it across deploys. `JwtService` refuses to start on anything shorter than 32 bytes. |
| `RATE_LIMIT_TRUST_FORWARDED_HEADER` | Literal `true`. See **Rate limiting behind the proxy** below. |
| `UPLOAD_DIR` | `/tmp/uploads`. See **Uploads are ephemeral on the free plan** below. |
| `PORT` | Injected by Render and read automatically — never set it by hand. |

### Prompted on first sync (`sync: false`)

| Variable | Description | Example (dummy) |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call the API. Must be the static site's origin, scheme included, **no trailing slash and no path**. Wildcards are rejected at startup. | `https://iunu-web.onrender.com` |
| `FRONTEND_URL` | Base URL used to build password-reset links in outgoing mail. | `https://iunu-web.onrender.com` |
| `PUBLIC_API_URL` | Public origin of this backend, **without** `/api`. Uploaded images are handed to the frontend as `${PUBLIC_API_URL}/uploads/...`, so a wrong value produces broken images. | `https://iunu-api.onrender.com` |

### Uploads are ephemeral on the free plan

Render's free instances have no persistent disk, and they spin down when idle.
`UPLOAD_DIR` therefore points at `/tmp/uploads`, and **every uploaded cover
image is lost on redeploy and on every cold start**. The database keeps rows
pointing at image URLs whose files no longer exist, so the site renders broken
images.

To keep uploads, attach a **Render Disk** (paid plans only) to `iunu-api` and
set `UPLOAD_DIR` to its mount path — the two must be identical.
`LocalImageStorage` creates the directory at startup and refuses to boot if it
is not writable, so a misconfigured mount fails loudly rather than at the first
admin upload.

### Rate limiting behind the proxy

Every request reaches the app through Render's edge proxy, so
`request.getRemoteAddr()` is the proxy's address for everyone. With
`RATE_LIMIT_TRUST_FORWARDED_HEADER=false` the per-IP limits collapse into one
global bucket and the 10-logins-per-minute limit applies to the whole site at
once — one attacker would lock every visitor out of logging in.

Set to `true` (as `render.yaml` does) the limiter reads `X-Forwarded-For` and
keys on its **last** entry. That is deliberate: a client can send its own
`X-Forwarded-For` and the proxy appends to it, so the first entry is
attacker-controlled and the last is the one the trusted proxy wrote. Only set
this to `true` where a proxy you trust actually terminates every request.

### Optional — defaults apply if unset

| Variable | Description | Default |
|---|---|---|
| `DB_POOL_SIZE` | Hikari maximum pool size. Kept small in `prod`: free PostgreSQL allows few connections and the free instance has 0.1 CPU. | `5` in `prod`, `10` otherwise |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | Access-token lifetime. | `900000` (15 min) |
| `JWT_ISSUER` | `iss` claim. | `iunu-real-estate-api` |
| `MAX_FAILED_LOGIN_ATTEMPTS` | Failed logins before the account locks. | `5` |
| `ACCOUNT_LOCK_DURATION_MINUTES` | Lock duration. | `15` |
| `RESET_TOKEN_EXPIRY_MINUTES` | Password-reset token lifetime. | `30` |
| `REFRESH_TOKEN_EXPIRY_DAYS` | Refresh-token lifetime. | `7` |
| `RATE_LIMIT_TRUST_FORWARDED_HEADER` | Read the client IP from `X-Forwarded-For`. `TRUST_FORWARDED_HEADER` is the older name and is still honoured. | `false` |
| `MAIL_ENABLED` | Send password-reset mail. When `false`, reset links are logged instead. | `false` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP server. | `localhost` / `587` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials. | *(empty)* |
| `MAIL_FROM` | From address on outgoing mail. | `no-reply@iunu-eg.com` |

Actuator's mail health indicator is switched off (`management.health.mail.enabled: false`).
It probes `MAIL_HOST` on every call, so with mail disabled it reported `DOWN`
and took `/actuator/health` — which Render gates releases on — down with it.

### One-time admin bootstrap — set, deploy once, then remove

| Variable | Description | Example (dummy) |
|---|---|---|
| `ADMIN_EMAIL` | Email of the first ADMIN user. | `admin@example.com` |
| `ADMIN_PASSWORD` | Its password; at least 8 characters or the account is not created. | `replace-with-a-strong-password` |

These take effect only while the database contains no ADMIN at all, so leaving
them set cannot add further admins. Remove them after the first successful
deploy and create any further admins via `POST /api/admin/users`.

---

## `iunu-web` — frontend static site

The Vite project lives at the repository root (`package.json`, `vite.config.js`).
`render.yaml` sets build command `npm ci && npm run build` and publish path
`dist`, with a rewrite of `/*` to `/index.html` for client-side routing.

| Variable | Description | Example (dummy) |
|---|---|---|
| `VITE_API_URL` | Backend API base URL. `src/services/apiClient.js` normalises it in one place - trailing slashes are stripped and `/api` is appended when the value does not already end in `/api`, so `https://iunu-api.onrender.com` and `https://iunu-api.onrender.com/api` both reach `/api/properties`. A production build with this unset logs an error and every API call fails. | `https://iunu-api.onrender.com/api` |

Vite inlines `VITE_*` variables into the shipped bundle **at build time**, so
changing this value requires a rebuild, and every value here is public. Auth
uses `Authorization: Bearer` tokens, not cookies, so the frontend never sets
`withCredentials`.

---

## Checklist

1. Create a Blueprint from `render.yaml`. Render prompts for the `sync: false`
   variables; the URLs are not known yet, so put placeholders in
   `CORS_ALLOWED_ORIGINS`, `FRONTEND_URL` and `PUBLIC_API_URL` for now, and set
   `ADMIN_EMAIL` / `ADMIN_PASSWORD` to the first admin you want.
2. Let the first deploy finish and note both service URLs.
3. Set `PUBLIC_API_URL`, `CORS_ALLOWED_ORIGINS` and `FRONTEND_URL` to the real
   origins and redeploy `iunu-api`.
4. Set `VITE_API_URL=<api URL>/api` on `iunu-web` and **rebuild** — Vite inlines
   it, so a restart alone will not pick it up.
5. Confirm you can log in as the bootstrapped admin, then remove `ADMIN_EMAIL`
   and `ADMIN_PASSWORD` and redeploy.
6. Free instances spin down when idle; the first request after that waits for a
   cold start, and anything in `UPLOAD_DIR` is gone.
