import apiClient from "./apiClient";

/**
 * Admin calls. The bearer token and 401 handling live in apiClient, so nothing
 * here builds headers by hand.
 */

const PAGE_SIZE = 50;
/** Stops a malformed totalPages from turning pagination into an infinite loop. */
const MAX_PAGES = 100;

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

  uploadPropertyImages: async (files) => {
    const formData = new FormData();
    files.forEach((file) => formData.append("files", file));

    // Content-Type is deliberately left unset so the browser adds the
    // multipart boundary itself.
    const response = await apiClient.post("/properties/images", formData);
    return response.data;
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

  /** Fills the Arabic of existing projects that have none yet. */
  backfillTranslations: async () => {
    const response = await apiClient.post("/admin/translations/properties/backfill");
    return response.data;
  },
};

export default adminServices;
