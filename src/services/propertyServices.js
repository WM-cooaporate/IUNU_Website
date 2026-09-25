import { publicClient } from "./apiClient";

/**
 * Public, unauthenticated reads. These only ever hit /api/properties, which
 * the backend filters to published rows - the admin listing that includes
 * drafts lives in adminServices and is never called from a public page.
 *
 * They go through publicClient, which never sends the admin token.
 *
 * Pages do not call these directly: they go through the hooks in
 * src/hooks/useProperties.js, which share one cached request across pages.
 */

const propertyServices = {
  /** One page of published properties: `{ content, totalPages, ... }`. */
  getPropertiesPage: async ({ page, size, signal }) => {
    const response = await publicClient.get("/properties", {
      params: { page, size },
      signal,
    });
    return response.data;
  },

  getPropertyById: async (id) => {
    const response = await publicClient.get(`/properties/${id}`);
    return response.data;
  },
};

export default propertyServices;
