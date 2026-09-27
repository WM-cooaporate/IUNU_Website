// Shared configuration for every script in this directory.
//
// The production guard below is the important part. A load test against the
// live site is indistinguishable from an attack: it fills the client's leads
// table, trips their rate limits, burns their hosting quota, and may breach the
// host's acceptable-use policy. BASE_URL has to be named explicitly, and a
// known production host is refused outright.

export const BASE_URL = __ENV.BASE_URL;

/**
 * Comma-separated hostnames that must never be load tested. Set PROD_HOSTS in
 * the shell profile or CI so the guard is on by default rather than something
 * each person has to remember:
 *
 *   export PROD_HOSTS="iunu-eg.com,iunu-api.onrender.com"
 */
const PROD_HOSTS = (__ENV.PROD_HOSTS || "iunu-eg.com,iunu-api.onrender.com")
  .split(",")
  .map((host) => host.trim())
  .filter(Boolean);

if (!BASE_URL) {
  throw new Error("Set BASE_URL, e.g. -e BASE_URL=http://localhost:8080/api");
}

if (PROD_HOSTS.some((host) => BASE_URL.includes(host)) && __ENV.I_KNOW_THIS_IS_PROD !== "yes") {
  throw new Error(
    `Refusing to load test production host: ${BASE_URL}\n` +
      "Point BASE_URL at localhost or a staging deployment. If you genuinely " +
      "mean to test this host, set I_KNOW_THIS_IS_PROD=yes - and tell whoever " +
      "owns the hosting account first."
  );
}

/**
 * Pass/fail criteria, shared so every script is judged the same way.
 *
 * The per-tag durations matter more than the aggregate: a listing page doing
 * a database query and a detail page reading one row have different budgets,
 * and averaging them hides which one degraded.
 */
export const thresholds = {
  http_req_failed: ["rate<0.01"],
  "http_req_duration{type:list}": ["p(95)<500"],
  "http_req_duration{type:detail}": ["p(95)<400"],
  "http_req_duration{type:image}": ["p(95)<800"],
};

/** The real enum. A value outside it is a 400, which would skew the error rate. */
export const PROPERTY_TYPES = ["RESIDENTIAL", "COMMERCIAL", "ADMINISTRATIVE"];

/**
 * Identifies this traffic in the access log and in Cloudflare's event feed, so
 * a spike during a run is not mistaken for an attack - or vice versa.
 *
 * <p>With DISTINCT_CLIENTS=yes each VU sends its own X-Forwarded-For, so 200
 * VUs arrive as 200 clients rather than one very busy one. Without it, a load
 * test from a single machine measures the per-IP rate limiter and nothing else:
 * 200 VUs at one request every few seconds is thousands per minute from one
 * address, so the app answers 429 to almost all of it and the p95 you record is
 * the limiter's.
 *
 * <p>Only meaningful when the target is in x-forwarded-for mode
 * (CLIENT_IP_MODE=x-forwarded-for); otherwise the header is ignored, which is
 * the whole point of that mode. Never use it against an environment where the
 * limiter is what you are trying to measure - that is abuse.js's job.
 */
const DISTINCT_CLIENTS = __ENV.DISTINCT_CLIENTS === "yes";

export const params = (tag) => {
  const headers = { Accept: "application/json", "User-Agent": "k6-iunu-loadtest" };
  if (DISTINCT_CLIENTS) {
    // Right-hand entry, because that is the one the server reads: a proxy
    // appends what it saw, so this is the shape a real hop produces.
    headers["X-Forwarded-For"] = `10.${__VU % 250}.${Math.floor(__VU / 250) % 250}.1`;
  }
  return { tags: { type: tag }, headers };
};
