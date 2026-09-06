# IUNU Real Estate — Backend

Spring Boot 3 / Java 21 REST API backing the `projects/iunu-website` frontend.
MySQL for persistence, JWT for auth. Covers the public site (published
projects and properties, lead-capture forms) and the authenticated admin
dashboard behind it.

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

There is no default or seeded admin account anywhere in the code or the
migrations. The first one is created at startup from the environment:

```bash
export ADMIN_EMAIL=you@example.com
export ADMIN_PASSWORD='a-long-password'   # at least 8 characters
mvn spring-boot:run
```

`AdminBootstrapRunner` creates that single ADMIN, BCrypt-hashing the
password, and logs `Bootstrapped initial ADMIN user: ...`.

The rules it follows:

- It only acts when **no user with `role = ADMIN` exists at all**. The gate
  is the ADMIN role, not the email — so leaving these vars set cannot
  quietly add a second admin to a running deployment.
- If `ADMIN_PASSWORD` is under 8 characters it logs an error and creates
  **nothing**. (`ADMIN_PASSWORD is N characters; at least 8 are required...`)
- If either var is unset or blank it does nothing at all.
- It is idempotent, so restarting with the vars still set is harmless.

**After the first successful run, unset `ADMIN_EMAIL` and `ADMIN_PASSWORD`**
from the environment / your deployment's secrets. Create any further admins
through `POST /api/admin/users` (below) — there is no public endpoint that
can mint an ADMIN.

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
  `{ accessToken, refreshToken, tokenType, expiresInSeconds, expiresAt, user }`
  — see **Projects & admin API** below for a full example.
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
| GET | `/projects` | public | List published projects, newest first |
| GET | `/projects/{id}` | public | Get one published project (404 on a draft) |
| GET | `/admin/projects` | admin | List all projects, published and draft |
| POST | `/admin/projects` | admin | Create a project (draft by default) |
| PUT | `/admin/projects/{id}` | admin | Update a project, incl. publish/unpublish |
| DELETE | `/admin/projects/{id}` | admin | Delete a project |
| POST | `/admin/projects/{id}/cover-image` | admin | Upload/replace the cover image (multipart) |
| GET | `/admin/users` | admin | List staff accounts |
| POST | `/admin/users` | admin | Create another admin |
| GET | `/properties` | public | List published properties (filter by `type`) |
| GET | `/properties/{id}` | public | Get one published property |
| GET/POST/PUT/DELETE | `/properties/admin*`, `/properties` (write) | admin | Manage listings |
| POST | `/contact` | public | Submit contact form |
| POST | `/quotes` | public | Submit quote request |
| POST | `/newsletter` | public | Subscribe an email |
| GET/PATCH | `/admin/contacts*`, `/admin/quotes*` | admin | Review/triage leads |

## Projects & admin API

Everything the admin dashboard and the public Projects page need, with the
exact shapes they return. `{{token}}` below is the `accessToken` from login.

### Login

`POST /api/auth/login` — public.

```json
{ "email": "you@example.com", "password": "a-long-password" }
```

