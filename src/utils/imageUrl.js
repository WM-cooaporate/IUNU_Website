import { API_URL } from "../services/apiClient";

/**
 * Responsive delivery for images stored on Cloudinary.
 *
 * Only this fixed set of widths is ever requested, so Cloudinary derives at
 * most four variants per photo - which keeps the free plan's transformation
 * quota predictable no matter how many screen sizes visit the site.
 */
const WIDTHS = [400, 800, 1200, 1600];
const HOST = "res.cloudinary.com";
const MARKER = "/image/upload/";
/** A path segment that is already a transformation, e.g. "f_auto,w_800" or "c_fill,h_300". */
const TRANSFORMATION_SEGMENT = /^[a-z]{1,3}_[^/]*$/;

const snapWidth = (width) => WIDTHS.find((candidate) => candidate >= width) ?? WIDTHS[WIDTHS.length - 1];

/**
 * Cloudinary URL with f_auto,q_auto,c_limit,w_<w> inserted after /image/upload/,
 * the width snapped up to the nearest of 400/800/1200/1600. Any other URL -
 * a legacy /uploads/ file, a pasted external link, a bundled placeholder - is
 * returned unchanged.
 */
export function imageUrl(url, width) {
  if (!url || typeof url !== "string") return url;

  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return url;
  }
  if (parsed.protocol !== "https:" || parsed.hostname !== HOST || parsed.port !== "") return url;

  const markerIndex = parsed.pathname.indexOf(MARKER);
  if (markerIndex < 0) return url;

  const head = parsed.pathname.slice(0, markerIndex + MARKER.length);
  const rest = parsed.pathname.slice(markerIndex + MARKER.length);
  // Already transformed (someone pasted a delivery URL): leave it as it is
  // rather than stacking a second transformation on top.
  if (TRANSFORMATION_SEGMENT.test(rest.split("/")[0])) return url;

  return `${parsed.origin}${head}f_auto,q_auto,c_limit,w_${snapWidth(width)}/${rest}${parsed.search}`;
}

const API_ORIGIN = (() => {
  try {
    return new URL(API_URL).origin;
  } catch {
    return "";
  }
})();

/**
 * True for an image URL the site's Content-Security-Policy lets the browser
 * load: Cloudinary, or a legacy /uploads/ file on the API's own origin. The
 * CSP img-src names only these (plus the site itself), so a URL from anywhere
 * else would be saved and then render as the placeholder on every page.
 * Keep in step with img-src in render.yaml, vercel.json and public/_headers.
 */
export function isAllowedImageUrl(url) {
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return false;
  }
  if (parsed.protocol === "https:" && parsed.hostname === HOST && parsed.port === "") return true;
  return API_ORIGIN !== "" && parsed.origin === API_ORIGIN;
}

/** "url-400 400w, url-800 800w, ..." for a Cloudinary URL; "" for anything else. */
export function imageSrcSet(url) {
  if (!url || imageUrl(url, WIDTHS[0]) === url) return "";
  return WIDTHS.map((width) => `${imageUrl(url, width)} ${width}w`).join(", ");
}

/** Bundled image shown when a stored one fails to load. */
export const PLACEHOLDER_IMAGE = "/images/hh.jpg";

/**
 * onError handler: swaps a broken image for the placeholder instead of the
 * browser's broken-image icon. Marks the element first, so a placeholder that
 * also fails cannot loop.
 */
export function showPlaceholderOnError(event) {
  const image = event.currentTarget;
  if (!image || image.dataset.fallback === "true") return;
  image.dataset.fallback = "true";
  // The srcset would otherwise keep winning over src.
  image.removeAttribute("srcset");
  image.removeAttribute("sizes");
  image.src = PLACEHOLDER_IMAGE;
}
