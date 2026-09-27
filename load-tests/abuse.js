import http from "k6/http";
import { check, group } from "k6";
import { BASE_URL } from "./lib/config.js";

/**
 * Checks each application-layer defence, one at a time, at low volume.
 *
 * <p>This is not a load test. It is a functional test of the things that are
 * meant to say no - run it after every deploy that touches security, because a
 * defence that silently stopped working looks exactly like one that is working.
 *
 * <p>Sequential (1 VU, 1 iteration) on purpose: the checks share rate-limit
 * buckets, so running them in parallel would have them spend each other's
 * allowances and report failures that are really interference.
 */
export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    // Every defence must hold. This suite has no tolerance.
    checks: ["rate==1.0"],
    // http_req_failed counts 4xx/5xx, and most of this file is deliberately
    // provoking them. The checks above are the real verdict.
    http_req_failed: ["rate<=1"],
  },
};

const json = { headers: { "Content-Type": "application/json" } };

export default function () {
  group("login rate limit", () => {
    // A throwaway address that belongs to no account. NEVER a real admin
    // email: repeated failures against one would lock the client out of their
    // own dashboard for 15 minutes, which is the account-lockout DoS this
    // suite is supposed to be testing for, not causing.
    const throwaway = `k6-throwaway-${Date.now()}@example.invalid`;
    let sawTooManyRequests = false;
    let retryAfter = null;

    for (let attempt = 1; attempt <= 15; attempt++) {
      const response = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({ email: throwaway, password: "definitely-not-the-password" }),
        json
      );
      if (response.status === 429) {
        sawTooManyRequests = true;
        retryAfter = response.headers["Retry-After"];
        break;
      }
    }

    check(null, {
      "login limiter fires within 15 attempts": () => sawTooManyRequests,
      // Without this a client retries immediately and makes the flood worse.
      "429 carries a Retry-After header": () => retryAfter !== null && Number(retryAfter) >= 1,
    });
  });

  group("pagination cap", () => {
    const response = http.get(`${BASE_URL}/properties?size=100000`);
    const items = response.status === 200 ? (response.json("content") || []).length : -1;

    check(response, {
      "an absurd page size is still a 200": (r) => r.status === 200,
      // Uncapped, this single request selects and serialises the whole table.
      "at most 50 items are returned": () => items >= 0 && items <= 50,
    });
  });

  group("request body size limit", () => {
    // 2MB of JSON, over the 1MB cap. Refused on Content-Length, before the
    // body is read - the @Size constraints on the DTO only run after Jackson
    // has already parsed the whole thing.
    const twoMegabytes = JSON.stringify({ message: "A".repeat(2 * 1024 * 1024) });
    const response = http.post(`${BASE_URL}/contact`, twoMegabytes, json);

    check(response, {
      "a 2MB body is refused with 413": (r) => r.status === 413,
      "the 413 body is the standard error shape": (r) => r.json("status") === 413,
      "no stack trace in the response": (r) => r.body.indexOf("java.lang") === -1,
    });
  });

  group("actuator exposure", () => {
    const health = http.get(`${BASE_URL.replace(/\/api$/, "")}/actuator/health`);
    const metrics = http.get(`${BASE_URL.replace(/\/api$/, "")}/actuator/metrics`);
    const prometheus = http.get(`${BASE_URL.replace(/\/api$/, "")}/actuator/prometheus`);

    check(health, {
      "health is public": (r) => r.status === 200,
      // show-details: never. An anonymous caller learns UP or DOWN and nothing
      // about the database, the mail host or which component is failing.
      "health reveals no component detail": (r) => r.body.indexOf("components") === -1,
    });
    check(metrics, { "metrics needs a token": (r) => r.status === 401 });
    check(prometheus, { "prometheus needs a token": (r) => r.status === 401 });
  });

  group("error hygiene", () => {
    const badSort = http.get(`${BASE_URL}/properties?sort=password`);
    const badEnum = http.get(`${BASE_URL}/properties?type=NOPE`);

    // A 500 here would both look like a fault and tell the caller they found
    // something real - varying the field name and watching the status change
    // is a free map of the entity.
    check(badSort, {
      "an unknown sort field is a 400, not a 500": (r) => r.status === 400,
      "no stack trace for a bad sort": (r) => r.body.indexOf("java.lang") === -1 && r.json("trace") === undefined,
    });
    check(badEnum, {
      "an invalid enum is a 400, not a 500": (r) => r.status === 400,
      "no stack trace for a bad enum": (r) => r.body.indexOf("java.lang") === -1 && r.json("trace") === undefined,
    });
  });

  group("security headers", () => {
    const response = http.get(`${BASE_URL}/properties`);

    check(response, {
      "X-Content-Type-Options is set": (r) => r.headers["X-Content-Type-Options"] === "nosniff",
      "X-Frame-Options is DENY": (r) => r.headers["X-Frame-Options"] === "DENY",
      "public reads are cacheable": (r) => (r.headers["Cache-Control"] || "").indexOf("max-age=60") !== -1,
      "public reads carry an ETag": (r) => r.headers["Etag"] !== undefined,
    });
  });
  // LAST, deliberately. Proving the spoof does not work means sending
  // hundreds of requests that all land in one bucket, which spends the
  // public allowance - every check after it would then see 429 and report
  // a failure that is really interference from this group.
  group("X-Forwarded-For spoofing does not reset the bucket", () => {
    // The bypass this checks for: rotate a fake leftmost X-Forwarded-For entry
    // and land in a fresh rate-limit bucket every request. The server reads the
    // right-hand hop - the one a proxy wrote, not one a caller can set - so all
    // of these share a bucket and the limit still bites.
    //
    // Two things have to be true on the target for this to hold: it is in
    // x-forwarded-for mode, AND app.client-ip.trusted-proxies names the real
    // proxy range. Without the allowlist the server believes the header from
    // any peer, and a caller reaching the origin directly picks its own bucket
    // - which is the finding this check exists to catch.
    //
    // Skipped by default, because against a target in remote-addr mode the
    // header is ignored outright and a pass would prove nothing.
    const behindProxy = __ENV.BEHIND_PROXY === "yes";
    if (!behindProxy) {
      check(null, { "XFF spoof check skipped (set BEHIND_PROXY=yes on a proxied environment)": () => true });
      return;
    }

    let sawTooManyRequests = false;
    for (let request = 1; request <= 400 && !sawTooManyRequests; request++) {
      const response = http.get(`${BASE_URL}/properties`, {
        headers: { "X-Forwarded-For": `1.2.3.${request % 250}` },
      });
      sawTooManyRequests = response.status === 429;
    }

    check(null, {
      "a spoofed X-Forwarded-For does not bypass the public limit": () => sawTooManyRequests,
    });
  });
}
