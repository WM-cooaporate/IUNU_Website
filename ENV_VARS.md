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

### Client IP behind the proxy — get this right or the rate limits do nothing

Every request reaches the app through Render's edge proxy, so
`request.getRemoteAddr()` is the proxy's address for everyone. There are two
ways to get this wrong and both are silent.

**Trusting too little.** Key on `getRemoteAddr()` behind a proxy and every
visitor shares one bucket: the 10-logins-per-minute limit applies to the whole
site at once, so one attacker spending it locks every real admin out of the
dashboard.

**Trusting too much.** Read `X-Forwarded-For` without checking who sent it and
the caller picks their own bucket. A client can set the header itself, and
"the last entry is the one a proxy wrote" only holds *if a proxy wrote it* — a
request that reaches the origin directly (over the `*.onrender.com` URL, which
stays reachable) carries whatever the caller typed, end to end.

| Variable | Description | Value on Render |
|---|---|---|
| `CLIENT_IP_MODE` | `remote-addr` (no proxy — local development), `x-forwarded-for` (Render or Railway, no Cloudflare), or `cloudflare` (`CF-Connecting-IP`; only once traffic is proxied **and** the origin is closed). | `x-forwarded-for`, or `cloudflare` after runbook step 7 |
| `TRUSTED_PROXY_HOPS` | How many proxies sit in front of the app. The entry this far from the **right** of `X-Forwarded-For` is the real client. | `1` (Render alone), `2` (Cloudflare in front of Render) |
| `TRUSTED_PROXIES` | Comma-separated addresses or CIDR blocks. Forwarding headers are honoured **only** from a peer inside one of these; anything else falls back to the socket address. **Set this.** Leaving it blank logs a WARN at startup and means any peer can forge the header. | Render's internal proxy range, or Cloudflare's published ranges (<https://www.cloudflare.com/ips/>) — check what `getRemoteAddr()` actually is first |
| `RATE_LIMIT_TRUST_FORWARDED_HEADER` | The older boolean, superseded by `CLIENT_IP_MODE`. Still honoured when `CLIENT_IP_MODE` is unset, so an existing deployment does not change behaviour mid-release. | leave as is, or drop once `CLIENT_IP_MODE` is set |

### Optional — defaults apply if unset

| Variable | Description | Default |
|---|---|---|
| `DB_POOL_MAX` | Hikari maximum pool size. **Must stay below the database plan's connection limit**, counting every instance plus anything else that connects (migrations, a `psql` session, a backup job). Exceeding it does not degrade gracefully — the database refuses new connections and the app 500s. Raising it does not buy throughput once the database is the bottleneck; it converts slow requests into failed ones. | `5` in `prod`, `10` otherwise |
| `DB_POOL_SIZE` | The older name for `DB_POOL_MAX`, still honoured. | — |
| `TOMCAT_MAX_THREADS` | Concurrent in-flight requests. Requests beyond this queue rather than fail. Keep it well above `DB_POOL_MAX`: a thread waiting on the pool is cheap, a connection the server cannot accept is a refused request. | `100` |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | Access-token lifetime. | `900000` (15 min) |
| `JWT_ISSUER` | `iss` claim. | `iunu-real-estate-api` |
| `MAX_FAILED_LOGIN_ATTEMPTS` | Failed logins before the account locks. | `5` |
| `ACCOUNT_LOCK_DURATION_MINUTES` | Lock duration. | `15` |
| `RESET_TOKEN_EXPIRY_MINUTES` | Password-reset token lifetime. | `30` |
| `REFRESH_TOKEN_EXPIRY_DAYS` | Refresh-token lifetime. | `7` |
| `RATE_LIMIT_TRUST_FORWARDED_HEADER` | Read the client IP from `X-Forwarded-For`. `TRUST_FORWARDED_HEADER` is the older name and is still honoured. | `false` |
| `MAIL_ENABLED` | Send password-reset mail. **Password reset does not work without it.** When `false` outside the `prod` profile the reset link is logged so local development can complete the flow; under `prod` the link is never logged (it is a working account-takeover token) and a WARN says reset is not functioning. | `false` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP server. | `localhost` / `587` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials. | *(empty)* |
| `MAIL_FROM` | From address on outgoing mail. | `no-reply@iunu-eg.com` |
| `GOOGLE_TRANSLATE_API_KEY` | Google Cloud Translation API key. Enables automatic English -> Arabic translation of project content on save. Unset or blank disables the feature: saves still succeed, the Arabic fields stay empty, and the site falls back to the English text. | *(empty)* |
| `GOOGLE_TRANSLATE_BASE_URL` | Override for the translation API host. Only useful for pointing the backend at a stub in tests. | `https://translation.googleapis.com` |
| `GOOGLE_TRANSLATE_DAILY_CHAR_LIMIT` | Characters per UTC day across every caller. Google bills per character, so this bounds what a loop over the preview or backfill endpoint can cost. Past it the translator behaves as disabled: saves still succeed with English fallback. `0` means unlimited. **A backstop, not the cap** — it lives in one JVM's memory and resets on every deploy. The real cap is a quota in the Google Cloud Console (runbook step 9). | `200000` |
| `SWAGGER_ENABLED` | Publishes `/v3/api-docs` and `/swagger-ui`. **Off by default**, deliberately: a deploy that forgets `SPRING_PROFILES_ACTIVE=prod` must not hand an anonymous visitor the shape of every admin endpoint. Turn on temporarily for a ZAP scan. | `false` |
| `EDGE_SHARED_SECRET` | When set, requests without a matching `X-Edge-Auth` header are refused with 403, closing the origin to anything that did not come through Cloudflare. **Blank (disabled) by default.** Set it only *after* the Cloudflare Transform Rule that injects the header exists — the other order takes the API offline. `/actuator/health` stays exempt so deploys keep passing. See `docs/DDOS_RUNBOOK.md` step 8. | *(empty)* |
| `RATE_LIMIT_ENABLED` | Master switch for the in-app rate limiter. Leave on. Exists so the test suite can disable it. | `true` |
| `RATE_LIMIT_MAX_TRACKED_CLIENTS` | Ceiling on the limiter's bucket store. Bounded so a flood from many addresses cannot grow it until the JVM runs out of memory. | `100000` |
| `MAX_JSON_REQUEST_BYTES` | Largest non-multipart body accepted, checked against `Content-Length` before the stream is read. | `1048576` (1MB) |

### Arabic auto-translation

When an admin saves a project, the backend translates its title, description
and location into Arabic and stores both languages on the row. The public API
returns both, and the site's language toggle picks between them with no extra
request. Arabic the admin typed into the dashboard is always kept as typed.

`GOOGLE_TRANSLATE_API_KEY` is **backend only**. It is sent in the
`X-goog-api-key` header, never in a URL, and must never be given a `VITE_`
name — anything prefixed `VITE_` is compiled into the browser bundle and is
therefore public.

Setting one up:

1. Google Cloud Console -> **APIs & Services** -> enable **Cloud Translation API**.
2. **Credentials** -> **Create credentials** -> **API key**.
3. Edit the key and, under **API restrictions**, restrict it to *Cloud Translation API*.
4. Set it on the backend host (Railway / Render) only — never in Vercel or any
   other frontend project.

With the key absent the backend logs
`Google translation disabled: GOOGLE_TRANSLATE_API_KEY not set` at startup and
the dashboard's translation buttons report that translation is not configured.

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
6. Set `CLIENT_IP_MODE` and `TRUSTED_PROXIES`. Until `TRUSTED_PROXIES` is set,
   startup logs a WARN and the per-IP rate limits can be bypassed by anything
   that reaches the origin directly.
7. Set `MAIL_ENABLED=true` with real SMTP credentials, or accept that password
   reset does not work.
8. Free instances spin down when idle; the first request after that waits for a
   cold start, and anything in `UPLOAD_DIR` is gone.

## Before this is load-bearing for a client

- `docs/DDOS_RUNBOOK.md` — Cloudflare in front of the backend, and closing the
  origin behind it. Application code cannot stop a volumetric attack; that
  document is where the protection actually lives.
- `docs/SECURITY_AUDIT.md` — what was found, what was fixed, and the decisions
  left to you.
