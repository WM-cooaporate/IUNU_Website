# IUNU Development — website

The portfolio and lead-capture website for IUNU Development, with an admin
dashboard for managing the projects it shows. Built by WM Solutions.

Visitors browse published projects (in English or Arabic) and send contact,
quote, newsletter and career requests. Admins sign in at `/admin` to create,
edit, publish and photograph projects.

## Stack

| Part | Technology |
|---|---|
| Backend | Spring Boot 3.3.5, Java 21, Spring Security (JWT), Spring Data JPA, Flyway |
| Database | PostgreSQL (16 in Docker Compose and the Testcontainers tests; H2 in PostgreSQL mode for the fast suite) |
| Frontend | React 19, Vite, React Router, TanStack Query, axios |
| E2E tests | Cypress, against the production build with the API stubbed |

## Repository layout

```
.                     Frontend (Vite project: package.json, index.html, src/, public/)
├── backend/          Spring Boot API (own README with the full API reference)
├── cypress/          End-to-end smoke tests and their fixtures
├── docs/             Operations: DDoS runbook, monitoring setup, security audit
├── security/         Local attack lab and header checks (see security/README.md)
├── load-tests/       k6 load-test scripts
├── monitoring/       Alert rules
├── render.yaml       Render Blueprint: database, API and static site
├── vercel.json       Vercel config (not the live host; Netlify reads public/_headers)
└── ENV_VARS.md       Every environment variable, what it does, and its default
```

## Local setup

You need Java 21, Maven, Node.js (LTS) and either Docker or a local
PostgreSQL 16.

### Backend

```bash
cd backend
cp .env.example .env
openssl rand -base64 48            # paste into JWT_SECRET in .env; set DB_PASSWORD too
```

Then either run the API and PostgreSQL together with Docker Compose:

```bash
docker compose up --build
```

or run it against your own PostgreSQL:

```bash
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

The API listens on `http://localhost:8080` and every route is under `/api`.
Flyway creates the schema on first boot.

There is no default admin account. To create the first one, start the backend
once with `ADMIN_EMAIL` and `ADMIN_PASSWORD` (at least 8 characters) set, then
remove them. See `backend/README.md` → *Create the first admin user*.

### Frontend

From the repository root:

```bash
npm ci
cp .env.example .env.local         # VITE_API_URL=http://localhost:8080/api
npm run dev
```

The site runs on `http://localhost:5173`, which is the backend's default
`CORS_ALLOWED_ORIGINS`.

## Environment variables

[`ENV_VARS.md`](ENV_VARS.md) is the reference: every variable, which service
reads it, its default, and the order to set them in. The ones that matter
first:

- **Backend:** `JWT_SECRET`, the database connection (`DB_*`),
  `CORS_ALLOWED_ORIGINS`, `PUBLIC_API_URL`, `FRONTEND_URL`, `UPLOAD_DIR`, and
  `ADMIN_EMAIL` / `ADMIN_PASSWORD` for the one-time admin bootstrap.
  `APP_REGISTRATION_ENABLED` is optional and off by default.
- **Frontend:** `VITE_API_URL`, the backend's public URL. Vite inlines it at
  build time, so changing it needs a rebuild.

No real values belong in this repository. Every example in it is a dummy.

## Tests

```bash
# Backend: unit and integration tests
cd backend && mvn test
# mvn verify also runs the PostgreSQL tests through Testcontainers (needs Docker)

# Frontend: lint and production build
npm run lint
npm run build

# End-to-end: serve the production build, then run Cypress against it
npm run build && npm run preview     # http://localhost:4173, leave it running
npm run cy:run                       # in a second terminal
```

The E2E specs stub every API call with fixtures, so they need no backend.
They check that the site renders and navigates; the backend's behaviour is
covered by the JUnit suite.

GitHub Actions runs all of this on every pull request: `ci.yml` (backend
`mvn verify`, frontend lint/build/audit, gitleaks, security-header sync) and
`cypress.yml` (E2E).

## Deployment

**Backend and database:** Render, from the [`render.yaml`](render.yaml)
Blueprint, following `main`:

- `iunu-db`: managed PostgreSQL.
- `iunu-api`: the backend as a Docker service, with a persistent disk at
  `/var/data` for uploaded project images.

**Frontend:** Render, as the `iunu-web` static site in the same Blueprint
(<https://iunu-web.onrender.com>). It builds with `npm ci && npm run build`
and publishes `dist/`.

The repository also carries frontend configuration for Netlify
(`public/_headers`; the `iunuwebsite` site posts a deploy preview on every pull
request) and Vercel (`vercel.json`). Neither serves the live site.

Each host reads its security headers from its own file, so `render.yaml`,
`public/_headers` and `vercel.json` must stay in sync. CI checks this
(`security/check-headers-sync.mjs`). The build needs `VITE_API_URL`, and
`CYPRESS_INSTALL_BINARY=0` keeps it from downloading the Cypress test
browser; `render.yaml` sets the second for `iunu-web`.

See [`ENV_VARS.md`](ENV_VARS.md) for the deploy checklist. Operations docs:

- [`docs/DDOS_RUNBOOK.md`](docs/DDOS_RUNBOOK.md): putting the API behind
  Cloudflare and what to do under attack.
- [`docs/MONITORING.md`](docs/MONITORING.md): metrics, error tracking and
  alerting setup.
- [`docs/SECURITY_AUDIT.md`](docs/SECURITY_AUDIT.md): the security review and
  its findings.

## Security notes

Only admins have accounts. The first is created from the bootstrap variables,
further ones by an admin through `POST /api/admin/users`, and public
self-registration is switched off. Admin calls use short-lived JWT access
tokens (15 minutes by default) plus revocable refresh tokens. Logins, public
forms and admin calls are rate-limited per client, failed logins lock the
account for that address, and the API answers browsers only from the origins
listed in `CORS_ALLOWED_ORIGINS` (wildcards are refused at startup).
