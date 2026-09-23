# Security audit — IUNU website

**Date:** 2026-09-17
**Scope:** `backend/` (Spring Boot 3.3.5, Java 21, PostgreSQL) and `src/` (React 19 + Vite, deployed to Vercel, Netlify and Render — see M12)
**Method:** manual review of every controller, service, filter and configuration file; dependency scans; a running instance probed with `curl` and the k6 abuse suite; the production frontend bundle loaded in a headless browser under the proposed CSP.

Findings marked **Fixed in this PR** have a test. Findings marked **Recommended** are for you to decide on — each one either changes product behaviour, needs a decision that is not mine, or lives outside the code.

---

## Summary

| Severity | Fixed in this PR | Recommended |
|---|---|---|
| Critical | 0 | 0 |
| High | 3 | 1 |
| Medium | 7 | 5 |
| Low | 4 | 3 |
| Info | — | 4 |

**Nothing Critical was found.** The codebase was already in good shape before this audit: parameterised queries throughout, BCrypt at strength 12, refresh tokens stored hashed and rotated, magic-byte validation on uploads, a CORS fail-fast guard, and stack traces already suppressed. The findings below are the gaps around that, and most of them are about *availability* — the ways one request can be made to cost what a thousand should.

---

## Automated scans

**`npm audit --omit=dev`** — ran. **0 vulnerabilities** across the production dependency tree (axios 1.19, react 19.2, react-router-dom 7.18, framer-motion 12.43, jwt-decode 4.0).

**OWASP `dependency-check`** — **could not run in this environment.** The plugin is now in `backend/pom.xml` behind a `security` Maven profile, but its NVD and CISA feed downloads are blocked by the sandbox's egress proxy (`HTTP 403` tunnelling to `services.nvd.nist.gov` and `cisa.gov`). This is an environment limitation, not a finding — **run it yourself**:

```bash
cd backend
# An NVD API key makes the first run minutes instead of hours.
# Get one free at https://nvd.nist.gov/developers/request-an-api-key
export NVD_API_KEY=...
mvn -Psecurity org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7
# Report: backend/target/dependency-check-report.html
```

**ZAP dynamic scan** — **not run.** There is no staging URL, and a scan of a local instance with Swagger force-enabled would report on a configuration that does not exist in production. Run it against staging once one exists:

```bash
docker run --rm -t ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py -t "$STAGING_FRONTEND_URL" -r zap-frontend.html
# The backend scan needs SWAGGER_ENABLED=true on that environment for the duration.
docker run --rm -t ghcr.io/zaproxy/zaproxy:stable \
  zap-api-scan.py -t "$STAGING_BACKEND_URL/v3/api-docs" -f openapi -r zap-api.html
```

**Secrets in git history** — scanned `git log --all -p` for API-key, secret, password and JWT patterns. **Nothing found.** The only matches are the deliberate test constant `test-only-secret-that-is-at-least-32-bytes-long-for-hmac-sha`, which is a test fixture and not a credential. `gitleaks` was not available in this environment; the pattern scan is a weaker substitute, so run it when you can:

```bash
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest detect --source /repo -v
```

---

## High

### H1. Password reset links written to the log, and mail is off by default in production

**Severity:** High
**Location:** `backend/src/main/java/com/iunu/realestate/service/impl/EmailServiceImpl.java:32`
**Status:** **Fixed in this PR**

**Evidence.** When `app.mail.enabled` is false, the service logged the full reset URL:

```java
log.info("app.mail.enabled=false - skipping real email send. Reset link for {}: {}", toEmail, resetLink);
```

`app.mail.enabled` defaults to `${MAIL_ENABLED:false}` in `application.yml`, and `application-prod.yml` does **not** override it.

**Impact.** Account takeover for anyone who can read the logs. A deployment that never configured SMTP — which is the default state — writes a working, 30-minute password reset token to its log stream every time anyone submits the forgot-password form. An attacker does not need log access to *cause* this; they only need it to *collect*. Render's dashboard, any log drain, and any exported log file are all sufficient. Because `forgotPassword` deliberately returns the same response whether or not the account exists, nothing about the flow looks unusual.

**Fix.** The link is now printed only when the `prod` profile is not active, which keeps local development workable. In production the same branch logs a WARN saying password reset is not functioning and that SMTP needs configuring — which is the other half of the problem, and was previously silent.

### H2. Forwarding headers were trusted from any peer, defeating every per-IP limit

**Severity:** High
**Location:** `backend/src/main/java/com/iunu/realestate/security/ClientIpResolver.java`
**Status:** **Fixed in this PR**

**Evidence.** Found empirically, not by reading: 400 requests to `/api/properties` with a rotating `X-Forwarded-For: 1.2.3.N` produced **no 429 at all** against a server in `x-forwarded-for` mode. The k6 check is in `load-tests/abuse.js`.

**Impact.** "The last `X-Forwarded-For` entry is the real client" holds only if a proxy appended it. A request that reaches the process directly carries whatever the caller typed, end to end — so the caller picks their own rate-limit bucket and every per-IP limit becomes decoration. This is not hypothetical: the platform URL (`*.onrender.com`, `*.up.railway.app`) stays directly reachable whatever is configured at the edge, and it is discoverable from DNS history, old commits and certificate transparency logs.

