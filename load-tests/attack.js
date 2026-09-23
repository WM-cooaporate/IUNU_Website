import http from "k6/http";
import { check, group, sleep } from "k6";
import { Gauge, Trend } from "k6/metrics";
import { BASE_URL } from "./lib/config.js";

/**
 * What a real attacker tries that abuse.js does not: refresh-token replay and
 * races, credential stuffing, path and verb tricks, hostile uploads, cache
 * busting, lead-form spam, and reaching the origin around the edge.
 *
 * <p>Run by security/run-lab.sh against the local lab and nothing else. On top
 * of lib/config.js's production-host guard, this script refuses any target
 * that is not localhost, 127.0.0.1 or a lab service name - with no override.
 * Several groups here fill tables and burn rate limits on purpose.
 *
 * <p>Check names say what the attacker is trying. A failed check fails the run.
 * Findings the owner has chosen to accept (M2, M8, M9) are recorded as
 * "report_*" metrics instead and never fail it.
 *
 * <p>Forged-token cases (alg:none, tampered signatures) are deliberately not
 * here: JwtValidationTest covers them on every build.
 *
 * <p>Sequential, 1 VU: the groups share rate-limit buckets. The login bucket
 * (10/min per IP) is spent by several groups, so those start after a 61s
 * pause that gives them a full allowance.
 */

const LAB_HOSTS = ["localhost", "127.0.0.1", "proxy", "backend", "frontend"];
const targetHost = BASE_URL.replace(/^[a-z]+:\/\//, "").split(/[/:]/)[0];
if (!LAB_HOSTS.includes(targetHost)) {
  throw new Error(`attack.js only runs against the local lab (${LAB_HOSTS.join(", ")}). Got: ${targetHost}`);
}

const ORIGIN = BASE_URL.replace(/\/api$/, "");
/** The backend itself, skipping the proxy - what an attacker who found the origin URL reaches. */
const DIRECT_BACKEND = __ENV.DIRECT_BACKEND_URL || "http://backend:8080";
const ADMIN_EMAIL = __ENV.LAB_ADMIN_EMAIL;
const ADMIN_PASSWORD = __ENV.LAB_ADMIN_PASSWORD;
const GRACE_SECONDS = Number(__ENV.REFRESH_REUSE_GRACE_SECONDS || 10);

if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
  throw new Error("Set LAB_ADMIN_EMAIL and LAB_ADMIN_PASSWORD (security/run-lab.sh does).");
}

export const options = {
  scenarios: {
    attack: { executor: "per-vu-iterations", vus: 1, iterations: 1, maxDuration: "30m" },
  },
  setupTimeout: "2m",
  thresholds: {
    // Every defence must hold. Report-only measurements are metrics, not checks.
    checks: ["rate==1.0"],
    // Most of this file provokes 4xx on purpose; the checks are the verdict.
    http_req_failed: ["rate<=1"],
  },
};

// Report-only measurements. Never fail the run; printed in the summary.
const resetExisting = new Trend("report_m8_forgot_password_existing_ms", true);
const resetMissing = new Trend("report_m8_forgot_password_missing_ms", true);
const lockoutRealPasswordStatus = new Gauge("report_m9_real_password_status_after_6_failures");
const cacheHitRatio = new Gauge("report_cache_hit_ratio_during_busting");
const cacheBustingThrottled = new Gauge("report_cache_busting_429s");
const translationCharsDelta = new Gauge("report_translation_chars_delta");

const JSON_HEADERS = { "Content-Type": "application/json" };
const json = (body) => JSON.stringify(body);
const bearer = (token) => ({ Authorization: `Bearer ${token}` });

const ADMIN_PATHS = [
  ["GET", "/admin/users"],
  ["POST", "/admin/users"],
  ["GET", "/admin/contacts"],
  ["GET", "/admin/quotes"],
  ["GET", "/admin/projects"],
  ["GET", "/admin/audit-log"],
  ["GET", "/properties/admin"],
  ["POST", "/properties"],
  ["DELETE", "/properties/1"],
  ["POST", "/admin/translations/properties/backfill"],
];

