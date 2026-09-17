import { thresholds } from "./lib/config.js";
import { visitorJourney } from "./lib/journeys.js";

/**
 * Fifty users for an hour. Modest load, long duration - the only shape that
 * finds the problems that need time rather than pressure.
 *
 * What to look for afterwards, none of which a five-minute run can show:
 *   - jvm.memory.used climbing across the whole run and not returning after GC
 *   - hikaricp.connections.active drifting up (a connection leak)
 *   - cache.size pinned at its maximum with a falling hit rate
 *   - p95 at minute 55 noticeably worse than at minute 5
 */
export const options = {
  stages: [
    { duration: "2m", target: 50 },
    { duration: "60m", target: 50 },
    { duration: "2m", target: 0 },
  ],
  thresholds,
};

export default function () {
  visitorJourney();
}
