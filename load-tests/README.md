# Load tests

k6 scripts for the IUNU backend: what it does under load, and whether the
things meant to say no actually do.

**Read this first.** A load test against the live site is indistinguishable
from an attack. It fills the client's leads table, trips their rate limits,
burns their hosting quota, and may breach the host's acceptable-use policy.
Every script here refuses to run against a known production hostname
(`load-tests/lib/config.js`), but the guard only knows the hosts it has been
told about. Check the host's policy before any large test:

- **Render** — permits load testing your own services; sustained abusive
  traffic is not. Free-plan instances spin down when idle and have 0.1 CPU, so
  the numbers mean nothing.
- **Vercel** — the frontend is static and behind their CDN; there is nothing
  useful to load test there, and their abuse policy applies.
- **Cloudflare** — once traffic is proxied, a load test looks like a flood to
  Bot Fight Mode and the WAF. Expect challenges, and test the origin directly
  or whitelist your source address first.

## Install k6

```bash
# macOS
brew install k6
# Debian/Ubuntu
sudo gpg -k && sudo gpg --no-default-keyring \
  --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
# Docker, no install
docker run --rm -i -v "$PWD:/scripts" grafana/k6 run /scripts/smoke.js -e BASE_URL=...
```

## The scripts

| Script | Shape | Answers |
|---|---|---|
| `smoke.js` | 1 VU, 30s | Is the target up and answering correctly? |
| `load.js` | ramp to 200 VUs, hold 5m | Does expected peak traffic hold? |
| `stress.js` | 100 → 1000 VUs in steps | What breaks first, and at what load? |
| `spike.js` | 0 → 300 VUs in 10s | Does it survive a burst, and recover after? |
| `soak.js` | 50 VUs for 60m | Does anything leak over an hour? |
| `abuse.js` | 1 VU, sequential | Do the DoS defences still work? |

```bash
# Always smoke first. A stress run against a wrong BASE_URL is a graph of 404s.
k6 run -e BASE_URL=http://localhost:8080/api smoke.js
k6 run -e BASE_URL=http://localhost:8080/api abuse.js
k6 run -e BASE_URL=http://localhost:8080/api load.js
k6 run -e BASE_URL=http://localhost:8080/api stress.js
k6 run -e BASE_URL=http://localhost:8080/api spike.js
k6 run -e BASE_URL=http://localhost:8080/api soak.js
```

Environment variables:

| Variable | Purpose |
|---|---|
| `BASE_URL` | Required. Includes the `/api` path segment. |
| `PROD_HOSTS` | Comma-separated hostnames the guard refuses. Defaults to the known production hosts. |
| `I_KNOW_THIS_IS_PROD` | `yes` overrides the guard. Do not. |
| `BEHIND_PROXY` | `yes` enables `abuse.js`'s X-Forwarded-For spoofing check, which is meaningless without a proxy. |

## Where to run

**Not production.** Not a free tier either, at least not for numbers you intend
to act on: a free instance sleeps when idle, wakes with a cold JVM and an empty
cache, and runs on a fraction of a CPU. A p95 measured there says more about
the plan than about the code.

Two environments are worth the effort:

1. **Local**, for finding regressions. Runs against a real PostgreSQL rather
   than H2, with `SWAGGER_ENABLED=false` and the rate limiter on. Fast to
   iterate on, and the relative numbers between two commits are meaningful even
   though the absolute ones are not.
2. **Staging on the same plan as production**, for numbers you will quote. Same
   instance size, same database plan, same region. Anything else is a guess.

## What to watch during a run

Actuator, with an ADMIN token (`Authorization: Bearer <token>`):

```bash
TOKEN=...   # an ADMIN access token
BASE=http://localhost:8080

curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/http.server.requests
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/hikaricp.connections.active
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/hikaricp.connections.pending
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/cache.gets
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/jvm.memory.used
curl -H "Authorization: Bearer $TOKEN" $BASE/actuator/metrics/iunu.ratelimit.rejected
```

What each one tells you:

- **`hikaricp.connections.pending` above zero** — requests are queuing for a
  database connection. The pool is the bottleneck, not the CPU. Raising
  `DB_POOL_MAX` helps only while the database plan has connections to spare;
  past that it converts slow requests into failed ones.
- **`cache.gets` with a low hit ratio under load** — the cache is not helping.
  Either the key space is too wide (many distinct page/type combinations) or
  writes are evicting faster than reads fill.
- **`iunu.ratelimit.rejected` climbing** — the limiter is firing. During a load
  test that means the test is generating more per-IP traffic than a real
  visitor would, and the numbers past that point measure the limiter rather
  than the app.
- **`jvm.memory.used` rising and not returning after GC** — a leak. Only a soak
  run shows this.

Also watch the host dashboard (Render/Railway): CPU, memory, and whether the
instance restarted mid-run. A restart invalidates everything after it.

## Reading the k6 summary

```
http_req_duration..........: avg=45ms min=2ms med=38ms max=1.2s p(90)=78ms p(95)=95ms
http_req_failed............: 0.12% ✓ 14  ✗ 11655
checks.....................: 99.88%
```

- **`p(95)`, not `avg`.** The average hides the tail, and the tail is what
  people experience as "the site is slow".
- **`http_req_failed`** counts 4xx and 5xx. Anything above ~1% under normal
  load needs explaining before the run is worth quoting.
- **`max` far above `p(95)`** is usually a GC pause, a cold cache entry, or a
  connection acquired after a wait. Correlate with the Actuator metrics rather
  than guessing.
- Thresholds are the pass/fail line; k6 exits non-zero when one is crossed
  (except in `stress.js`, where crossing them *is* the result).

## Results log

Record every run worth remembering. Local numbers and production numbers are
different measurements - say which is which.

| Date | Commit | Environment | Test | Peak VUs | p95 (list) | Error rate | First bottleneck observed |
|---|---|---|---|---|---|---|---|
| | | | | | | | |
| | | | | | | | |

## A note on what this can and cannot show

These scripts measure the application. They cannot measure resistance to a
volumetric DDoS, because by the time traffic reaches Spring Boot the host's
bandwidth and CPU are already spent. That defence lives at the edge - see
`docs/DDOS_RUNBOOK.md`.
