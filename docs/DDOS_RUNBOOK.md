# DDoS runbook

## The thing to understand first

**Application code cannot stop a volumetric DDoS.** By the time a request reaches Spring Boot, the host's bandwidth and CPU have already been spent receiving it. No rate limiter, no cache and no amount of tuning changes that — a limiter still has to read the request before it can refuse it.

What the code in this repository does is make the app survive **abuse and moderate floods**: scripted scraping, credential stuffing, oversized requests, one request engineered to cost a thousand, and a stolen admin token running up a bill. That is worth having, and it is not DDoS protection.

Real DDoS protection happens at the edge, before traffic reaches the origin. The frontend already has it — Vercel absorbs attacks on the static site as part of the platform. The **backend does not**, and everything below is about giving it some. All of it is dashboard configuration that has to be done by a person with access to those accounts; none of it can be automated from here.

**Do not tell anyone this site is "DDoS-proof".** With the steps below it is meaningfully harder to knock over. That is the honest claim.

---

## Part 1 — Setup (once)

Do these in order. Steps 7 and 8 in particular will take the API offline if done before the steps they depend on.

### 1. Put the backend behind Cloudflare

Cloudflare cannot protect a hostname it does not serve, and it cannot proxy a `*.onrender.com` address.

1. In Render → your backend service → **Settings → Custom Domains**, add `api.<client-domain>` (e.g. `api.iunu-eg.com`). Render shows you a DNS target.
2. In Cloudflare → the zone for `<client-domain>` → **DNS**, add a `CNAME` for `api` pointing at that target.
3. Set the proxy status to **Proxied** (the orange cloud). This is the whole point of the step — a grey cloud is plain DNS and gives you nothing.
4. Wait for Render to show the domain as verified with a certificate issued.
5. Update `VITE_API_URL` in Vercel to `https://api.<client-domain>/api` and redeploy. Vite inlines this at build time, so a redeploy is required.
6. Update `CORS_ALLOWED_ORIGINS` on Render if the frontend origin changed.
7. **Update the CSP in `vercel.json`**: `connect-src 'self' https://api.<client-domain>`. If you skip this the browser blocks every API call and the site looks broken with no server-side error to find.

The frontend stays on Vercel. Do not move it.

### 2. SSL/TLS mode: Full (strict)

Cloudflare → **SSL/TLS → Overview → Full (strict)**.

Anything less is worth knowing about: *Flexible* means Cloudflare talks to your origin over plain HTTP, so the traffic is unencrypted for the leg that crosses the public internet — while the browser shows a padlock. *Full* encrypts it but does not verify the origin certificate. **Full (strict)** encrypts and verifies. Render provides a valid certificate, so there is no reason to use anything else.

### 3. Bot Fight Mode

Cloudflare → **Security → Bots → Bot Fight Mode: On**.

Free, and it removes most of the unsophisticated scraping and scanning traffic before it costs you anything. Note that it challenges some automated clients — if you run a k6 test against the proxied hostname afterwards, expect challenges (and see `load-tests/README.md`).

### 4. WAF managed ruleset

Cloudflare → **Security → WAF → Managed rules** → enable the **Cloudflare Free managed ruleset**.

Generic protection against known attack patterns. Leave it in its default action; if it ever blocks something legitimate, **Security → Events** shows which rule fired and you can add a targeted exception rather than turning the set off.

### 5. Rate-limiting rule on the auth endpoints

Cloudflare → **Security → WAF → Rate limiting rules → Create**. The free tier allows exactly one, so spend it on the most expensive endpoint:

- **Field:** URI Path — **Operator:** starts with — **Value:** `/api/auth/`
- **Rate:** 20 requests per 10 seconds, per IP
- **Action:** Block, for 60 seconds

This sits *in front of* the app's own 10/minute login limit and stops that traffic at the edge, where it costs you nothing. Login is the most expensive operation in the API — each attempt is a BCrypt verification at strength 12, roughly 250–400ms of CPU on Render's free plan — so it is also where an attacker gets the best return.

### 6. Cache rule for public property reads

Cloudflare → **Caching → Cache Rules → Create**.

- **If:** URI Path starts with `/api/properties` **AND** URI Path does **not** start with `/api/properties/admin`
- **Then:** Eligible for cache, **Respect origin TTL**

