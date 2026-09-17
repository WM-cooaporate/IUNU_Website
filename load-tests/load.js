import { thresholds } from "./lib/config.js";
import { visitorJourney } from "./lib/journeys.js";

/**
 * Expected peak traffic, held long enough to see whether it is sustainable.
 *
 * The five-minute hold is the point. A ramp alone shows the app can absorb a
 * burst; only a hold surfaces the slow failures - a connection pool filling up,
 * a cache that stops helping once its eviction rate matches its fill rate,
 * memory that climbs and never comes back down.
 */
export const options = {
  stages: [
    { duration: "2m", target: 200 }, // ramp
    { duration: "5m", target: 200 }, // hold
    { duration: "1m", target: 0 },   // ramp down
  ],
  thresholds,
};

export default function () {
  visitorJourney();
}
