import { thresholds } from "./lib/config.js";
import { visitorJourney } from "./lib/journeys.js";

/**
 * Zero to 300 users in ten seconds - a link going around, or the cheap end of
 * a flood.
 *
 * Recovery is what is being measured, not the peak. An app that degrades during
 * the spike and is healthy a minute after it ends is behaving correctly; one
 * that is still slow after the load is gone has a queue, a pool or a thread
 * leak that does not drain, and that is the finding.
 */
export const options = {
  stages: [
    { duration: "10s", target: 300 }, // spike
    { duration: "1m", target: 300 },  // hold
    { duration: "10s", target: 0 },   // drop
    { duration: "2m", target: 5 },    // does it recover?
  ],
  thresholds,
};

export default function () {
  visitorJourney();
}
