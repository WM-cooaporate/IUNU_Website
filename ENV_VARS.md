# Deployment environment variables

Backend (Spring Boot) on **Railway**, frontend (Vite/React) on **Vercel**.
Everything below is typed into a dashboard — no secret value belongs in this
repository, and every example here is a dummy.

The backend reads its configuration from `backend/src/main/resources/application.yml`
(defaults for local development) plus `application-prod.yml`, which is active
only when `SPRING_PROFILES_ACTIVE=prod`. **In the `prod` profile the required
variables have no defaults**: if one is missing the container fails at startup
instead of quietly falling back to a localhost database or an empty JWT secret.

---

## Railway — backend service

Set the service **Root Directory** to `backend` so the build context matches
`backend/Dockerfile` (multi-stage Maven → Temurin 21 JRE, runs as a non-root
user, produces `target/real-estate-backend.jar`).

### Required

| Variable | Description | Example (dummy) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Activates `application-prod.yml`. Without it the app runs on development defaults. | `prod` |
| `DB_URL` | JDBC URL of the MySQL database. Railway's MySQL plugin supplies host/port/name. | `jdbc:mysql://mysql.railway.internal:3306/railway?useSSL=true&serverTimezone=UTC&allowPublicKeyRetrieval=false` |
| `DB_USERNAME` | Database user. | `iunu_app` |
| `DB_PASSWORD` | Database password. | `replace-with-the-railway-generated-value` |
| `JWT_SECRET` | HMAC signing key for access tokens. At least 32 bytes; generate with `openssl rand -base64 48`. | `replace-with-a-freshly-generated-random-string` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call the API. Wildcards are rejected at startup. Must be the Vercel origin, scheme included, **no trailing slash**. | `https://iunu.vercel.app,https://www.iunu-eg.com` |
| `FRONTEND_URL` | Base URL used to build password-reset links in outgoing mail. | `https://iunu.vercel.app` |
| `PUBLIC_API_URL` | Public origin of this backend. Uploaded images are handed to the frontend as `${PUBLIC_API_URL}/uploads/...`, so a wrong value produces broken images. | `https://iunu-backend.up.railway.app` |
| `UPLOAD_DIR` | Directory the backend writes uploaded images to. **Must equal the volume mount path below.** | `/data/uploads` |

`PORT` is injected by Railway and read automatically — do not set it by hand.

### Volume — required, or images vanish on every deploy

The container filesystem is wiped on redeploy. The database keeps rows pointing
at image URLs whose files no longer exist, so the site renders broken images.

Attach a **Railway Volume** to the backend service:

| Setting | Value |
|---|---|
| Mount path | `/data/uploads` |
| Matching variable | `UPLOAD_DIR=/data/uploads` |

The mount path and `UPLOAD_DIR` must be identical. `LocalImageStorage` creates
the directory at startup and refuses to boot if it is not writable, so a
misconfigured mount fails loudly rather than at the first admin upload.

### Optional — defaults apply if unset

| Variable | Description | Default |
|---|---|---|
| `DB_POOL_SIZE` | Hikari maximum pool size. | `10` |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | Access-token lifetime. | `900000` (15 min) |
| `JWT_ISSUER` | `iss` claim. | `iunu-real-estate-api` |
| `MAX_FAILED_LOGIN_ATTEMPTS` | Failed logins before the account locks. | `5` |
| `ACCOUNT_LOCK_DURATION_MINUTES` | Lock duration. | `15` |
| `RESET_TOKEN_EXPIRY_MINUTES` | Password-reset token lifetime. | `30` |
| `REFRESH_TOKEN_EXPIRY_DAYS` | Refresh-token lifetime. | `7` |
| `TRUST_FORWARDED_HEADER` | Read the client IP from `X-Forwarded-For`. Already forced to `true` by the `prod` profile because Railway proxies every request. | `true` in `prod` |
| `MAIL_ENABLED` | Send password-reset mail. When `false`, reset links are logged instead. | `false` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP server. | `localhost` / `587` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials. | *(empty)* |
| `MAIL_FROM` | From address on outgoing mail. | `no-reply@iunu-eg.com` |

### One-time admin bootstrap — set, deploy once, then remove

| Variable | Description | Example (dummy) |
|---|---|---|
| `ADMIN_EMAIL` | Email of the first ADMIN user. | `admin@example.com` |
| `ADMIN_PASSWORD` | Its password; at least 8 characters or the account is not created. | `replace-with-a-strong-password` |

These take effect only while the database contains no ADMIN at all, so leaving
them set cannot add further admins. Remove them after the first successful
deploy and create any further admins via `POST /api/admin/users`.

---

## Vercel — frontend project

The Vite project lives at the repository root (`package.json`, `vite.config.js`,
`vercel.json`). Build command `npm run build`, output directory `dist`.

| Variable | Description | Example (dummy) |
|---|---|---|
| `VITE_API_URL` | Backend API base URL **including the `/api` segment**. Trailing slashes are stripped by `src/services/apiClient.js`. A production build with this unset throws at load rather than silently calling localhost. | `https://iunu-backend.up.railway.app/api` |

Vite inlines `VITE_*` variables into the shipped bundle, so treat every value
here as public. Auth uses `Authorization: Bearer` tokens, not cookies, so the
frontend never sets `withCredentials`.

---

## Checklist

1. Deploy the backend with the Railway variables above, `SPRING_PROFILES_ACTIVE=prod`,
   and the volume mounted at `/data/uploads`.
2. Note the backend's public URL; set it as `PUBLIC_API_URL` and redeploy.
3. Deploy the frontend on Vercel with `VITE_API_URL=<backend URL>/api`.
4. Set `CORS_ALLOWED_ORIGINS` and `FRONTEND_URL` to the Vercel origin and redeploy
   the backend.
5. Set `ADMIN_EMAIL`/`ADMIN_PASSWORD`, deploy once, confirm you can log in, then
   remove both variables.