**Fix.** `app.client-ip.trusted-proxies` (`TRUSTED_PROXIES`) takes addresses or CIDR blocks. Forwarding headers are honoured only from a peer inside one; anything else falls back to the socket address, which is over-restrictive rather than unrestricted. Left blank the old behaviour is preserved and startup logs a WARN naming the exact consequence. **Set this on Render** — see `ENV_VARS.md`.

### H3. The rate limiter's bucket store was unbounded

**Severity:** High
**Location:** `backend/src/main/java/com/iunu/realestate/security/RateLimitingFilter.java` (previously `ConcurrentHashMap`)
**Status:** **Fixed in this PR**

**Evidence.** Buckets were held in a `ConcurrentHashMap<String, Bucket>` keyed on client address, with no eviction and no size cap.

**Impact.** Memory exhaustion. Requests from many addresses — a botnet, or forged `X-Forwarded-For` values under H2 — grow the map until the JVM dies. The defence becomes the attack: an attacker who cannot get past the limiter can instead use it to consume all the heap, and the instance on Render's free plan has very little.

**Fix.** A Caffeine cache, `maximumSize` 100,000 (configurable) with `expireAfterAccess` of one hour. The bound is asserted by a test that injects a 16-entry cap.

### H4. The origin is reachable directly, bypassing every edge protection

**Severity:** High
**Location:** Deployment topology, not code
**Status:** **Recommended** — code support shipped, the configuration is yours

**Evidence.** The backend answers on its platform URL regardless of any Cloudflare configuration on a custom domain.

**Impact.** This is the finding that determines the value of all the others at the edge. Cloudflare's WAF, Bot Fight Mode, rate-limiting rule and cache rule all sit in front of `api.<domain>`; none of them sit in front of `iunu-api.onrender.com`. An attacker who finds the platform URL — and it is in DNS history, in the git history of `VITE_API_URL`, in CT logs — sends traffic straight to the origin. It also undermines H2's fix and the `cloudflare` client-IP mode, both of which assume traffic arrives through the edge.

**Fix shipped:** `EdgeSecretFilter` refuses any request without a shared-secret header that a Cloudflare Transform Rule injects. Disabled unless `EDGE_SHARED_SECRET` is set, with `/actuator/health` exempt so deploys still pass their health check.
**What you must do:** steps 1, 7 and 8 of `docs/DDOS_RUNBOOK.md`, in that order. Enabling the secret before the Transform Rule exists takes the API offline.

---

## Medium

### M1. No cap on page size — one request could select the whole table

**Severity:** Medium (availability)
**Location:** `application.yml`; every controller binding `Pageable`
**Status:** **Fixed in this PR**

`GET /api/properties?size=100000` was honoured. One such request costs more than a thousand normal ones, in database time, heap and bandwidth, and looks like an ordinary request in an access log. Capped at 50 via `spring.data.web.pageable.max-page-size`, asserted by a test and by `abuse.js`.

### M2. No cap on request body size

**Severity:** Medium (availability)
**Location:** `backend/src/main/java/com/iunu/realestate/security/ContentLengthLimitFilter.java` (new)
**Status:** **Fixed in this PR**

The `@Size` constraints on the request DTOs run *after* Jackson has parsed the entire body into objects, so a 50MB JSON document was fully read, decoded and allocated before the first constraint was checked. The new filter rejects a non-multipart body over 1MB on `Content-Length`, before the stream is read.

**Residual gap:** a chunked request sends no `Content-Length`, so this check cannot see its size. Tomcat's `max-swallow-size` and `max-http-form-post-size` bound the common cases and Jackson 2.17's `StreamReadConstraints` (nesting depth, string and number length — verified ≥ 2.15, Boot 3.3.5 ships 2.17.2) bound what a parsed document can cost. Fully closing it needs a body-size cap at the edge — a Cloudflare WAF rule.

### M3. Actuator was `denyAll`, so operators had no metrics either

**Severity:** Medium
**Location:** `backend/src/main/java/com/iunu/realestate/config/SecurityConfig.java`
**Status:** **Fixed in this PR**

The previous rule refused everything under `/actuator` except health — safe, but it meant nobody could read metrics during an incident, and with no Micrometer registry on the classpath none were being collected anyway. Now the three health probes are anonymous (`show-details: never`, so an anonymous caller learns UP or DOWN and nothing about the database or which component is failing), and everything else requires ADMIN. Stated explicitly rather than left to `anyRequest().authenticated()`, so widening `management.endpoints.web.exposure.include` cannot quietly publish a new endpoint to any logged-in user. A test asserts `/actuator/env` and `/actuator/heapdump` are unreachable even for an admin.

### M4. Swagger and the OpenAPI document were public

**Severity:** Medium
**Location:** `SecurityConfig` (`/v3/api-docs/**`, `/swagger-ui/**` were `permitAll`)
**Status:** **Fixed in this PR**

