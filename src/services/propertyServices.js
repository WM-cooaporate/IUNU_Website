import apiClient from "./apiClient";

/**
 * Public, unauthenticated reads. These only ever hit /api/properties, which
 * the backend filters to published rows - the admin listing that includes
 * drafts lives in adminServices and is never called from a public page.
 *
 * `skipAuth` keeps a stored admin token off these requests so the browser does
 * not preflight them (see the request interceptor in apiClient).
 *
 * Pages do not call these directly: they go through the hooks in
 * src/hooks/useProperties.js, which share one cached request across pages.
 */

const propertyServices = {
  /** One page of published properties: `{ content, totalPages, ... }`. */
  getPropertiesPage: async ({ page, size, signal }) => {
    const response = await apiClient.get("/properties", {
      params: { page, size },
      signal,
      skipAuth: true,
    });
    return response.data;
  },

  getPropertyById: async (id) => {
    const response = await apiClient.get(`/properties/${id}`, { skipAuth: true });
    return response.data;
  },
};

export default propertyServices;
