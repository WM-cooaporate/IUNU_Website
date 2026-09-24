/**
 * Shrinks a photo in the browser before it is uploaded: long edge to 2000px,
 * re-encoded as WebP (JPEG where the browser cannot write WebP).
 *
 * Why here and not on the server: a phone photo is 3-12MB and the admin is
 * often on mobile data, uploading to a 0.1-CPU instance that may be waking up.
 * A 300KB WebP arrives in a second and never hits the 5MB per-file cap.
 *
 * Re-encoding through a canvas also drops every byte of metadata - EXIF, and
 * with it the GPS position of the admin's phone - which would otherwise be
 * published with the image.
 */

export const HEIC_MESSAGE =
  "iPhone HEIC photos aren't supported. Set Camera → Formats → Most Compatible, or export as JPG.";
export const UNSUPPORTED_MESSAGE = "Only JPG, PNG and WebP images are supported.";

const ALLOWED_TYPES = ["image/jpeg", "image/png", "image/webp"];
const ALLOWED_EXTENSION = /\.(jpe?g|png|webp)$/i;
const HEIC_EXTENSION = /\.(heic|heif)$/i;
const KEEP_ORIGINAL_MAX_BYTES = 1.5 * 1024 * 1024;

const basename = (name) => (name || "image").replace(/\.[^.]*$/, "") || "image";

const toBlob = (canvas, type, quality) =>
  new Promise((resolve) => {
    canvas.toBlob((blob) => resolve(blob), type, quality);
  });

/**
 * Decodes with orientation applied, so a portrait phone photo is not drawn
 * sideways. createImageBitmap first; an <img> where that is missing or throws
 * (older Safari rejects the options object).
 */
const decode = async (file) => {
  if (typeof createImageBitmap === "function") {
    try {
      const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
      return { source: bitmap, width: bitmap.width, height: bitmap.height, release: () => bitmap.close?.() };
    } catch {
      /* fall through to <img> */
    }
  }

  // A data: URL, not URL.createObjectURL: the site's CSP allows data: images
  // and not blob: ones, and it applies to an off-screen <img> too.
  const dataUrl = await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
  const image = new Image();
  image.decoding = "async";
  image.src = dataUrl;
  try {
    await image.decode();
  } catch {
    throw new Error("This image could not be opened. It may be damaged, or in a format this browser cannot read.");
  }
  return {
    source: image,
    width: image.naturalWidth,
    height: image.naturalHeight,
    release: () => {
      image.src = "";
    },
  };
};

/**
 * True when the file carries EXIF or XMP (JPEG APP1 "Exif", PNG eXIf, WebP
 * EXIF/XMP chunks, or an XMP packet anywhere). Such a file is never uploaded
 * as-is, even when re-encoding makes it bigger: stripping GPS wins over bytes.
 */
const carriesMetadata = async (file) => {
  const bytes = new Uint8Array(await file.arrayBuffer());
  const text = new TextDecoder("latin1").decode(bytes);
  return text.includes("Exif\u0000\u0000") || text.includes("eXIf") || text.includes("EXIF")
    || text.includes("XMP ") || text.includes("http://ns.adobe.com/xap/1.0/");
};

/**
 * Returns { file, width, height, originalBytes, finalBytes } or throws
 * Error(userMessage). Call it for one file at a time: decoding several 48MP
 * photos at once is how mobile Safari runs out of memory.
 */
export async function prepareImage(file, { maxEdge = 2000, quality = 0.82 } = {}) {
  const type = (file?.type || "").toLowerCase();
  const name = file?.name || "image";

  if (type === "image/heic" || type === "image/heif" || HEIC_EXTENSION.test(name)) {
    throw new Error(HEIC_MESSAGE);
  }
  // Some Android pickers send no type at all; fall back to the extension then.
  if (!ALLOWED_TYPES.includes(type) && !(type === "" && ALLOWED_EXTENSION.test(name))) {
    throw new Error(UNSUPPORTED_MESSAGE);
  }

  const decoded = await decode(file);
  const canvas = document.createElement("canvas");
  try {
    const scale = Math.min(1, maxEdge / Math.max(decoded.width, decoded.height));
    const width = Math.max(1, Math.round(decoded.width * scale));
    const height = Math.max(1, Math.round(decoded.height * scale));

    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("This browser could not process the image. Try another browser.");
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    context.drawImage(decoded.source, 0, 0, width, height);

    let blob = await toBlob(canvas, "image/webp", quality);
    let extension = "webp";
    // Safari before 14 cannot encode WebP and silently hands back a PNG.
    if (!blob || blob.type !== "image/webp") {
      // JPEG has no transparency: paint white behind the image, not black.
      context.globalCompositeOperation = "destination-over";
      context.fillStyle = "#ffffff";
      context.fillRect(0, 0, width, height);
      blob = await toBlob(canvas, "image/jpeg", 0.85);
      extension = "jpg";
    }
    if (!blob) throw new Error("This image could not be processed. Try a different photo.");

    if (
      blob.size > file.size
      && scale === 1
      && file.size <= KEEP_ORIGINAL_MAX_BYTES
      && ALLOWED_TYPES.includes(type)
      && !(await carriesMetadata(file))
    ) {
      // Already small, and re-encoding would only make it bigger.
      return { file, width, height, originalBytes: file.size, finalBytes: file.size };
    }

    const prepared = new File([blob], `${basename(name)}.${extension}`, { type: blob.type, lastModified: Date.now() });
    return { file: prepared, width, height, originalBytes: file.size, finalBytes: prepared.size };
  } finally {
    decoded.release();
    // Hand the pixel buffer back now rather than at the next GC - iOS caps
    // total canvas memory per tab.
    canvas.width = 0;
    canvas.height = 0;
  }
}

/**
 * A small data: URL preview of an (already prepared) image. data: rather than
 * blob:, because the site's Content-Security-Policy allows data: images and
 * not blob: ones.
 */
export async function thumbnailDataUrl(file, maxEdge = 320) {
  const decoded = await decode(file);
  const canvas = document.createElement("canvas");
  try {
    const scale = Math.min(1, maxEdge / Math.max(decoded.width, decoded.height));
    canvas.width = Math.max(1, Math.round(decoded.width * scale));
    canvas.height = Math.max(1, Math.round(decoded.height * scale));
    canvas.getContext("2d")?.drawImage(decoded.source, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL("image/jpeg", 0.7);
  } finally {
    decoded.release();
    canvas.width = 0;
    canvas.height = 0;
  }
}