An anonymous visitor could read the full shape of every endpoint, including every admin route and every request DTO. That is not a vulnerability by itself, but it is the reconnaissance step that makes everything else cheaper.

`springdoc` now defaults to **off** and is opt-in via `SWAGGER_ENABLED=true`. Deliberately a safe default rather than a production override: a deploy that forgets `SPRING_PROFILES_ACTIVE=prod` must not publish the API surface. The `permitAll` rules remain, so turning it on for a ZAP scan needs no code change — but with the endpoints disabled they route nowhere.

### M5. Bad `?sort=` and unmapped paths returned 500 with a logged stack trace

**Severity:** Medium
**Location:** `backend/src/main/java/com/iunu/realestate/exception/GlobalExceptionHandler.java`
**Status:** **Fixed in this PR**

`?sort=password` raised `PropertyReferenceException` from inside Spring Data query creation and fell through to the catch-all: a 500, plus an ERROR-level stack trace. Two problems. A 500 reads as "you found something real", so varying the field name and watching the status change is a free map of the entity's shape. And a scanner walking a wordlist fills the logs on the way through, which is a small denial of service against whoever has to read them. Both `PropertyReferenceException` and `NoResourceFoundException`/`NoHandlerFoundException` are now mapped to 400/404, with a message that names no field — echoing the rejected property back would keep the oracle open.

`?type=NOPE` was already handled correctly (400 via `MethodArgumentTypeMismatchException`).

### M6. `allowCredentials: true` on a token-authenticated API

**Severity:** Medium
**Location:** `SecurityConfig.corsConfigurationSource()`
**Status:** **Fixed in this PR**

This API has no cookies and no session — the browser sends an `Authorization` header, which is not a credential in the CORS sense. Allowing credentials granted a capability nothing needs, and it is the precondition for CSRF against an API whose CSRF protection is deliberately disabled (correctly, for a bearer-token API). Now `false`. `Retry-After` was added to the exposed headers so the frontend can read it off a 429.

### M7. Access tokens in `localStorage`

**Severity:** Medium
**Location:** `src/services/apiClient.js:69-84`
**Status:** **Recommended** — the mitigation is shipped, the structural fix is not

Any successful XSS on the frontend reads the admin's access and refresh tokens directly out of `localStorage`. The strict CSP added to `vercel.json` in this PR (`script-src 'self'`, no `unsafe-inline`, no `unsafe-eval`) is the practical mitigation and materially reduces the chance of XSS in the first place — and the codebase helps, with no `dangerouslySetInnerHTML`, no `innerHTML` and no `eval` anywhere in `src/`.

The structural fix is httpOnly, `SameSite=Strict`, `Secure` cookies for the refresh token, with the short-lived access token held in memory only. That is a coordinated change across the backend auth endpoints, the axios client and the admin route guard, and it reintroduces CSRF (so it needs CSRF tokens back). It is worth doing, and it is too large to fold into this PR.

### M8. `forgotPassword` leaks account existence through response timing

**Severity:** Medium
**Location:** `backend/src/main/java/com/iunu/realestate/service/impl/AuthServiceImpl.java:159-179`
**Status:** **Recommended**

The response *body* is correctly identical either way. The *timing* is not: for an existing account the request invalidates prior tokens, generates a token, writes a row, and calls `EmailService`; for a non-existent one it does nothing and returns immediately. With SMTP enabled the difference is the full SMTP round trip — hundreds of milliseconds, measurable over a single request, no statistics needed.

