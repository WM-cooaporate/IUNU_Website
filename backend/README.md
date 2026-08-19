# IUNU Real Estate — Backend

Spring Boot 3 / Java 21 REST API backing the `projects/iunu-website` frontend.
MySQL for persistence, JWT for auth. No test suite is included by design
(per project scope) — see **Verification** below for how this was checked.

## Stack

- Java 21, Spring Boot 3.3
- Spring Web, Spring Security, Spring Data JPA
- MySQL 8 (via `mysql-connector-j`) + Flyway migrations
- JJWT for access tokens
- Bucket4j for in-memory rate limiting
- springdoc-openapi (Swagger UI)

## Getting started

### 1. Configure environment

Copy `.env.example` to `.env` and fill in real values (see comments in the
file). At minimum you must set `JWT_SECRET` and the `DB_*` variables.

```bash
cp .env.example .env
openssl rand -base64 48   # paste the output into JWT_SECRET
```

### 2a. Run with Docker Compose (MySQL + API)

```bash
docker compose up --build
```

### 2b. Run locally against your own MySQL

```bash
export $(grep -v '^#' .env | xargs)   # or use a tool like direnv
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Flyway creates the schema
automatically on first boot.

### 3. Create the first admin user

Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` before starting the app once — a
startup runner creates that one ADMIN account if it doesn't already exist.
Unset both afterwards. There is no default/seeded admin account baked into
the code or migrations.

### API docs

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Connecting the existing frontend

`projects/iunu-website` currently calls a mocked `authServices.js`. To wire
it to this backend (not done here, per "don't touch the frontend"):

- Point axios at `http://localhost:8080/api` (or your deployed URL) and add
  it to `CORS_ALLOWED_ORIGINS`.
- `POST /api/auth/register` expects `{ fullName, email, phone, password }`
  matching `Register.jsx`'s form fields.
- `POST /api/auth/login` expects `{ email, password }` and returns
  `{ accessToken, refreshToken, tokenType, expiresInSeconds, user }`.
- `POST /api/auth/forgot-password` expects `{ email }` and always returns a
  generic success message, matching the existing UI copy.
- The contact form (`Contact.jsx`), quote form (`QuoteForm.jsx`), and
  newsletter field (`Project.jsx`) currently have no submit wiring at all;
  `POST /api/contact`, `POST /api/quotes`, `POST /api/newsletter` are ready
  for them respectively.

## API overview

All endpoints are prefixed `/api`. Full request/response schemas are in
Swagger UI; this is the shape of the surface.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | public | Create account |
| POST | `/auth/login` | public | Get access + refresh token |
| POST | `/auth/refresh` | public (valid refresh token) | Rotate tokens |
| POST | `/auth/logout` | public (valid refresh token) | Revoke a refresh token |
| POST | `/auth/forgot-password` | public | Request reset email (generic response always) |
| POST | `/auth/reset-password` | public (valid reset token) | Set new password |
| POST | `/auth/change-password` | user | Change password while logged in |
| GET | `/auth/me` | user | Current user profile |
| GET | `/properties` | public | List published properties (filter by `type`) |
| GET | `/properties/{id}` | public | Get one published property |
| GET/POST/PUT/DELETE | `/properties/admin*`, `/properties` (write) | admin | Manage listings |
| POST | `/contact` | public | Submit contact form |
| POST | `/quotes` | public | Submit quote request |
| POST | `/newsletter` | public | Subscribe an email |
| GET/PATCH | `/admin/contacts*`, `/admin/quotes*` | admin | Review/triage leads |

## Security measures implemented