"Respect origin TTL" matters: the app already sends `Cache-Control: max-age=60, public` on exactly the two public reads and `no-store` on everything else, so Cloudflare caching honestly is the same as it caching correctly. The app also sends an `ETag`, so revalidation returns a 304 with no body.

Add a second rule to **Bypass cache** for `/api/properties/admin`, `/api/admin/`, `/api/auth/` and `/uploads/` if you want it explicit. The origin's `no-store` already covers the first three; being explicit costs nothing and survives someone changing the origin headers later.

Uploaded images (`/uploads/**`) are content-addressed and now served `max-age=31536000, immutable`, so Cloudflare will cache them essentially forever with no rule needed. This is the single biggest bandwidth reduction available to you.

### 7. Switch the app to Cloudflare's client IP header

**Only after traffic is actually flowing through Cloudflare (step 1 complete and verified).**

On Render, set:

```
CLIENT_IP_MODE=cloudflare
TRUSTED_PROXIES=<Cloudflare's IP ranges, or Render's internal proxy range>
```

`TRUSTED_PROXIES` is not optional here. `CF-Connecting-IP` is a header, and a header is only trustworthy if something you trust set it — without the allowlist, anything that can reach the origin directly sets its own and picks its own rate-limit bucket. Cloudflare publishes its ranges at <https://www.cloudflare.com/ips/>; if Render terminates before forwarding, the peer your app sees is Render's internal proxy, so check what `getRemoteAddr()` actually is before choosing the value.

Startup logs a WARN while `TRUSTED_PROXIES` is unset. Do not ignore it.

### 8. Close the origin

**This is the step that makes steps 2–7 mean anything, and the easiest one to skip.**

Your Render URL (`https://iunu-api.onrender.com`) keeps serving after everything above. It is discoverable from DNS history, from certificate transparency logs, and from the git history of `VITE_API_URL` in this repository. An attacker who finds it sends traffic straight to the origin, past every WAF rule, rate limit and cache rule you just configured.

Mitigations, weakest to strongest:

**a. Do not publish it.** Necessary but not sufficient — it is already in this repository's history and in CT logs. Treat it as public.

**b. Shared-secret header (implemented, disabled by default).**

1. Generate a secret: `openssl rand -base64 32`
2. Cloudflare → **Rules → Transform Rules → Modify Request Header → Create**:
   - **If:** Hostname equals `api.<client-domain>`
   - **Then:** Set static — Header name `X-Edge-Auth`, value = the secret
3. Deploy that rule and **confirm the site still works**.
4. *Only then*, on Render, set `EDGE_SHARED_SECRET` to the same value and redeploy.

The backend now refuses any request without that header. `/actuator/health` stays exempt so Render's health check — which comes from inside Render's network, not through Cloudflare — keeps passing and deploys do not roll back.

Getting the order wrong takes the API offline. If that happens: unset `EDGE_SHARED_SECRET` on Render and redeploy.

**c. IP allowlist at the origin.** Render's free plan does not offer origin firewall rules. If you move to a platform that does, allowlisting Cloudflare's published ranges is stronger than a shared secret, because it does not depend on a value that could leak.

### 9. Google Cloud: quota and budget alert on the Translation API

The app now enforces a daily character budget (`GOOGLE_TRANSLATE_DAILY_CHAR_LIMIT`, default 200,000), one backfill at a time, and 30 previews per minute per admin. **That is a backstop, not the cap.** It lives in one JVM's memory, resets on every deploy, and can only see spending by that process.

The real cap is in Google Cloud Console:

1. **APIs & Services → Cloud Translation API → Quotas** — set a daily character limit. This is a hard stop; Google refuses the request past it.
2. **Billing → Budgets & alerts** — create a budget with alerts at 50%, 90% and 100%. A budget alert notifies; it does not stop spending. The quota is what stops spending.
3. Restrict the API key itself: **APIs & Services → Credentials** → restrict it to the Cloud Translation API only, so a leaked key cannot be used against anything else in the project.

### 10. Render: usage limits

Render → **Account → Billing** — set a spend limit or a notification threshold. An attack that does not take the site down can still generate a surprise bill through bandwidth and autoscaling. A limit turns an unbounded invoice into an outage, which is the better failure.

---

## Part 2 — During an attack

### 1. Turn on Under Attack Mode