**Impact:** enumeration of registered email addresses. Low direct value here (the admin's address is likely `info@iunu-eg.com` and guessable anyway), but it feeds credential stuffing and the lockout issue in M9.

**Recommended fix:** move the token generation and send to an `@Async` task so the endpoint returns before any of it happens. That makes the timing identical *and* stops a slow SMTP server from holding a request thread — which is also a small availability win. Roughly 15 lines plus a `TaskExecutor`. Not done here because it changes the delivery path and deserves its own test.

### M9. Account lockout is a denial-of-service vector against the admin

**Severity:** Medium
**Location:** `AuthServiceImpl.registerFailedAttempt`; `app.security.max-failed-attempts: 5`, `lock-duration-minutes: 15`
**Status:** **Report only, by your instruction — not changed**

Five wrong passwords lock any account for 15 minutes. The lock is per *account*, so anyone who knows the admin's email address can keep the client permanently locked out of their own dashboard by sending six wrong passwords every fifteen minutes — roughly 24 requests an hour, which is far below the 10/minute login limit and indistinguishable from a forgetful user.

The admin email is not secret. It is likely `info@iunu-eg.com` or similar, and M8 gives a way to confirm it.

Three options, in the order I would consider them:

- **(a) Count failures per `(account, IP)` rather than per account.** The attacker locks only themselves out. Cheapest change and it preserves the protection against online guessing from one source. Weakness: an attacker with many addresses can still lock the account — but that is a much higher bar, and the existing per-IP login limit already raises it.
- **(b) Replace the hard lock with progressive delay, or a CAPTCHA after N failures.** Strictly better security — there is no state an attacker can put the account into that denies the real owner access — but it needs a CAPTCHA provider (a third party in the login path, and a privacy consideration) or a delay mechanism that holds request threads, which is its own availability problem unless done asynchronously.
- **(c) Keep the lock, but exempt addresses that have previously authenticated successfully to this account.** The real admin is never locked out from their usual location. Needs a small table of known-good addresses per user, and degrades when the admin travels or their address changes.

**My recommendation: (a).** It removes the denial of service outright, is about twenty lines plus a migration for the per-IP counter, and keeps the current behaviour for the case the lock was actually designed for. But this is your call, as you said.

### M10. Uploaded files live on an ephemeral filesystem

**Severity:** Medium (availability / data loss)
**Location:** `app.file-storage.location`; `application-prod.yml` already documents this
**Status:** **Recommended** — pre-existing, already known, restated because it is still true

On Render's free plan there is no persistent disk. Every deploy, and every wake from a spin-down, destroys the upload directory while the database rows keep pointing at the vanished URLs — so the site renders with broken images and the only recovery is re-uploading by hand.

**Fix:** attach a Render Disk (paid) and point `UPLOAD_DIR` at its mount path, or move to object storage. The `ImageStorage` interface exists precisely so that an S3 or Cloudinary implementation is a new class and a property change, with no call-site edits.

### M11. `PageImpl` is serialised directly

**Severity:** Medium (stability, not security)
**Location:** every controller returning `Page<...>`
**Status:** **Recommended**

Spring Data logs a warning on every such response: the JSON shape of a serialised `PageImpl` is not a stable contract and can change between Spring versions. The frontend reads `content`, `totalElements` and `size` from it. A Spring Data upgrade could silently reshape those and break the listing pages.

**Fix:** `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`, plus whatever frontend adjustment the new shape needs. Not done here because it changes the public API response shape, which is a coordinated frontend change and outside this PR's scope.

### M12. The site deploys to three hosts, and security headers were configured for one

**Severity:** Medium
**Location:** `vercel.json`, `public/_headers` (new), `render.yaml`
**Status:** **Fixed in this PR**

**Evidence.** This repository is deployed by **three** static hosts, each of which reads a different configuration file and ignores the others:

| Host | Reads | Had headers before this finding |
|---|---|---|
| Vercel (`iunu-website`) | `vercel.json` | Yes — added earlier in this PR |
| Netlify (`iunuwebsite`) | `_headers` at the publish root | **No file existed at all** |
| Render (`iunu-web`) | the `headers:` block in `render.yaml` | **No block existed** |

The Netlify deployment surfaced only when its bot commented on this PR; nothing in the repository referenced it.

**Impact.** Every header in M7's mitigation — the strict CSP that is the practical defence for tokens held in `localStorage`, plus `X-Frame-Options`, HSTS and the rest — applied to exactly one of three deployments. Which host serves the production domain is not something the code can know, so a CSP present in `vercel.json` alone is not a CSP the client is protected by. Worse, it reads as done: the audit would have claimed the headers were shipped, and the browser verification would have passed, while two live deployments served nothing.

**Fix.** `public/_headers` (Vite copies `public/` into `dist/` verbatim, which is where Netlify looks) and a `headers:` block in `render.yaml`, both carrying the identical header set. A check confirms all three files declare the same six headers with byte-identical CSP values, and the browser verification was re-run reading the shipped `dist/_headers` rather than a copy of it — zero violations on every route.

**Standing risk:** three files now have to stay in sync by hand, and nothing enforces it at build time. Each carries a comment saying so. If a fourth host ever appears, or the backend moves to a custom domain, all three need the same edit — the `connect-src` directive names the API origin, and getting it wrong blocks every API call with no server-side error to find.

---

## Low

### L1. `HEAD` returned 401 on every public path

**Severity:** Low (availability)
**Location:** `SecurityConfig` — rules were written for `HttpMethod.GET` only
**Status:** **Fixed in this PR**

Spring Security's method matcher is exact, so a rule for GET refuses HEAD of the same URL. Nothing in this app issues a HEAD, which is why it went unnoticed — but CDN cache validation, link previews and uptime monitors all do, and Cloudflare's cache rule (runbook step 6) would have behaved unpredictably. HEAD now travels with GET everywhere; a test asserts it still cannot reach an admin path.

### L2. Immutable images served with `no-store`

**Severity:** Low (availability / performance)
**Location:** `WebConfig.addResourceHandlers`
**Status:** **Fixed in this PR**

Spring Security's default `no-store` applied to `/uploads/**`. Uploaded files are content-addressed — the filename is the SHA-256 of the bytes — so a URL can never return different content, and `no-store` meant every visitor re-downloaded every image on every page view. On a listing page that is most of the bytes, and under load it is origin bandwidth spent re-sending files the browser already holds, which is exactly the resource a volumetric attack targets. Now `max-age=31536000, public, immutable`.

### L3. Log injection through unvalidated free text

**Severity:** Low (integrity of the audit trail)
**Location:** `CareerServiceImpl:35` (`position`)
**Status:** **Fixed in this PR**

`position` is only length-limited, so it can contain newlines. In a line-oriented log that means a public career applicant can write their own log lines — for example a convincing fake "Admin login succeeded" entry. Not a disclosure bug; an integrity one, and it matters most exactly when you are reading the logs after an incident. Added `LogSanitizer.forLog()` and applied it.

Other logged user input (emails, phone numbers) is already protected by `@Email` and the phone `@Pattern`, both of which reject newlines.

### L4. Email header injection surface in the career application subject

**Severity:** Low
**Location:** `CareerServiceImpl:44`
**Status:** **Fixed in this PR**

`position` and `fullName` went unfiltered into `helper.setSubject(...)`. A mail header is terminated by CRLF, so an unfiltered newline in a subject is the classic header-injection primitive — append a `Bcc:` and use the company's mail server to send elsewhere. JavaMail's RFC-2047 encoding makes this hard to actually exploit, but not relying on that is cheaper than being certain of it. Both values are now stripped of line breaks.

### L5. No Subresource Integrity on the Google Fonts stylesheet

**Severity:** Low
**Location:** `src/styles/global.css:1`
**Status:** **Recommended**

`@import url("https://fonts.googleapis.com/css2?family=Poppins...")` trusts Google's CDN to serve benign CSS. The CSP limits the damage (`style-src` allows only `fonts.googleapis.com`, and CSS cannot execute script in any current browser), so the realistic worst case is defacement rather than compromise. SRI cannot be applied to an `@import`, so the fix is to self-host the Poppins woff2 files — which also removes a third-party request from every page load and a DNS lookup from the critical path.

### L6. BCrypt strength 12 makes `/api/auth/login` a CPU cost multiplier

**Severity:** Low (availability)
**Location:** `SecurityConfig.passwordEncoder()`
**Status:** **Recommended** — informational; no change made

Strength 12 is a good choice and I am **not** recommending lowering it. Recording it because it is the relevant number for capacity planning: each verification is roughly 250–400ms of CPU, and on Render's free 0.1-CPU instance that is the single most expensive operation the API performs. The 10/minute per-IP login limit is what keeps this bounded, which is why H2 (forwarding headers trusted from any peer) mattered so much — without it the login limit did not apply and this became a cheap CPU-exhaustion vector.

If the instance is ever CPU-bound under normal load, the answer is a larger instance, not a weaker hash.

### L7. `DELETE /api/properties/{id}` has no confirmation semantics

**Severity:** Low
**Status:** **Recommended** — product decision, not a vulnerability

A compromised or careless admin token deletes properties irreversibly, and the orphan-image cleanup deletes the files too. Consider a soft delete (`deleted_at`) so a mistake is recoverable. Noting it because the image cleanup made deletion genuinely destructive in a way it was not before.

---

## Manual checklist

### Authentication and sessions

| Check | Result |
|---|---|
| JWT secret from env, ≥ 256 bits, startup fails if missing or short | **Pass** — `JwtService` constructor throws below 32 bytes, with the `openssl` command in the message |
| Algorithm pinned; `alg: none` rejected | **Pass** — `Keys.hmacShaKeyFor` + `verifyWith`; `JwtValidationTest` covers `alg: none`, a forged signature and a wrong issuer |
| Access token lifetime 15 minutes | **Pass** — `JWT_ACCESS_TOKEN_EXPIRATION_MS:900000` |
| Refresh tokens stored hashed, rotated, revoked on reset and change | **Pass** — SHA-256 via `TokenHasher`; `refresh()` revokes the old row before issuing; `resetPassword` and `changePassword` both call `revokeAllForUser` |
| Registration can never produce ADMIN, including via extra JSON fields | **Pass** — `Role.USER` is hardcoded and `RegisterRequest` has no role field. Now tested with `"role":"ADMIN"`, `"authorities":["ROLE_ADMIN"]` and `"accountLocked":false` in the body |
| `forgotPassword` same response for existing and missing accounts | **Pass** on body, **M8** on timing |
| Admin bootstrap is inert once an admin exists | **Pass** — gated on `existsByRole(ADMIN)`, not on the email, so re-running with a different address does nothing |
| BCrypt strength | **Pass** — 12; see L6 |

### Authorization

| Check | Result |
|---|---|
| Every endpoint has a test proving who cannot reach it | **Pass** — `AdminEndpointAuthorizationTest` sweeps 23 endpoints × (anonymous → 401, USER → 403); the four actuator paths were added in this PR |
| `/api/properties/admin/**` matched before the public GET rule | **Pass** — ordered correctly, commented, and covered by the sweep. This is the single easiest place in the app to publish every draft, and it is right |
| IDOR on `/{id}` routes | **Pass** — there are no user-owned resources addressable by id. `/api/admin/users` is ADMIN-only; property and project ids are public data by design |
| Method security agrees with URL security | **Pass** — `@PreAuthorize("hasRole('ADMIN')")` on every admin controller method, duplicating the URL rule rather than relying on it |

### Injection and input

| Check | Result |
|---|---|
| Every `@Query` parameterised, no concatenation | **Pass** — four `@Query` methods, all `:named` parameters. The string concatenation in `PropertyRepository.findIdsMissingArabic` is Java source assembling a constant, with no user input anywhere in it |
| Native queries | **Pass** — none (`nativeQuery = true` appears nowhere) |
| `sort` on arbitrary property paths | **M5**, fixed |
| Invalid enum values → 400, not 500 | **Pass** |
| Log injection | **L3**, fixed |

### File upload

| Check | Result |
|---|---|
| Magic-byte validation, not `Content-Type` | **Pass** — verified live: a file declared `image/png` whose bytes are SVG is rejected 400 |
| SVG rejected | **Pass** — verified live: allowlist is JPEG/PNG/WebP only, so SVG never reaches disk. This matters because SVG carries script and would be stored XSS served from the API's own origin |
| Server-generated filenames, no path traversal | **Pass** — SHA-256 of the content plus an extension derived from the allowlisted type, never from the uploaded filename; `normalize()` + `startsWith(root)` belt-and-braces |
| Served with `nosniff` and the right `Content-Type` | **Pass** — verified live: `X-Content-Type-Options: nosniff`, `Content-Type: image/png` |
| Attachment filenames sanitised | **Pass** — `CareerServiceImpl.safeAttachmentName` strips directories and non-filename characters, with a fallback for `..`-only input |
| Ephemeral filesystem | **M10** |

### Error handling and exposure

| Check | Result |
|---|---|
| `include-stacktrace`, `include-message`, `include-binding-errors` all `never` | **Pass** — already set before this PR |
| H2 console disabled outside tests | **Pass** — H2 is `<scope>test</scope>`; the console is never on the production classpath |
| Swagger disabled in production | **M4**, fixed |
| Actuator locked down | **M3**, fixed |
| No passwords, tokens or API keys logged | **H1** (fixed) was the exception. The Google API key is never logged — `GoogleTranslationService` deliberately logs `exception.getMessage()` only, with a comment saying why |
| User responses never contain a password | **Pass** — `UserResponse` has no password field; now asserted against the raw response body of `/api/admin/users`, including a `$2a$` BCrypt-prefix check |

### Transport and headers

Verified live against a running instance:

| Header | Value |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` (secure requests only — correct: a plaintext response must not be trusted to pin HTTPS) |
| `Referrer-Policy` | `no-referrer` |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |
| `X-XSS-Protection` | `0` — correct. The legacy filter it disables introduced vulnerabilities of its own |

**One deliberate deviation from the brief:** `Referrer-Policy` stays `no-referrer` rather than `strict-origin-when-cross-origin`. That policy exists to keep analytics and navigation working across sites; a JSON API has no navigations to preserve, so there is no reason to leak the origin at all. The frontend's `vercel.json` uses `strict-origin-when-cross-origin`, which is right for a browsable site.

### CORS

| Check | Result |
|---|---|
| Explicit origins, no wildcard | **Pass** — startup fails on an empty list or any `*`, with a message explaining the consequence |
| `allowCredentials` false | **M6**, fixed |

### Frontend

| Check | Result |
|---|---|
| No `dangerouslySetInnerHTML`, `innerHTML` or `eval` | **Pass** — zero occurrences in `src/` |
| No secrets in `VITE_*` | **Pass** — `VITE_API_URL` is the only one, and it is a public URL. `grep -rn "GOOGLE_TRANSLATE\|JWT_SECRET\|EDGE_SHARED_SECRET" src/` returns nothing |
| Security headers on the static site | **Fixed in this PR** — see M12; added to all three host configs |
| CSP verified, not just written | **Pass** — see below |

**CSP verification.** A wrong CSP is a white screen, so it was tested rather than reasoned about. The production bundle was built with `VITE_API_URL=https://iunu-api.onrender.com/api`, served by a local server replaying the exact headers from `vercel.json`, and loaded in headless Chromium. Every route — `/home`, `/project`, `/about`, `/contact`, `/careers`, `/admin`, and a 404 — rendered with **zero CSP violations**. The script is in this PR's history; re-run it after any change to what the app loads.

Each directive is derived from something the app actually does:

| Directive | Why |
|---|---|
| `script-src 'self'` | Vite emits a single module bundle; there are no inline scripts. No `unsafe-inline`, no `unsafe-eval` — this is what makes the CSP worth having against M7 |
| `style-src 'self' 'unsafe-inline' https://fonts.googleapis.com` | `global.css` `@import`s Google Fonts; framer-motion writes inline styles, which needs `unsafe-inline`. (Unavoidable with this animation library, and far less dangerous for styles than for scripts) |
| `font-src 'self' https://fonts.gstatic.com` | Where the Google Fonts stylesheet fetches the woff2 files from |
| `img-src 'self' data: https:` | Property images come from the backend origin, and admins can paste externally hosted URLs. Broad by necessity |
| `connect-src 'self' https://iunu-api.onrender.com` | The only host the app calls. **Update this if the backend moves to a custom domain** — the API will silently stop working otherwise |
| `frame-ancestors 'none'`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'` | Clickjacking, plugin content, base-tag hijacking and form exfiltration |

---

## What this audit does not cover

- **Volumetric DDoS.** Nothing in this document, and nothing in this PR, makes the site resistant to one. By the time traffic reaches Spring Boot the host's bandwidth and CPU are already being consumed. See `docs/DDOS_RUNBOOK.md`.
- **The hosting accounts themselves.** Render, Vercel, Cloudflare and Google Cloud console access, and whether those accounts have MFA, is outside the repository and is worth checking.
- **The database at rest.** Backups, encryption and who holds the credentials are Render configuration.
- **Dependency CVEs.** `npm audit` is clean; the Java side could not be scanned here (see above). Run it.
- **Dynamic scanning.** No staging environment existed to scan.

---

## Addendum: attack lab, detection and monitoring (2026-09-23)

The original audit hardened the code but could not run the scanners, and nothing watched the
app afterwards. This addendum covers the gaps that remained, the first run of the attack lab
(`security/run-lab.sh`) against a local stack built with the production configuration, and what
that run found. Nothing in this section was run against production.

### Summary

| ID | Finding | Severity | Status |
|---|---|---|---|
| N1 | Refresh-token rotation could be raced into two valid sessions | High | **Fixed** (#12) |
| N2 | A replayed refresh token raised nothing | High | **Fixed** (#12) |
| N3 | No record of what an admin did | Medium | **Fixed** (#12) |
| N4 | No CI: none of the security tests gated a merge | Medium | **Fixed** (#13) |
| N5 | Account lockout never persisted | High | **Open**, needs a decision (see below) |
| N6 | Every per-IP rate limit could be bypassed in production with one header | High | **Fixed** (#13), found by the lab |
| N7 | With the edge secret on, the liveness and readiness probes returned 403 | Low | **Fixed** (#13), found by the lab |

Also fixed along the way:
- **Testcontainers skipped silently.** Testcontainers 1.19.8 is refused by Docker Engine 29, so `MigrationSchemaTest` was reported as *skipped* rather than failed. Bumped to 1.21.4, and CI now fails if the PostgreSQL tests skip.
- **Nightly cleanup disabled reuse detection.** The cleanup deleted revoked refresh tokens, which would have turned off N2's reuse detection every night. It now deletes expired tokens only.

### N1. Refresh-token rotation was not atomic (High, fixed)

`refresh()` read the token, checked `revoked`, then wrote it. Under READ COMMITTED, two requests
carrying one token both passed the check and both received a new pair. That is how a thief and
the real admin both keep a session alive indefinitely. This was reproduced with the old code:
both requests returned 200, on H2 and on PostgreSQL. Rotation is now a single conditional
`UPDATE ... WHERE revoked = false`, and exactly one caller sees a row updated.
`RefreshTokenAbuseTest` and `RefreshTokenRacePostgresTest` each run the race 20 times.

### N2. No refresh-token reuse detection (High, fixed)

A rotated token presented again more than 10s after rotation (`APP_SECURITY_REFRESH_REUSE_GRACE_SECONDS`)
can only be a copy. It now revokes every live token of that user, raises `REFRESH_REUSE_DETECTED`
(WARN, and a critical alert), and returns the same generic 401 as every other refresh failure.
The revocation is committed despite the 401 (`noRollbackFor`), and a test proves the row is revoked.

### N3. No admin audit trail (Medium, fixed)

- The `audit_log` table (V7) records every admin create, update, publish and delete, uploads,
  admin-user creation, password changes, backfills, leads marked handled, and admin sign-ins.
- Rows are written inside the same transaction as the change, and the summary names fields, never values.
- An admin sign-in from an address not seen in 90 days raises `ADMIN_LOGIN_NEW_IP` and emails the admin.
- The trail is readable at `GET /api/admin/audit-log` and in the dashboard's **Activity** tab.

### N4. No CI (Medium, fixed)

`.github/workflows/ci.yml` runs on every push and PR to `main`:
- `mvn verify`, including the PostgreSQL tests
- lint, build and `npm audit`
- gitleaks over the full history
- the header-sync check (M12's standing risk)

`security-scan.yml` runs the lab weekly. Dependabot covers Maven, npm, Actions and Docker.

### N5. Account lockout never persisted (High, open)

`login()` is `@Transactional`, and the `UnauthorizedException` thrown after
`registerFailedAttempt()` rolls the counter back. After six wrong passwords the account shows
`failed_login_attempts = 0` and is not locked. Meanwhile the `iunu.auth.account.locked` metric and
the log line fire as if it had locked. The lab confirms it: after six wrong passwords the lab
admin's real password still returned **200**, and `ACCOUNT_LOCKED` stayed at **0**.

In practice the only brake on password guessing is the per-IP login limit (10/min), plus
Cloudflare's rule once it exists. The fix is one annotation (`noRollbackFor`, as `refresh()`
now uses), but a working lockout switches on M9's lockout denial of service, which you chose to
accept as report-only. **Your call:** fix N5 alone, fix it together with M9 option (a), or leave
both as they are.

### N6. X-Forwarded-For bypass of every per-IP limit under the prod profile (High, fixed)

`application-prod.yml` set `server.forward-headers-strategy: framework`. That installs Spring's
`ForwardedHeaderFilter`, which replaces `getRemoteAddr()` with the **leftmost**
`X-Forwarded-For` entry (the one the caller typed) and then removes the header, before
`ClientIpResolver` ever sees the request. H2's fix was therefore bypassed in production: the
caller chose their own bucket for the login, lead-form and public limits.

The original audit's test of H2 ran without the prod profile, so it missed this. The lab runs
the prod profile behind a proxy:

| Through the lab proxy | Result |
|---|---|
| 360 × `GET /api/properties`, no header | 316 × 200, then **44 × 429** |
| 360 × `GET /api/properties`, rotating fake `X-Forwarded-For` | **360 × 200, no 429** |

The first fix, `native`, did not hold: with a blank `remote-ip-header`, Spring Boot falls back to
`X-Forwarded-For`, and with every hop trusted Tomcat's valve does the same leftmost rewrite. The
lab caught that on its next run, which is why it exists.

The fix that holds is `server.forward-headers-strategy: none`. The one job that setting did,
marking a request as HTTPS so Spring Security still sends HSTS, is now done by
`ForwardedProtoFilter`. That filter reads only `X-Forwarded-Proto`, and only from a peer in
`TRUSTED_PROXIES`, the same trust rule `ClientIpResolver` applies. Only `ClientIpResolver` decides
the client address.

Tests:
- `ForwardedHeadersBypassTest` sends 400 requests with rotating fake entries through a trusted
  proxy and requires a 429. It fails under `framework`.
- `ProdForwardedHeadersTest` pins the prod setting.
- The lab's `abuse.js` spoofing check covers the container-level behaviour, including the valve,
  which MockMvc cannot run.

### N7. Health probes behind the edge secret (Low, fixed)

`EdgeSecretFilter` exempted only `/actuator/health`. With `EDGE_SHARED_SECRET` set,
`/actuator/health/liveness` and `/readiness` returned 403, so a platform check or uptime monitor
pointed at them would have taken the service down. All three probe paths are now exempt, by exact
match.

### Lab results

_The results table from the confirming run, with the N6 fix in place, is added below once that run completes._

### Report-only measurements

These are findings you have accepted. The lab measures them; it does not change them.

| Item | Measurement | Reading |
|---|---|---|
| **M2** chunked body | A 2MB JSON body with no `Content-Length` returned **400**, not 413 | The body was read and parsed, then refused by `@Size` validation. The `Content-Length` check cannot see it. Still needs the edge body-size rule |
| **M8** reset timing | `forgot-password` median **8.6ms** for an existing account vs **2.9ms** for a missing one (10 samples each, mail disabled) | Account existence is still visible from timing. With SMTP on, the gap grows by the SMTP round trip |
| **M9** lockout | Real password after 6 failures: **200** | No lockout at all today (N5), so M9's lockout denial of service cannot happen yet |
| Cache busting | 500 distinct `page`/`size` combinations, paced under the limit: **0% hit ratio**, 0 × 429, all served | An attacker who stays under 5/s defeats the property cache completely. Cloudflare's cache rule and a query-string rule are the lever (runbook Part 2 §3) |

### Detection check

Counters read from `/actuator/metrics` after `attack.js`:

| Event | Count | |
|---|---|---|
| `LOGIN_FAILED` | 27 | credential stuffing, lockout probe |
| `REFRESH_REUSE_DETECTED` | 1 | refresh replay |
| `REFRESH_RACE_LOST` | 1 | refresh race |
| `ADMIN_LOGIN_NEW_IP` | 2 | first admin sign-in from each client |
| `RATE_LIMITED` | 20,562 | ZAP, k6 |
| `ACCESS_DENIED` | 251 | USER sweep, anonymous probes |
| `UPLOAD_REJECTED` | 8 | upload abuse |
| `EDGE_SECRET_REJECTED` | 1 | origin bypass |
| `TOKEN_INVALID` | 1 | malformed bearer |
| `PASSWORD_RESET_REQUESTED` | 20 | reset timing |
| **`ACCOUNT_LOCKED`** | **0** | **gap: N5** |

`PASSWORD_RESET_COMPLETED` and `PASSWORD_CHANGED` are not exercised by the lab. They need a
mailbox or the user's password. `SecurityLogRedactionTest` covers them on every build, and also
asserts that no raw email, token, password or `Bearer` string reaches the `SECURITY` log.

The alert rules in `monitoring/alert-rules.yml` were checked against the lab's
`/actuator/prometheus` scrape: all eight metric names they use exist.

### Not done

- **OWASP dependency-check.** Its first run could not complete in the environment used for this
  addendum, because the NVD and CISA feed downloads were blocked. The weekly CI scan runs it; set
  the `NVD_API_KEY` repository secret so it finishes in minutes rather than hours.
- **Out of scope:** M7 (tokens in HttpOnly cookies), M9, M10 and M11, and everything in
  `DDOS_RUNBOOK.md` Part 1.