function login(email, password) {
  return http.post(`${BASE_URL}/auth/login`, json({ email, password }), { headers: JSON_HEADERS });
}

function refresh(refreshToken) {
  return http.post(`${BASE_URL}/auth/refresh`, json({ refreshToken }), { headers: JSON_HEADERS });
}

/** One Actuator statistic, read with the admin token. Missing reads as 0. */
function metric(adminToken, name, tags = [], statistic = "COUNT") {
  const query = tags.map((tag) => `tag=${encodeURIComponent(tag)}`).join("&");
  const response = http.get(`${ORIGIN}/actuator/metrics/${name}${query ? `?${query}` : ""}`,
    { headers: bearer(adminToken), tags: { name: "actuator" } });
  if (response.status !== 200) return 0;
  const measurement = (response.json("measurements") || []).find((m) => m.statistic === statistic);
  return measurement ? measurement.value : 0;
}

const securityEvents = (adminToken, type) => metric(adminToken, "iunu.security.events", [`type:${type}`]);

/** A full login-bucket allowance: the login limiter refills 10 per minute. */
function waitForLoginBucket() {
  sleep(61);
}

export function setup() {
  const admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
  if (admin.status !== 200) throw new Error(`lab admin login failed: ${admin.status}`);

  // A plain USER account, created the way anyone on the internet can.
  const stamp = Date.now();
  const userEmail = `attacker-${stamp}@iunu-lab.test`;
  const userPassword = `LabUser${stamp}a`;
  const registered = http.post(`${BASE_URL}/auth/register`,
    json({ fullName: "Lab Attacker", email: userEmail, phone: "+20 100 000 0000", password: userPassword }),
    { headers: JSON_HEADERS });
  if (registered.status !== 201) throw new Error(`lab user registration failed: ${registered.status} ${registered.body}`);

  return { adminToken: admin.json("accessToken"), userEmail, userPassword };
}

