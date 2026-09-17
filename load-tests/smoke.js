import { sleep } from "k6";
import { thresholds } from "./lib/config.js";
import { browseListing, viewDetail, loadImage } from "./lib/journeys.js";

/**
 * One virtual user, thirty seconds. Not a load test - a check that the target
 * is up, reachable and answering correctly before anything heavier is pointed
 * at it.
 *
 * Run this first, every time. A stress run against a misconfigured BASE_URL
 * produces a beautiful graph of 404s.
 */
export const options = {
  vus: 1,
  duration: "30s",
  thresholds: {
    ...thresholds,
    // Nothing may fail in a smoke run. One failure means the environment is
    // wrong, and there is no point continuing.
    http_req_failed: ["rate==0"],
  },
};

export default function () {
  browseListing();
  viewDetail();
  loadImage();

  // Paced, not flat out. Without this one VU issues ~3000 requests a second
  // against a local server, trips the 300/min public rate limit, and reports a
  // 99% failure rate - which says nothing about whether the target is healthy
  // and everything about how fast the loop runs.
  sleep(1);
}
