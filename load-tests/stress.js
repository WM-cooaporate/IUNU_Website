import { thresholds } from "./lib/config.js";
import { visitorJourney } from "./lib/journeys.js";

/**
 * Steps up until something breaks, to find out WHAT breaks first.
 *
 * The number this produces is not "the app handles N users" - it is "at N
 * users, the bottleneck is X". X is what you act on. Watch
 * hikaricp.connections.pending and http.server.requests p99 during the run;
 * whichever moves first is the constraint, and raising VUs past that point
 * only measures how a saturated system fails.
 *
 * Thresholds are declared but NOT abort-on-fail: crossing them is the result,
 * not an error.
 */
export const options = {
  stages: [
    { duration: "2m", target: 100 },
    { duration: "2m", target: 250 },
    { duration: "2m", target: 500 },
    { duration: "2m", target: 750 },
    { duration: "2m", target: 1000 },
    { duration: "2m", target: 0 },
  ],
  thresholds,
};

export default function () {
  visitorJourney();
}
