import axios from "axios";

/**
 * The axios instance for admin and auth calls (publicClient below serves the
 * public site).
 *
 * Centralising it fixes three things that used to be per-service decisions:
 * the base URL is always read from the environment, the bearer token is
 * attached in one place, and a 401 always ends the session instead of leaving
 * the dashboard sitting there with a token the API has already rejected.
 */

const configuredApiUrl = import.meta.env.VITE_API_URL;

// Missing configuration must break API calls, not the whole site. This module
// is imported by every page, so throwing here would run before React mounts
// and leave a blank screen with nothing to read. Log loudly instead and let
// the app render its shell and its existing fetch-failed states.
//
// Only in a production build: in dev the localhost fallback below is the
// intended target, so warning there would just be noise people learn to skip.
if (!configuredApiUrl && import.meta.env.PROD) {
  console.error(
    "VITE_API_URL is not set - API calls will fail. Set it in the hosting " +
      "provider's environment settings and redeploy (Vite inlines it at " +
      "build time, so a rebuild is required for the change to take effect)."
  );
}

/** Used when VITE_API_URL is absent - the local backend in `npm run dev`. */
const FALLBACK_API_URL = "http://localhost:8080/api";

/**
 * The one place the API base URL is decided. Everything else - every service,
 * every component - goes through the axios instance below with a bare path
 * like "/properties", so no other module may add or strip "/api".
 *
 * Two things are normalised here:
 *
 * 1. Trailing slashes are stripped, because axios joins this to paths that
 *    already start with "/": "https://host/api/" + "/properties" would request
 *    "https://host/api//properties", which matches none of the Spring Security
 *    path rules and 401s instead of 200s.
 * 2. "/api" is appended when it is not already the last segment. Every backend
 *    route is served under /api (server.servlet.context-path), so a value like
 *    "https://iunu-api.onrender.com" - the host on its own, which is what a
 *    deploy dashboard gives you if you paste the service URL - would otherwise
 *    send every call to /properties and /auth/login and get a 401 back.
 *    Already-correct values ending in "/api" are left exactly as they are.
 */
const normaliseApiUrl = (value) => {
  const trimmed = String(value ?? "").trim().replace(/\/+$/, "");

  if (!trimmed) return FALLBACK_API_URL;

  return /\/api$/i.test(trimmed) ? trimmed : `${trimmed}/api`;
};

export const API_URL = normaliseApiUrl(configuredApiUrl);

export const REQUEST_TIMEOUT = 15000;

export const TOKEN_KEY = "accessToken";
export const REFRESH_TOKEN_KEY = "refreshToken";
export const USER_KEY = "user";

const safeStorage = {
  get(key) {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(key, value);
    } catch {
      /* private mode / storage disabled - the app still works, just not across reloads */
    }
  },
  remove(key) {
    try {
      localStorage.removeItem(key);
    } catch {
      /* nothing to do */
    }
  },
};

export const getAccessToken = () => safeStorage.get(TOKEN_KEY);
export const getRefreshToken = () => safeStorage.get(REFRESH_TOKEN_KEY);

export const getStoredUser = () => {
  try {
    return JSON.parse(safeStorage.get(USER_KEY) || "null");
  } catch {
    return null;
  }
};

export const storeSession = ({ accessToken, refreshToken, user }) => {
  if (accessToken) safeStorage.set(TOKEN_KEY, accessToken);
  if (refreshToken) safeStorage.set(REFRESH_TOKEN_KEY, refreshToken);
  if (user) safeStorage.set(USER_KEY, JSON.stringify(user));
};

export const clearSession = () => {
  [TOKEN_KEY, REFRESH_TOKEN_KEY, USER_KEY, "userEmail"].forEach(safeStorage.remove);
};

/** True only for a stored ADMIN session. Advisory - the API is the real gate. */
export const isAdminSession = () => {
  const user = getStoredUser();
  return Boolean(getAccessToken() && user?.role === "ADMIN");
};

const apiClient = axios.create({
  baseURL: API_URL,
  timeout: REQUEST_TIMEOUT,
  headers: { Accept: "application/json" },
});

