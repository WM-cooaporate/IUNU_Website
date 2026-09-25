import { QueryClient } from "@tanstack/react-query";

/**
 * Retry once, and only when a second attempt can plausibly succeed: the
 * server never answered (network error, timeout - e.g. the API waking from a
 * cold start) or answered 5xx. A 4xx will fail the same way again, so the
 * error state is shown straight away instead of after several silent retries.
 */
const shouldRetry = (failureCount, error) => {
  if (failureCount >= 1) return false;
  const status = error?.response?.status;
  return !status || status >= 500;
};

/**
 * The one cache every page reads server data through. Identical in-flight
 * queries are deduplicated, and a result is reused across components and
 * route changes for `staleTime` - App remounts the route tree on every
 * navigation (key={location.pathname}), so without this each visit refetched.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      refetchOnWindowFocus: false,
      retry: shouldRetry,
    },
  },
});

export default queryClient;
