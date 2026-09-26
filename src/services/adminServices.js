import apiClient, { API_URL } from "./apiClient";

/**
 * Admin calls. The bearer token and 401 handling live in apiClient, so nothing
 * here builds headers by hand.
 */

const PAGE_SIZE = 50;
/** Stops a malformed totalPages from turning pagination into an infinite loop. */
const MAX_PAGES = 100;

/**
 * Uploads only. Everything else keeps apiClient's 15s: a slow upload on mobile
 * data, or one that lands while the free Render instance is waking up, needs
 * far longer than a JSON call ever should.
 */
const UPLOAD_TIMEOUT = 120000;
const UPLOAD_RETRY_DELAY = 2000;
const WAKE_TIMEOUT = 60000;

const wait = (ms) => new Promise((resolve) => { window.setTimeout(resolve, ms); });

/**
 * Worth one more try: no response at all (network drop, timeout) or a 5xx
 * (the server failed or is still waking up). A 4xx is the
 * server saying no - a retry would only get the same answer.
 */
const isRetryable = (error) => !error?.response || error.response.status >= 500;

const postImage = (file, onProgress) => {
  const formData = new FormData();
  formData.append("files", file);
  // Content-Type is deliberately left unset so the browser adds the
  // multipart boundary itself.
  return apiClient.post("/properties/images", formData, {
    timeout: UPLOAD_TIMEOUT,
    onUploadProgress: (event) => {
      if (onProgress && event.total) onProgress(Math.min(100, Math.round((event.loaded * 100) / event.total)));
    },
  });
};

const adminServices = {
  getProperties: async () => {
    const response = await apiClient.get("/properties/admin");
    return response.data;
  },

  getAllProperties: async () => {
    const properties = [];
    let page = 0;
    let totalPages;

    do {
      const response = await apiClient.get("/properties/admin", {
        params: { page, size: PAGE_SIZE },
      });

      properties.push(...(response.data?.content || []));
      totalPages = response.data?.totalPages || 1;
      page += 1;
    } while (page < totalPages && page < MAX_PAGES);

    return properties;
  },

  /**
   * The old all-files-in-one-request upload. Kept for any other caller; the
   * dashboard uses uploadPropertyImage, one file per request.
   */
  uploadPropertyImages: async (files) => {
    const formData = new FormData();
    files.forEach((file) => formData.append("files", file));

    // Content-Type is deliberately left unset so the browser adds the
    // multipart boundary itself.
    const response = await apiClient.post("/properties/images", formData, { timeout: UPLOAD_TIMEOUT });
    return response.data;
  },

  /**
   * Uploads one (already resized) image and returns its URL. onProgress gets
   * 0-100. Retries once, after 2s, on a network error, timeout or 5xx.
   */
  uploadPropertyImage: async (file, onProgress) => {
    let response;
    try {
      response = await postImage(file, onProgress);
    } catch (error) {
      if (!isRetryable(error)) throw error;
      onProgress?.(0);
      await wait(UPLOAD_RETRY_DELAY);
      response = await postImage(file, onProgress);
    }
    const url = response.data?.[0];
    if (!url) throw new Error("The server did not return an image address. Please try again.");
    return url;
  },

  /**
   * Pokes the backend so a sleeping free-plan instance starts waking up while
   * the admin is still choosing photos. The liveness probe is anonymous and
   * cheap; no-cors, because only the request matters, not the answer. Never
   * throws.
   */
  wakeBackend: async () => {
    const origin = API_URL.replace(/\/api$/i, "");
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), WAKE_TIMEOUT);
    try {
      await fetch(`${origin}/actuator/health/liveness`, {
        mode: "no-cors",
        cache: "no-store",
        credentials: "omit",
        signal: controller.signal,
      });
    } catch {
      /* ignored - the uploads themselves report real failures */
    } finally {
      window.clearTimeout(timeoutId);
    }
  },

  getPropertyById: async (id) => {
    const response = await apiClient.get(`/properties/admin/${id}`);
    return response.data;
  },

  createProperty: async (propertyData) => {
    const response = await apiClient.post("/properties", propertyData);
    return response.data;
  },

  updateProperty: async (id, propertyData) => {
    const response = await apiClient.put(`/properties/${id}`, propertyData);
    return response.data;
  },

  deleteProperty: async (id) => {
    await apiClient.delete(`/properties/${id}`);
  },

  /** Translates the open form's English into Arabic without saving anything. */
  previewTranslation: async (fields) => {
    const response = await apiClient.post("/admin/translations/preview", fields);
    return response.data;
  },

  /**
   * The admin audit trail, newest first. params: { page, size, action } -
   * action is one of the backend's AuditAction names, or omitted for all.
   */
  getAuditLog: async (params = {}) => {
    const response = await apiClient.get("/admin/audit-log", { params });
    return response.data;
  },

  /** Fills the Arabic of existing projects that have none yet. */
  backfillTranslations: async () => {
    const response = await apiClient.post("/admin/translations/properties/backfill");
    return response.data;
  },
};

export default adminServices;