/**
 * For the public site: published property reads and the lead forms. It never
 * carries a token, so a visitor's page behaves the same whether or not an
 * admin has signed in on this browser. That matters beyond tidiness - the API
 * is on another origin, and an Authorization header turns a plain GET into a
 * CORS-preflighted one (an OPTIONS before every new URL). It also has none of
 * the session handling below: a public call can never end the admin session.
 */
export const publicClient = axios.create({
  baseURL: API_URL,
  timeout: REQUEST_TIMEOUT,
  headers: { Accept: "application/json" },
});

/**
 * Attach the bearer token to every admin and auth request that has one.
 * Requests made before login (the login call itself) simply go out without it.
 */
apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

const SESSION_EXPIRED_EVENT = "iunu:session-expired";

/** Auth calls answer 401 for their own reasons (wrong password, bad refresh token); never refresh on those. */
const isAuthCall = (config) => /^\/?auth\//.test(config?.url ?? "");

/**
 * Trades the stored refresh token for a new pair. The backend rotates refresh
 * tokens, so the old one is spent once this succeeds.
 *
 * Goes through publicClient so the call itself can never trigger this
 * interceptor again.
 *
 * Every tab shares localStorage. If another tab rotated the token first, this
 * call is refused (the backend's reuse grace window makes that a plain 401) -
 * but the other tab has already stored a fresh session, so use that instead of
 * signing everyone out.
 */
const refreshSession = async () => {
  const usedRefreshToken = getRefreshToken();
  try {
    const response = await publicClient.post("/auth/refresh", { refreshToken: usedRefreshToken });
    storeSession(response.data);
    return response.data.accessToken;
  } catch (error) {
    const currentRefreshToken = getRefreshToken();
    if (currentRefreshToken && currentRefreshToken !== usedRefreshToken) return getAccessToken();
    throw error;
  }
};

/** One refresh at a time: every request that 401s while it runs waits for the same one. */
let refreshInFlight = null;

/**
 * A 401 on an admin call usually means the 15-minute access token expired.
 * Refresh it once and replay the request, so an admin working in the dashboard
 * is not signed out mid-task. If there is no refresh token, the refresh is
 * refused, or the replayed request 401s again, the session is dropped and
 * anything listening (the admin route guard) sends the user back to the login
 * screen. A 403 is left alone: the caller is authenticated, just not allowed
 * here.
 */
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;

    if (error.response?.status !== 401 || !getAccessToken()) {
      return Promise.reject(error);
    }

    const canRefresh = original && !original._retriedAfterRefresh && !isAuthCall(original) && getRefreshToken();

    if (canRefresh) {
      original._retriedAfterRefresh = true;
      try {
        refreshInFlight ??= refreshSession().finally(() => {
          refreshInFlight = null;
        });
        await refreshInFlight;
      } catch {
        return endSession(error);
      }
      // The request interceptor attaches the new access token. A second 401 on
      // the replay comes back through here, is not refreshed again, and ends
      // the session.
      return apiClient(original);
    }

    return endSession(error);
  }
);

function endSession(error) {
  clearSession();
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  return Promise.reject(error);
}

export const onSessionExpired = (handler) => {
  window.addEventListener(SESSION_EXPIRED_EVENT, handler);
  return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler);
};

/**
 * Turns an axios failure into something worth showing a person. Prefers the
 * API's own message, falls back to the first field error, and distinguishes
 * "the server never answered" from "the server said no".
 */
export const toUserMessage = (error, fallback = "Something went wrong. Please try again.") => {
  const data = error?.response?.data;

  if (data?.fieldErrors?.length) {
    return data.fieldErrors.map((fieldError) => fieldError.message).join(" ");
  }
  if (data?.message) return data.message;

  if (error?.code === "ECONNABORTED") {
    return "The server took too long to respond. Please try again.";
  }
  if (!error?.response) {
    return "We could not reach the server. Check your connection and try again.";
  }
  if (error.response.status === 429) {
    return "Too many attempts. Please wait a moment and try again.";
  }
  if (error.response.status === 413) {
    return "That file is too large. Please choose a smaller one.";
  }
  return fallback;
};

export default apiClient;