- **Passwords**: BCrypt (strength 12); never logged; min 8 chars + letter + number enforced server-side.
- **Auth tokens**: short-lived (15 min default) stateless JWT access tokens (HMAC-SHA256, secret from env, min 32 bytes, fails fast to start if unset/short) + opaque, DB-backed, single-use-per-rotation refresh tokens stored only as a SHA-256 hash (raw value never persisted), so a leaked DB cannot mint tokens and refresh tokens are individually revocable.
- **Account lockout**: 5 failed logins locks the account for 15 minutes (both configurable); auto-unlocks on the next attempt after the window passes.
- **Enumeration resistance**: login and forgot-password give identical generic responses regardless of whether the account exists; `DaoAuthenticationProvider.hideUserNotFoundExceptions` is enabled.
- **Password reset**: single-use, hashed, 30-minute-expiry tokens; resetting a password revokes all of that user's refresh tokens; changing a password does the same.
- **Authorization**: stateless JWT filter + Spring Security URL rules (admin-only paths, public GET/POST paths) *and* `@PreAuthorize` on controller methods (defense in depth); role model is `USER`/`ADMIN`.
- **Rate limiting**: per-IP token buckets on `/auth/login`, `/auth/register`, `/auth/forgot-password`, `/auth/reset-password` (10/min) and on the public lead forms `/contact`, `/quotes`, `/newsletter` (20/hour), to blunt credential stuffing and form spam. `X-Forwarded-For` is only trusted if `TRUST_FORWARDED_HEADER=true` is explicitly set (only turn this on behind a reverse proxy you control), otherwise it's spoofable and ignored.
- **Transport/response hardening**: HSTS, `X-Frame-Options: DENY`, a restrictive CSP, `Referrer-Policy: no-referrer`; CORS is an explicit allow-list from `CORS_ALLOWED_ORIGINS` (not `*`); stack traces and internal exception details are never returned in API responses (`server.error.include-*=never`, and a global exception handler that logs internally but returns generic messages).
- **Input validation**: Jakarta Bean Validation on every request DTO (email format, phone pattern, length caps, password policy, enum-constrained fields like property `type`/`spaceType`); SQL injection is not reachable since all persistence goes through parameterized JPA/Hibernate queries — no string-concatenated SQL anywhere.
- **CSRF**: disabled deliberately — the API is stateless (Bearer tokens, no cookies/sessions), which is the standard, safe posture for this architecture.
- **Secrets**: nothing is hardcoded — DB credentials, `JWT_SECRET`, mail credentials, and the one-time admin bootstrap all come from environment variables (`.env`, gitignored; `.env.example` documents every key with no real values committed).
- **No default/backdoor account**: the seed admin is only created if `ADMIN_EMAIL`/`ADMIN_PASSWORD` are explicitly set at boot; nothing is baked into a migration or the codebase.
- **DB schema ownership**: `ddl-auto=validate` — Hibernate can never silently alter production schema; Flyway migrations (`src/main/resources/db/migration`) are the only way schema changes ship.
- **Actuator**: only `/actuator/health` is exposed, with `show-details: never`.

### Known trade-offs / what a real deployment should add on top

- Access tokens can't be revoked before they expire (stateless JWT trade-off) — kept the TTL short (15 min) to bound the blast radius; refresh tokens *are* individually revocable.
- Rate limiting is in-memory/per-instance (Bucket4j `local`), fine for a single-instance deployment; move to a shared store (e.g. Bucket4j + Redis) if this ever runs behind a load balancer with multiple instances.
- No email verification step on registration (frontend doesn't currently ask for one either) — accounts are immediately usable after `/auth/register`.
- The `/actuator/health` and Swagger UI endpoints are open; consider restricting Swagger UI to non-production environments or putting it behind auth before going live.
- TLS termination is expected to happen at the load balancer/reverse proxy in front of this app (HSTS is sent assuming that's the case).

## Verification performed

There's no MySQL/Docker daemon available in the sandbox this was built in,
so full end-to-end verification against real MySQL wasn't possible here.
What *was* verified before handoff:

1. `mvn compile` / `mvn package` succeed cleanly.
2. The app was booted with `spring-boot:test-run` against an in-memory H2
   database (MySQL-compatibility mode) to catch Spring wiring issues
   (missing beans, bad `@Value` bindings, security filter chain errors,
   entity mapping mismatches) that a compile alone wouldn't catch.
3. Smoke-tested the main flows with curl against that instance: register,
   login (+ lockout after repeated bad passwords), refresh, forgot-password
   (generic response), contact/quote/newsletter submission, and public
   property listing.

Run it yourself against real MySQL before deploying — see **Getting
started** above.