`200 OK`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "b7c1e4f0-...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "expiresAt": "2026-09-05T20:31:44.512Z",
  "user": {
    "id": 1,
    "fullName": "Administrator",
    "email": "you@example.com",
    "phone": "N/A",
    "role": "ADMIN",
    "createdAt": "2026-09-01T10:00:00Z"
  }
}
```

`expiresAt` is the ISO-8601 absolute expiry of `accessToken` — use it to
decide when to call `/api/auth/refresh`. The `User` entity is never
serialized directly and `password` appears in no response.

On bad credentials, `401` with the same generic body whether or not the
email exists:

```json
{
  "timestamp": "2026-09-05T20:16:44.512Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/api/auth/login",
  "fieldErrors": []
}
```

Send the token on every admin request as `Authorization: Bearer {{token}}`.

### The Project shape

Every project endpoint — public, admin, and the cover-image upload — returns
this same object:

```json
{
  "id": 12,
  "title": "Nile Tower",
  "description": "Mixed-use development on the Corniche.",
  "location": "New Cairo",
  "status": "UNDER_CONSTRUCTION",
  "priceRange": "From 4,500,000 EGP",
  "coverImageUrl": "http://localhost:8080/uploads/projects/9f2b...c1.png",
  "published": true,
  "createdAt": "2026-09-05T19:04:11.204Z",
  "updatedAt": "2026-09-05T19:22:03.881Z"
}
```

- `title` is the only required field. Everything else may be `null`, so the
  dashboard can save a draft before the copy exists.
- `status` is one of `PLANNED`, `UNDER_CONSTRUCTION`, `COMPLETED`,
  `SOLD_OUT`, or `null`.
- `priceRange` is free text ("From 4,500,000 EGP", "On request"), not a
  number — render it as-is.
- `coverImageUrl` is an absolute URL built from `PUBLIC_API_URL`, or `null`.

List endpoints return a Spring `Page`, so the array is under `content`:

```json
{
  "content": [ { "id": 12, "title": "Nile Tower", "...": "..." } ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 12
}
```

### Public endpoints (no auth)

`GET /api/projects` — published projects only, newest first. Paged with
`?page=0&size=12`.

`GET /api/projects/{id}` — one published project. A draft returns exactly
the same `404` as a project that doesn't exist, so drafts can't be probed:

```json
{
  "timestamp": "2026-09-05T20:16:44.512Z",
  "status": 404,
  "error": "Not Found",
  "message": "Project not found",
  "path": "/api/projects/12",
  "fieldErrors": []
}
```

### Admin endpoints (`Authorization: Bearer {{token}}`, ROLE_ADMIN)

`GET /api/admin/projects` — all projects including drafts, newest first,
paged (`?page=0&size=20`).

`GET /api/admin/projects/{id}` — any project, published or not.

`POST /api/admin/projects` — create. Request:

```json
{
  "title": "Nile Tower",
  "description": "Mixed-use development on the Corniche.",
  "location": "New Cairo",
  "status": "UNDER_CONSTRUCTION",
  "priceRange": "From 4,500,000 EGP",
  "coverImageUrl": null,
  "published": false
}
```

`201 Created` with the Project shape above. **A new project is a draft
unless you explicitly send `"published": true`.**

`PUT /api/admin/projects/{id}` — update; same body as create, `200 OK` with
the updated project. Publish or unpublish by sending `"published": true` /
`false`. **Omitting `published` leaves the current state alone**, so an
ordinary edit can't accidentally take a live project off the site.

`DELETE /api/admin/projects/{id}` — `204 No Content`.

### Cover image upload

`POST /api/admin/projects/{id}/cover-image` — `multipart/form-data` with a
single part named `file`. JPG, PNG or WEBP, max 5 MB.

```bash
curl -X POST http://localhost:8080/api/admin/projects/12/cover-image \
  -H "Authorization: Bearer {{token}}" \
  -F "file=@cover.png"
```

`200 OK`:

```json
{
  "coverImageUrl": "http://localhost:8080/uploads/projects/9f2b...c1.png",
  "project": {
    "id": 12,
    "title": "Nile Tower",
    "coverImageUrl": "http://localhost:8080/uploads/projects/9f2b...c1.png",
    "published": true,
    "...": "the full Project shape"
  }
}
```

The upload saves the URL on the project and returns the whole updated
project alongside it, so the dashboard can refresh the row from one
response. Uploading again replaces the cover and deletes the old file if
nothing else references it.

Files are stored under `${UPLOAD_LOCATION}/projects/`, named by the SHA-256
of their bytes (re-uploading the same image is a no-op), and served from
`GET /uploads/projects/{filename}` with no auth — they're public assets.
Every caller goes through `ImageStorageService`, so moving to S3 or Cloud
Storage later means reimplementing `store`/`deleteIfStored` and nothing else.

A non-image or empty file is a `400`, not a `500`:

```json
{
  "timestamp": "2026-09-05T20:16:44.512Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Only JPG, PNG and WEBP images are supported",
  "path": "/api/admin/projects/12/cover-image",
  "fieldErrors": []
}
```

Over 5 MB is a `413`.

### Creating more admins

`POST /api/admin/users` — requires an existing admin's token. There is no
public route to an ADMIN account.

```json
{
  "fullName": "Second Admin",
  "email": "second@example.com",
  "phone": "+20 100 111 2222",
  "password": "Password1",
  "role": "ADMIN"
}
```

`role` is optional and defaults to `ADMIN`. `phone` is optional. The
password must be at least 8 characters with a letter and a digit.

`201 Created`:

```json
{
  "id": 2,
  "fullName": "Second Admin",
  "email": "second@example.com",
  "phone": "+20 100 111 2222",
  "role": "ADMIN",
  "createdAt": "2026-09-05T20:16:44.512Z"
}
```

`GET /api/admin/users` lists staff accounts as a page of that same shape.

### Error shape

Every error is the same JSON object, from one `@RestControllerAdvice`:

```json
{
  "timestamp": "2026-09-05T20:16:44.512Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/admin/projects",
  "fieldErrors": [
    { "field": "title", "message": "Title is required" }
  ]
}
```

`fieldErrors` is `[]` for everything except Bean Validation failures.
Statuses used: `400` validation/malformed/bad upload, `401` missing or
invalid token, `403` valid token without ROLE_ADMIN, `404` missing or
unpublished, `409` data conflict, `413` upload too large, `429` rate
limited, `500` unexpected (generic message; details are logged, never
returned).

### Projects vs. properties

`projects` and `properties` are separate tables with separate endpoints, and
they overlap heavily — a project is editorial content for the Projects page
(title, blurb, cover image, published flag), a property is a sellable unit
with a type, area and price. The frontend currently drives the admin
dashboard off `/api/properties/admin`. Worth deciding whether both should
survive before wiring the dashboard to `/api/projects`; consolidating later
is a migration, not a rewrite.

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

```bash
mvn test
```

30 tests, all passing. They run against in-memory H2 (`test` profile,
`src/test/resources/application-test.yml`) with Hibernate generating the
schema, because the Flyway migrations are MySQL-specific DDL.

What they cover:

- **`PublicProjectApiTest`** — the public listing returns published projects
  only, newest first, with no token; a draft 404s identically to a missing
  project.
- **`AdminProjectApiTest`** — 401 without a token, 403 for a non-admin, 401
  on a forged token; create defaults to draft; `published: true` on create
  and on update reaches the public site; omitting `published` on update
  leaves it alone; delete; blank title 400s with a field error; cover-image
  upload stores the file and saves the URL; a non-image upload 400s and
  leaves the project unchanged; upload requires ROLE_ADMIN.
- **`AdminUserApiTest`** — an admin can create another admin, no hash in the
  response, unauthenticated and non-admin callers are refused, short
  passwords and duplicate emails rejected.
- **`AdminBootstrapRunnerTest`** — no env vars creates nothing; a password
  under 8 chars creates nothing; the first run creates exactly one ADMIN
  with a BCrypt hash and repeat runs are no-ops; it won't add a second admin
  under a different email once one exists.
- **`LoginResponseShapeTest`** — pins the login contract the dashboard is
  built on (token, ISO-8601 `expiresAt`, user without a password) and checks
  that a wrong password and an unknown email return the identical message.
- **`MigrationSchemaTest`** — runs the real Flyway migrations against H2 in
  MySQL-compatibility mode and asserts, from `INFORMATION_SCHEMA`, that
  `V3__create_projects.sql` produces exactly the columns the `Project`
  entity maps, with the right nullability and a `published` default of
  false. This is the check that catches a migration/entity mismatch, which
  on real MySQL would otherwise only surface at boot as a `ddl-auto=validate`
  failure.

### Not covered here

No MySQL or Docker daemon is available in the sandbox this was built in, so
the app has not been booted against real MySQL. `MigrationSchemaTest` covers
the schema/entity agreement that boot would check, but run the app against
a real MySQL once before deploying — see **Getting started** above.

## TODO / follow-ups

- **Rate limiting on login is in place** (10/min per IP via Bucket4j) but is
  per-instance and in-memory; move it to a shared store before running more
  than one instance.
- Password reset and email verification exist for `USER` accounts but were
  out of scope for the admin flow — an admin who loses their password
  currently needs another admin, or a fresh bootstrap against an empty
  `users` table.
- `GET /api/properties/**` is `permitAll` in the URL rules, which also
  matches the admin-only `GET /api/properties/admin`. That path is safe
  today only because `@PreAuthorize("hasRole('ADMIN')")` on the method
  catches it — exactly the defense-in-depth this codebase asks for, but the
  URL rule should be narrowed so it isn't the only thing standing between a
  refactor and an exposed endpoint. The new project routes don't have this
  overlap (`/api/projects` public, `/api/admin/projects` admin).
- The production frontend origin still needs adding to
  `CORS_ALLOWED_ORIGINS` when the domain is known.
