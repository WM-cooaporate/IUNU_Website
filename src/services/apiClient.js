import axios from "axios";

/**
 * The single axios instance every service goes through.
 *
 * Centralising it fixes three things that used to be per-service decisions:
 * the base URL is always read from the environment, the bearer token is
 * attached in one place, and a 401 always ends the session instead of leaving
 * the dashboard sitting there with a token the API has already rejected.
 */

export const API_URL =
  import.meta.env.VITE_API_URL || "http://localhost:8080/api";

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
 * Attach the bearer token to every request that has one. Requests made before
 * login (the login call itself, the public forms) simply go out without it.
 */
apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

const SESSION_EXPIRED_EVENT = "iunu:session-expired";

/**
 * A 401 means the token is missing, expired or has been tampered with - there
 * is nothing a retry can fix, so the session is dropped and anything listening
 * (the admin route guard) can send the user back to the login screen. A 403 is
 * left alone: the caller is authenticated, just not allowed here.
 */
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && getAccessToken()) {
      clearSession();
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    return Promise.reject(error);
  }
);

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