Cloudflare → **Overview → Under Attack Mode: On**.

Every visitor gets an interstitial challenge for about five seconds. It is disruptive and it works. Turn it on first and diagnose second — the diagnosis is easier once the flood has stopped.

### 2. Find out what you are looking at

Cloudflare → **Security → Events**. Filter to the last hour and look at:

- **Top IPs** — a handful of addresses means a simple attack you can block outright.
- **Top ASNs** — one hosting provider is a common signature; block or challenge the ASN.
- **Top countries** — the business is Egypt-focused, so overwhelmingly foreign traffic to `/api/*` is suspicious.
- **Top paths** — `/api/auth/login` means credential stuffing. `/api/properties` means scraping or a flood.

Then add a targeted **WAF custom rule**. Prefer *Managed Challenge* over *Block* while you are still guessing — a challenge inconveniences a real visitor, a block loses them. For example, for a foreign flood against an Egypt-focused site:

- **If:** Country does not equal `EG` **AND** URI Path starts with `/api/`
- **Then:** Managed Challenge

Remove it when the attack ends. It is a tourniquet, not a policy.

### 3. Check what the origin is actually experiencing

With an ADMIN token:

```bash
TOKEN=...
BASE=https://api.<client-domain>

# Is the limiter firing, and on which bucket?
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/iunu.ratelimit.rejected
# Is the database the bottleneck? pending > 0 means requests are queuing for a connection.
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/hikaricp.connections.active
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/hikaricp.connections.pending
# Is the cache still absorbing reads?
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/cache.gets
# Credential stuffing?
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/iunu.auth.login.failed
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/iunu.auth.account.locked
```

How to read it:

- **`iunu.ratelimit.rejected` climbing, site still responsive** — the defences are working. Leave them to it.
- **`hikaricp.connections.pending` above zero** — the pool is the bottleneck. Do not raise `DB_POOL_MAX` past the database plan's connection limit; that converts slow requests into failed ones. Get the traffic down at the edge instead.
- **`cache.gets` hit ratio collapsing** — the attack is requesting many distinct URLs (varying `page` or `type`) to defeat the cache. A Cloudflare rule that blocks requests with unusual query strings helps.
- **`iunu.auth.account.locked` rising** — someone is locking accounts deliberately. See M9 in `SECURITY_AUDIT.md`; short-term, the Cloudflare rate-limiting rule on `/api/auth/` is your lever.

### 4. If the origin is being hit directly

Symptom: Cloudflare's Events show little traffic, but the Render dashboard shows heavy load. That means the attacker found the platform URL.

Enable `EDGE_SHARED_SECRET` (setup step 8b) and redeploy. Confirm the Transform Rule exists **first** — otherwise you have taken the site down yourself.

### 5. Afterwards

1. Turn **Under Attack Mode** off. Leaving it on costs you real visitors.
2. Remove the temporary WAF rules — especially any country challenge.
3. Write down what happened: when it started, what the traffic looked like, what stopped it, what the metrics showed. The next one goes much faster with notes.
4. If the origin was hit directly, keep `EDGE_SHARED_SECRET` enabled permanently. There is no downside once the Transform Rule is in place.

---

## Quick reference

| Symptom | First action |
|---|---|
| Site slow, Cloudflare Events show a flood | Under Attack Mode |
| Login endpoint hammered | Check the `/api/auth/` rate-limiting rule exists; tighten it |
| Render dashboard busy, Cloudflare quiet | Origin hit directly → enable `EDGE_SHARED_SECRET` |
| `hikaricp.connections.pending` > 0 | Reduce traffic at the edge; do **not** raise the pool past the DB plan's limit |
| Unexpected Google Translate bill | Google Cloud Console → Quotas (the app's budget is only a backstop) |
| Admin locked out repeatedly | Cloudflare rate-limiting rule on `/api/auth/`; see M9 in the audit |

## What is still not covered

- **Layer 3/4 attacks** are Cloudflare's to absorb and you have no control over them beyond being proxied.
- **A single Render instance** is a single point of failure. Scaling out needs Redis first — the cache and the rate limiter are both in-process, so a second instance would have its own copy of each and neither would be correct. See the backend README.
- **The database** has its own connection limit. Whatever the app survives, the plan's limit is a ceiling above it.