export default function (data) {
  const { adminToken, userEmail, userPassword } = data;
  let userToken = null;

  group("refresh replay (N2)", () => {
    const stolen = login(userEmail, userPassword).json("refreshToken");
    const legitimate = refresh(stolen).json("refreshToken");
    const before = securityEvents(adminToken, "REFRESH_REUSE_DETECTED");

    // Past the grace window: the owner already holds the successor, so a
    // replay of the old token can only be a copy.
    sleep(GRACE_SECONDS + 1);
    const replay = refresh(stolen);
    const afterwards = refresh(legitimate);

    check(null, {
      "attacker replays a stolen, already-rotated refresh token: 401": () => replay.status === 401,
      "the owner's newer token dies with it (whole family revoked)": () => afterwards.status === 401,
      "the replay raised REFRESH_REUSE_DETECTED": () => securityEvents(adminToken, "REFRESH_REUSE_DETECTED") > before,
    });
  });

  group("refresh race (N1)", () => {
    const session = login(userEmail, userPassword);
    userToken = session.json("accessToken");
    const token = session.json("refreshToken");
    const responses = http.batch([
      ["POST", `${BASE_URL}/auth/refresh`, json({ refreshToken: token }), { headers: JSON_HEADERS }],
      ["POST", `${BASE_URL}/auth/refresh`, json({ refreshToken: token }), { headers: JSON_HEADERS }],
    ]);
    const wins = responses.filter((r) => r.status === 200).length;
    check(null, { "thief and owner race one refresh token: exactly one gets a new pair": () => wins === 1 });
  });

  group("wrong-role and malformed tokens", () => {
    const deniedBefore = securityEvents(adminToken, "ACCESS_DENIED");
    const invalidBefore = securityEvents(adminToken, "TOKEN_INVALID");

    const userResults = ADMIN_PATHS.map(([method, path]) =>
      http.request(method, `${BASE_URL}${path}`, "{}", { headers: { ...JSON_HEADERS, ...bearer(userToken) } }).status);
    const garbage = http.get(`${BASE_URL}/admin/users`, { headers: bearer("not-a-token") });

    check(null, {
      "a signed-in USER walks every admin endpoint: all 403": () => userResults.every((status) => status === 403),
      "a malformed bearer value on an admin path: 401": () => garbage.status === 401,
      "the USER sweep was counted as ACCESS_DENIED": () =>
        securityEvents(adminToken, "ACCESS_DENIED") >= deniedBefore + ADMIN_PATHS.length,
      "the malformed token was counted as TOKEN_INVALID": () => securityEvents(adminToken, "TOKEN_INVALID") > invalidBefore,
    });
  });

  group("path tricks", () => {
    const tricks = [
      "/api//admin/users",
      "/api/admin/./users",
      "/api/Admin/users",
      "/api/admin/users;x=1",
      "/api/admin%2fusers",
      "/api/properties/admin/..%2f..%2fadmin/users",
    ];
    const statuses = tricks.flatMap((path) => [
      http.get(`${ORIGIN}${path}`).status,
      http.get(`${ORIGIN}${path}`, { headers: bearer(userToken) }).status,
    ]);
    check(null, {
      "path normalisation tricks never reach an admin handler (400/401/403/404 only)": () =>
        statuses.every((status) => [400, 401, 403, 404].includes(status)),
    });
  });

  group("verb tampering", () => {
    const created = http.post(`${BASE_URL}/properties`,
      json({ title: "Verb tampering target", type: "RESIDENTIAL", published: true }),
      { headers: { ...JSON_HEADERS, ...bearer(adminToken) } });
    const id = created.json("id");

    const trace = http.request("TRACE", `${BASE_URL}/properties`);
    const optionsNoOrigin = http.request("OPTIONS", `${BASE_URL}/admin/users`);
    const hidden = http.post(`${BASE_URL}/properties/${id}?_method=DELETE`, "_method=DELETE",
      { headers: { "Content-Type": "application/x-www-form-urlencoded", ...bearer(userToken) } });
    const override = http.post(`${BASE_URL}/properties/${id}`, "{}",
      { headers: { ...JSON_HEADERS, ...bearer(userToken), "X-HTTP-Method-Override": "DELETE" } });
    const stillThere = http.get(`${BASE_URL}/properties/admin/${id}`, { headers: bearer(adminToken) });

    check(null, {
      "the admin could create the target property": () => created.status === 201,
      "TRACE is never answered with 200": () => trace.status !== 200,
      "OPTIONS without an Origin does not bypass admin auth": () => optionsNoOrigin.status !== 200,
      "a USER's _method=DELETE is refused": () => hidden.status !== 200 && hidden.status !== 204,
      "a USER's X-HTTP-Method-Override: DELETE is refused": () => override.status !== 200 && override.status !== 204,
      "the property still exists afterwards": () => stillThere.status === 200,
    });
  });

  group("upload abuse", () => {
    const before = securityEvents(adminToken, "UPLOAD_REJECTED");
    const png = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
    const bytes = (values) => new Uint8Array(values).buffer;
    const upload = (name, content, type) =>
      http.post(`${BASE_URL}/properties/images`, { files: http.file(content, name, type) },
        { headers: bearer(adminToken) });

    const svgAsPng = upload("logo.png", '<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"/>', "image/png");
    const htmlWithMagicLater = upload("page.png",
      bytes([...Array.from("<html><script>alert(1)</script>", (c) => c.charCodeAt(0)), ...png]), "image/png");
    const oneByte = upload("tiny.png", bytes([0x89]), "image/png");
    const oversized = upload("huge.png", bytes([...png, ...new Array(6 * 1024 * 1024).fill(0)]), "image/png");
    const traversal = upload("../../evil.png", bytes([...png, 1, 2, 3, 4]), "image/png");
    const storedUrl = traversal.status === 200 ? (traversal.json() || [])[0] || "" : "";

    check(null, {
      "an SVG renamed .png is refused (stored XSS)": () => svgAsPng.status === 400,
      "HTML with PNG magic bytes after a prefix is refused": () => htmlWithMagicLater.status === 400,
      "a 1-byte file is refused": () => oneByte.status === 400,
      "an over-limit file is refused with 413": () => oversized.status === 413,
      "a ../../ filename is stored under a content hash, not the given name": () =>
        traversal.status === 200 && /\/uploads\/properties\/[0-9a-f]{64}\.png$/.test(storedUrl),
      "the rejections were counted as UPLOAD_REJECTED": () => securityEvents(adminToken, "UPLOAD_REJECTED") >= before + 3,
    });
  });

  group("big body", () => {
    // The chunked variant (no Content-Length, the M2 residual) is sent by
    // run-lab.sh with curl: k6 always sets Content-Length.
    const twoMegabytes = json({ message: "A".repeat(2 * 1024 * 1024) });
    const response = http.post(`${BASE_URL}/contact`, twoMegabytes, { headers: JSON_HEADERS });
    check(response, { "a 2MB JSON body with Content-Length is refused with 413": (r) => r.status === 413 });
  });

  group("origin bypass", () => {
    const before = securityEvents(adminToken, "EDGE_SECRET_REJECTED");
    const direct = http.get(`${DIRECT_BACKEND}/api/properties`);
    const directHealth = http.get(`${DIRECT_BACKEND}/actuator/health`);
    check(null, {
      "a request that skips the edge is refused with 403": () => direct.status === 403,
      "the health probe stays reachable for the platform": () => directHealth.status === 200,
      "the bypass was counted as EDGE_SECRET_REJECTED": () => securityEvents(adminToken, "EDGE_SECRET_REJECTED") > before,
    });
  });

  group("lead-form spam", () => {
    // The four forms share one write bucket per IP (20/hour), so once it is
    // spent every form answers 429 straight away.
    const forms = [
      ["/contact", json({ fullName: "Spam", email: "spam@iunu-lab.test", message: "spam" }), JSON_HEADERS],
      ["/quotes", json({ fullName: "Spam", email: "spam@iunu-lab.test", phone: "+20 100 000 0000" }), JSON_HEADERS],
      ["/newsletter", json({ email: "spam@iunu-lab.test" }), JSON_HEADERS],
      ["/careers", json({}), JSON_HEADERS],
    ];
    const limitedWithin = forms.map(([path, body, headers]) => {
      for (let attempt = 1; attempt <= 25; attempt++) {
        if (http.post(`${BASE_URL}${path}`, body, { headers }).status === 429) return attempt;
      }
      return null;
    });
    forms.forEach(([path], index) => {
      check(null, { [`flooding ${path} hits 429 within the write limit`]: () => limitedWithin[index] !== null && limitedWithin[index] <= 21 });
    });
  });

  group("translation cost", () => {
    const charsBefore = metric(adminToken, "iunu.translation.chars");
    let limitedAt = null;
    for (let attempt = 1; attempt <= 35 && limitedAt === null; attempt++) {
      const response = http.post(`${BASE_URL}/admin/translations/preview`,
        json({ title: "Villa", description: "Sea view", location: "Cairo" }),
        { headers: { ...JSON_HEADERS, ...bearer(adminToken) } });
      if (response.status === 429) limitedAt = attempt;
    }
    // No API key in the lab, so the budget counter cannot move; recorded anyway.
    translationCharsDelta.add(metric(adminToken, "iunu.translation.chars") - charsBefore);
    check(null, { "a stolen admin token looping translation previews hits 429 by attempt 31": () => limitedAt !== null && limitedAt <= 31 });
  });

  group("cache busting", () => {
    const hits = () => metric(adminToken, "cache.gets", ["cache:publicPropertyList", "result:hit"]);
    const misses = () => metric(adminToken, "cache.gets", ["cache:publicPropertyList", "result:miss"]);
    const hitsBefore = hits();
    const missesBefore = misses();
    let served = 0;
    let throttled = 0;
    let broken = 0;

    // 500 distinct page/size combinations, paced just under the 5/s public
    // limit - an attacker defeating the cache while staying under the limiter.
    for (let i = 0; i < 500; i++) {
      const status = http.get(`${BASE_URL}/properties?page=${i % 10}&size=${Math.floor(i / 10) + 1}`,
        { tags: { name: "cache-busting" } }).status;
      if (status === 200) served++;
      else if (status === 429) throttled++;
      else broken++;
      sleep(0.22);
    }

    const hitDelta = hits() - hitsBefore;
    const missDelta = misses() - missesBefore;
    cacheHitRatio.add(hitDelta + missDelta > 0 ? hitDelta / (hitDelta + missDelta) : 0);
    cacheBustingThrottled.add(throttled);
    check(null, {
      "500 cache-busting reads: still served, never a 5xx": () => broken === 0 && served > 0,
    });
  });

  group("credential stuffing", () => {
    waitForLoginBucket();
    const failedBefore = metric(adminToken, "iunu.auth.login.failed");
    let limitedAt = null;
    for (let attempt = 1; attempt <= 30; attempt++) {
      const response = login(`stuffed-${attempt}-${Date.now()}@iunu-lab.test`, "Password123!");
      if (response.status === 429 && limitedAt === null) limitedAt = attempt;
    }
    check(null, {
      "30 logins across 30 emails from one IP: 429 before attempt 11": () => limitedAt !== null && limitedAt <= 11,
      "iunu.auth.login.failed rose": () => metric(adminToken, "iunu.auth.login.failed") > failedBefore,
    });
  });

  group("reset enumeration (report only, M8)", () => {
    // Ten samples each, interleaved, spread over two login-bucket windows.
    for (let batch = 0; batch < 2; batch++) {
      waitForLoginBucket();
      for (let sample = 0; sample < 5; sample++) {
        const existing = http.post(`${BASE_URL}/auth/forgot-password`, json({ email: ADMIN_EMAIL }), { headers: JSON_HEADERS });
        if (existing.status === 200) resetExisting.add(existing.timings.duration);
        const missing = http.post(`${BASE_URL}/auth/forgot-password`,
          json({ email: `nobody-${batch}-${sample}-${Date.now()}@iunu-lab.test` }), { headers: JSON_HEADERS });
        if (missing.status === 200) resetMissing.add(missing.timings.duration);
      }
    }
  });

  group("admin lockout (report only, M9)", () => {
    // Last, because a working lockout would lock the lab admin for 15 minutes.
    waitForLoginBucket();
    for (let attempt = 0; attempt < 6; attempt++) {
      login(ADMIN_EMAIL, `wrong-password-${attempt}`);
    }
    lockoutRealPasswordStatus.add(login(ADMIN_EMAIL, ADMIN_PASSWORD).status);
  });
}

