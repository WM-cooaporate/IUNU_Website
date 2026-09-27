import apiClient from "./apiClient";

/**
 * Public, unauthenticated reads. These only ever hit /api/properties, which
 * the backend filters to published rows - the admin listing that includes
 * drafts lives in adminServices and is never called from a public page.
 */

const PAGE_SIZE = 50;
/** Stops a malformed totalPages from turning pagination into an infinite loop. */
const MAX_PAGES = 100;

const propertyServices = {
  getProperties: async () => {
    const response = await apiClient.get("/properties");
    return response.data;
  },

  getAllProperties: async () => {
    const properties = [];
    let page = 0;
    let totalPages;

    do {
      const response = await apiClient.get("/properties", {
        params: { page, size: PAGE_SIZE },
      });

      properties.push(...(response.data?.content || []));
      totalPages = response.data?.totalPages || 1;
      page += 1;
    } while (page < totalPages && page < MAX_PAGES);

    return properties;
  },

  getPropertyById: async (id) => {
    const response = await apiClient.get(`/properties/${id}`);
    return response.data;
  },
};

export default propertyServices;
