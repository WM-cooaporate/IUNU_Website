import http from "k6/http";
import { BASE_URL } from "./lib/config.js";

/**
 * One member of the lab's simulated botnet: a handful of wrong passwords
 * against one account, from this container's own address. security/run-lab.sh
 * starts several of these at once, each on its own address, so together they
 * cross the account-wide lockout threshold while none of them exceeds the
 * per-address one - the distributed attack the ACCOUNT_LOCKED safety net
 * exists for.
 *
 * <p>Local lab only, like attack.js.
 */

const LAB_HOSTS = ["localhost", "127.0.0.1", "proxy", "backend", "frontend"];
const targetHost = BASE_URL.replace(/^[a-z]+:\/\//, "").split(/[/:]/)[0];
if (!LAB_HOSTS.includes(targetHost)) {
  throw new Error(`botnet.js only runs against the local lab. Got: ${targetHost}`);
}
if (!__ENV.VICTIM_EMAIL) {
  throw new Error("Set VICTIM_EMAIL (security/run-lab.sh does).");
}

export const options = { vus: 1, iterations: 1 };

export default function () {
  const attempts = Number(__ENV.ATTEMPTS || 5);
  for (let i = 0; i < attempts; i++) {
    http.post(`${BASE_URL}/auth/login`,
      JSON.stringify({ email: __ENV.VICTIM_EMAIL, password: `botnet-guess-${__VU}-${i}` }),
      { headers: { "Content-Type": "application/json" } });
  }
}