/** The report-only numbers and every check, as JSON for run-lab.sh and as text for the log. */
export function handleSummary(data) {
  const metrics = data.metrics;
  const value = (name, field) => (metrics[name] && metrics[name].values ? metrics[name].values[field] : null);
  const report = {
    m2_note: "chunked-body result is measured by run-lab.sh (curl)",
    m8_forgot_password_existing_median_ms: value("report_m8_forgot_password_existing_ms", "med"),
    m8_forgot_password_missing_median_ms: value("report_m8_forgot_password_missing_ms", "med"),
    m9_real_password_status_after_6_failures: value("report_m9_real_password_status_after_6_failures", "value"),
    cache_hit_ratio_during_busting: value("report_cache_hit_ratio_during_busting", "value"),
    cache_busting_429s: value("report_cache_busting_429s", "value"),
    translation_chars_delta: value("report_translation_chars_delta", "value"),
  };

  const checks = [];
  const walk = (groupNode, prefix) => {
    (groupNode.checks || []).forEach((c) => checks.push({ group: prefix, name: c.name, passes: c.passes, fails: c.fails }));
    (groupNode.groups || []).forEach((g) => walk(g, g.name));
  };
  walk(data.root_group, "");

  const lines = ["", "attack.js", "========="];
  checks.forEach((c) => lines.push(`${c.fails === 0 ? "PASS" : "FAIL"}  [${c.group}] ${c.name}`));
  lines.push("", "Report only (never fails the run):");
  Object.entries(report).forEach(([key, v]) => lines.push(`  ${key}: ${v}`));
  lines.push("");

  const out = { stdout: lines.join("\n") };
  if (__ENV.REPORT_DIR) out[`${__ENV.REPORT_DIR}/attack-summary.json`] = JSON.stringify({ checks, report }, null, 2);
  return out;
}
