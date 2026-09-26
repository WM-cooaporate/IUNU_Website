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
  image.src = PLACEHOLDER_IMAGE;
}
