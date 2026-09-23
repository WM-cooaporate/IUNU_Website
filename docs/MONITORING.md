# Monitoring setup

The one-time setup that turns what the app records into something that wakes a person up.
Everything here is dashboard work in third-party accounts; nothing in it can be automated
from this repository.

**Free tiers and plan features change.** Every vendor named below was a reasonable choice when
this was written, not a promise about their current terms. Check each one's pricing page before
relying on it, and swap in an equivalent if something has moved behind a paywall.

## What the app already produces

| Signal | Where | Off by default? |
|---|---|---|
| Security events: failed logins, token replay, admin sign-ins from new addresses, rate-limit hits, probes | `SECURITY` logger (JSON under `prod`), and the `iunu.security.events{type}` counter | No, always on |
| Who changed what in the dashboard | `audit_log` table, shown in the dashboard's **Activity** tab | No, always on |
| Request correlation | `X-Request-Id` response header, and `requestId` on every log line | No, always on |
| Metrics (rates, latency, pool, heap, cache, the `iunu.*` counters) | `/actuator/prometheus` (ADMIN token), or pushed over OTLP | Push is off until `OTLP_METRICS_URL` is set |
| Backend errors with stack traces | Sentry | Off until `SENTRY_DSN` is set |

None of the optional pieces is needed for the app to run. With every variable below blank it
starts normally and makes no outbound calls.

## 1. Uptime (do this first)

An external monitor catches the one failure nothing inside the app can report: the app being down.

Use Better Stack, UptimeRobot or similar, with these three checks:

| Check | URL | Why |
|---|---|---|
| API alive | `https://<api-host>/actuator/health/liveness` | The process is up. Exempt from the edge secret, so it keeps working after runbook step 8b |
| Site up | `https://<frontend-host>/` | The static site is served |
| Real data | `https://<api-host>/api/properties?size=1`, **keyword** check for `"content"` | Catches the silent failure where the site renders demo data because the API is unreachable, which the first two checks miss |

Use an interval of 1 to 3 minutes and alert after 2 consecutive failures. That avoids paging on
one dropped packet, and Render's free instances sleep, so the first request after idle is slow.

## 2. Metrics and alerts

1. Create a Grafana Cloud stack (or any backend that accepts OTLP metrics and Prometheus-format
   alert rules).
2. In the stack's **OpenTelemetry** connection page, copy the OTLP endpoint and the
   `Authorization=Basic%20...` header line.
3. On Render, in the backend service's **Environment** tab, set:
   - `OTLP_METRICS_URL`: the endpoint with `/v1/metrics` appended, e.g.
     `https://otlp-gateway-<region>.grafana.net/otlp/v1/metrics`
   - `OTLP_METRICS_HEADERS`: the header line exactly as Grafana shows it
     (`Authorization=Basic%20<base64>`).

   Redeploy. Metrics are pushed every 60 seconds, so there is no new inbound endpoint and nothing
   to firewall.
4. Import `monitoring/alert-rules.yml` (**Alerting → Alert rules → Import**, or through your
   ruler). The metric names in it match what Grafana derives from the push.
5. Route alerts by severity:
   - **critical** and **high**: email plus a phone push (the Grafana OnCall app, or the uptime
     tool's app).
   - **warning**: email only.

`/actuator/prometheus` stays ADMIN-only. Scraping it is an alternative to the push, but it needs a
long-lived admin token stored in the scraper. Pushing avoids that.

## 3. Logs

Render keeps only a short window of logs. If your plan offers a **Log Stream**, point it at a log
platform (Better Stack Logs, Grafana Loki, Datadog or similar) and save these searches:

| Search | For |
|---|---|
| `logger_name:SECURITY` | Every security event |
| `logger_name:SECURITY AND level:WARN` | Only the ones a person should read: `REFRESH_REUSE_DETECTED`, `ADMIN_LOGIN_NEW_IP`, `ACCOUNT_LOCKED`, `EDGE_SECRET_REJECTED` |
| `requestId:<uuid>` | Everything one request did. The id is in the `X-Request-Id` response header |

Under the `prod` profile every line is one JSON object, so these are field searches, not
regular expressions. Noisy event types (`RATE_LIMITED`, `ACCESS_DENIED`, `TOKEN_INVALID`,
`LOGIN_FAILED`, `EDGE_SECRET_REJECTED`) are logged at most once per client IP per minute. The
metric counts every one, so use the metric for volume and the log for who.

No password, token, API key or request body is ever logged, and emails appear masked
(`m***@iunu-eg.com`).

## 4. Errors

Create a Sentry project (platform: Spring Boot) and set `SENTRY_DSN` on Render. Only backend
errors are sent. Credentials, cookies, request bodies, query strings, and the user's email and IP
are stripped from every event before it leaves (`SentryConfig`). Frontend error tracking is not
set up: it would need a Content-Security-Policy change in all three host configs.

## 5. Accounts (a checklist, not code)

An attacker who takes over a hosting account does not need a vulnerability in the app.

- [ ] MFA on **GitHub**, **Render**, **Vercel**, **Netlify**, **Cloudflare**, **Google Cloud**
      and the **domain registrar**. Prefer an authenticator app or a security key over SMS.
- [ ] Recovery codes stored somewhere that is not the laptop they protect.
- [ ] Google Cloud budget alert and Translation API quota: `DDOS_RUNBOOK.md` Part 1 step 9.
- [ ] Render spend limit: `DDOS_RUNBOOK.md` Part 1 step 10.
- [ ] Remove anyone who no longer needs access to these accounts.

## 6. When an alert fires

The first action for each alert. The runbook has the detail, which is not repeated here.

| Alert | First action |
|---|---|
| **RefreshTokenReuse** (critical) | The user's sessions are already revoked. Find their id and the client IP in `event=REFRESH_REUSE_DETECTED`. If it is an admin, have them change their password now and check the **Activity** tab for anything they did not do. Treat the admin's machine as the likely source (the token lives in its browser storage, see M7) |
| **AdminLoginNewIp** (high) | Ask the admin whether it was them. If not, change the password (signs out every session) and check the **Activity** tab |
| **AccountLocked** (high) | Someone is guessing, or locking the admin out (M9). Check the Cloudflare `/api/auth/` rule: `DDOS_RUNBOOK.md` Part 1 step 5 |
| **CredentialStuffing** (high) | `DDOS_RUNBOOK.md` Part 2 step 2, then tighten the `/api/auth/` rule (Part 1 step 5) |
| **OriginFound** (high) | `DDOS_RUNBOOK.md` Part 2 step 4 |
| **RateLimitFlood** (warning) | The defences are working. Watch capacity: `DDOS_RUNBOOK.md` Part 2 step 3 |
| **Probing** (warning) | Top IPs are in the `SECURITY` log. Challenge or block them at Cloudflare: `DDOS_RUNBOOK.md` Part 2 step 2 |
| **ServerErrors** (high) | Search the logs for `level:ERROR`, open one `requestId`, and check Sentry if it is set up |
| **SlowPublicList** (warning) | `DDOS_RUNBOOK.md` Part 2 step 3 (cache ratio and pool) |
| **DbPoolStarved** (high) | Do not raise `DB_POOL_MAX`. Cut traffic at the edge: `DDOS_RUNBOOK.md` Part 2 step 3 |
| **HeapHigh** (warning) | Check what traffic preceded it. Restart if it does not fall after a GC |
| **TranslationBudget** (warning) | Check the **Activity** tab and `RATE_LIMITED bucket=translation` for who is looping previews: `DDOS_RUNBOOK.md` Part 1 step 9 |
