# Attack lab

One command that runs what an attacker would run, against a throwaway copy of the production
setup on this machine, and reports what held and what did not.

```bash
bash security/run-lab.sh
```

It also runs every Sunday at 01:00 UTC in GitHub Actions (`.github/workflows/security-scan.yml`),
and on demand from the Actions tab.

## The rule: it never targets production

Every target is fixed to `127.0.0.1` and the lab's own compose services (`proxy`, `frontend`,
`backend`). `run-lab.sh` refuses anything else, and so does `load-tests/attack.js`. **There is no
override flag, on purpose.** Scanning or attacking the live site is indistinguishable from a real
attack: it fills the leads table, locks accounts, trips the rate limits real visitors depend on,
and may breach the hosting providers' acceptable-use policies.

## What it runs

| Step | Tool | Fails the run on |
|---|---|---|
| Secrets in the full git history | gitleaks (Docker image, `--redact`) | any finding not listed in `.gitleaksignore` |
| Vulnerable Java libraries | OWASP dependency-check (`mvn -Psecurity`) | any dependency with CVSS ≥ 7 |
| Vulnerable JS libraries | `npm audit --omit=dev --audit-level=high` | any high or critical |
| Smoke and existing defences | k6 `smoke.js`, `abuse.js` (`BEHIND_PROXY=yes`) | any failed check |
| Passive scan of the site | ZAP baseline against the frontend | any High |
| Active scan of the API | ZAP API scan of `/v3/api-docs`, anonymous and then as the lab admin | any High |
| Attacker techniques | k6 `attack.js` (see its header) | any failed check not marked report-only |
| Oversized chunked body | `curl` | never (report only, M2) |
| Log hygiene | `grep` over the backend's own log | any token, the admin password, or a `Bearer` string |

Afterwards it reads the `iunu.*` counters back from `/actuator/metrics`, which shows the detection
side saw the attacks. Then it deletes the whole stack with `docker compose down -v`.

## The stack

`docker-compose.lab.yml` builds the real backend image from `backend/Dockerfile` and runs it with
`SPRING_PROFILES_ACTIVE=prod`, so it is the production configuration that gets attacked:

```
ZAP / k6 --> frontend (serve-dist.mjs: dist/ + vercel.json's headers, /api forwarded)
         --> proxy    (nginx: appends X-Forwarded-For, injects the edge secret, like Cloudflare)
                --> backend (prod profile) --> postgres (tmpfs)
```

Every secret (database password, JWT key, edge secret, lab admin password) is generated per run
with `openssl rand` and only exists in that shell's environment. Host ports bind to `127.0.0.1`.
Differences from production, and why:

- `SWAGGER_ENABLED=true`, because the ZAP API scan needs the OpenAPI document. This is the only
  environment where it is on.
- `JWT_ACCESS_TOKEN_EXPIRATION_MS=7200000`, so the authenticated scan does not expire mid-run.
- `EDGE_SHARED_SECRET` is **on**, with the proxy injecting it, so reaching the backend directly
  is a real "origin found" attack. In production this stays off until `DDOS_RUNBOOK.md` step 8b.
- The proxy streams request bodies rather than buffering them. nginx's default would turn a
  chunked upload into one with a `Content-Length` and hide the M2 residual.

## Reading the reports

Everything lands in `security/reports/<UTC timestamp>/` (gitignored; uploaded as a 30-day artifact
in CI):

| File | What |
|---|---|
| `summary.txt` | The table printed at the end: one line per tool, the report-only numbers, and the security-event counters |
| `gitleaks.json` | Findings, with secrets redacted |
| `dependency-check-report.html` / `.json` | Java CVEs |
| `npm-audit.json` | JS advisories |
| `zap-*.html` / `.json` | ZAP findings, one pair per scan |
| `k6-*.log` / `.json` | k6 output and summaries; `attack-summary.json` holds every check plus the report-only numbers |
| `detection.txt` | `iunu.security.events` by type, and the other `iunu.*` counters, after the run |
| `prometheus.txt` | A full `/actuator/prometheus` scrape, used to check the names in `monitoring/alert-rules.yml` |
| `backend.log` | The app's own JSON log for the run |

"Report only" numbers measure findings the owner has accepted (M2 chunked bodies, M8 reset timing,
M9 account lockout). They are printed, never gating.

## Tuning

- `NVD_API_KEY`: makes dependency-check's first run minutes instead of hours.
- `LAB_SKIP_DEPENDENCY_CHECK=1`: skip it entirely, for a quick run.
- `LAB_API_PORT`, `LAB_WEB_PORT`: host ports (default `8081`, `8082`).
- `LAB_COMPOSE_OVERRIDE`: an extra compose file, for machines where the image builds need a proxy
  or a registry mirror.

## False positives

`zap-rules.tsv` lists ZAP rules that are ignored or downgraded, **each with the reason**. Never add
a rule because it is noisy: fix it, or downgrade it with a reason that names what covers the risk
instead. `.gitleaksignore` works the same way for gitleaks.
