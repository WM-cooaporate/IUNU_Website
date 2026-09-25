import { useQuery } from "@tanstack/react-query";
import propertyServices from "../services/propertyServices";

/**
 * The public catalogue size every properties section asks for. Home and
 * Projects both render the whole published list, so they share this one
 * request: whichever page loads first fills the cache for the other.
 */
export const CATALOGUE_PAGE_SIZE = 50;

export const propertyKeys = {
  list: (page, size) => ["properties", "list", { page, size }],
};

/**
 * Published properties, one page. Primitives only in the key, so it is stable
 * across renders and every caller with the same page/size shares one request.
 */
export function useProperties({ page = 0, size = CATALOGUE_PAGE_SIZE } = {}) {
  return useQuery({
    queryKey: propertyKeys.list(page, size),
    queryFn: ({ signal }) => propertyServices.getPropertiesPage({ page, size, signal }),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}
