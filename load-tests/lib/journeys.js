import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, PROPERTY_TYPES, params } from "./config.js";

/**
 * What a visitor actually does, in roughly the proportions they do it: mostly
 * listing pages, some detail pages, the occasional image.
 *
 * <p>Two rules this file exists to enforce.
 *
 * <p><strong>No writes.</strong> The load, stress, spike and soak scripts never
 * submit the contact, quote, careers or newsletter forms. Those write to the
 * client's leads table - a load test that posts them fills it with thousands of
 * fake enquiries somebody then has to delete by hand, and trips the write
 * limiter so real submissions start failing. Only abuse.js touches a write
 * path, once, on purpose.
 *
 * <p><strong>No hardcoded ids.</strong> Detail requests use ids collected from
 * the listing responses. A hardcoded id is a 404 on any environment but the one
 * it was written against, and 404s are cheap - a run full of them measures
 * nothing and still reports a healthy error rate if you forget to check.
 */

const THINK_TIME_MIN = 1;
const THINK_TIME_MAX = 4;

/** Ids seen in listing responses, reused for detail requests. */
let knownIds = [];

function randomOf(values) {
  return values[Math.floor(Math.random() * values.length)];
}

function thinkTime() {
  sleep(THINK_TIME_MIN + Math.random() * (THINK_TIME_MAX - THINK_TIME_MIN));
}

/** A listing page, sometimes filtered by type, over the first few pages. */
export function browseListing() {
  const page = Math.floor(Math.random() * 4);
  // A quarter of visits filter by type; the rest browse everything.
  const type = Math.random() < 0.25 ? `&type=${randomOf(PROPERTY_TYPES)}` : "";

  const response = http.get(`${BASE_URL}/properties?page=${page}&size=12${type}`, params("list"));

  check(response, {
    "listing is 200": (r) => r.status === 200,
    "listing is a page": (r) => r.json("content") !== undefined,
    // The cap the API applies, asserted on every run rather than only in the
    // unit tests: a regression here is a denial-of-service hole, not a bug.
    "listing respects the page-size cap": (r) => (r.json("content") || []).length <= 50,
  });

  if (response.status === 200) {
    const ids = (response.json("content") || []).map((property) => property.id);
    if (ids.length) {
      knownIds = ids;
    }
  }
  return response;
}

/** A detail page, for an id the listing actually returned. */
export function viewDetail() {
  if (!knownIds.length) {
    browseListing();
    if (!knownIds.length) {
      return null; // empty database - nothing to view, and that is not a failure
    }
  }

  const response = http.get(`${BASE_URL}/properties/${randomOf(knownIds)}`, params("detail"));
  check(response, { "detail is 200": (r) => r.status === 200 });
  return response;
}

/**
 * A cover image. Served by the static handler rather than a controller, so it
 * exercises a different path - and on a host with an ephemeral filesystem it is
 * where "the images vanished after the deploy" shows up first.
 */
export function loadImage() {
  const listing = browseListing();
  if (listing.status !== 200) {
    return null;
  }
  const withCover = (listing.json("content") || []).filter((property) => property.coverImageUrl);
  if (!withCover.length) {
    return null;
  }

  const response = http.get(randomOf(withCover).coverImageUrl, params("image"));
  check(response, { "image is 200": (r) => r.status === 200 });
  return response;
}

/** One visitor's iteration: 70% listing, 25% detail, 5% image, then a pause. */
export function visitorJourney() {
  const roll = Math.random();
  if (roll < 0.7) {
    browseListing();
  } else if (roll < 0.95) {
    viewDetail();
  } else {
    loadImage();
  }
  thinkTime();
}
