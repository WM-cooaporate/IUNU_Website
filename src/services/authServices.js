import apiClient, {
  clearSession,
  getRefreshToken,
  getStoredUser,
  isAdminSession,
  storeSession,
} from "./apiClient";

const authServices = {
  login: async (loginData) => {
    const response = await apiClient.post("/auth/login", loginData);

    storeSession({
      accessToken: response.data.accessToken,
      refreshToken: response.data.refreshToken,
      user: response.data.user,
    });

    return { success: true, ...response.data };
  },

  register: async (registerData) => {
    const response = await apiClient.post("/auth/register", registerData);
    return response.data;
  },

  /**
   * Revokes the refresh token server-side so a stolen copy is useless, then
   * clears local state. The local clear happens either way - a failed network
   * call must never leave someone appearing to still be signed in.
   */
  logout: async () => {
    const refreshToken = getRefreshToken();

    try {
      if (refreshToken) {
        await apiClient.post("/auth/logout", { refreshToken });
      }
    } catch {
      /* best effort - the token expires on its own */
    } finally {
      clearSession();
    }
  },

  getCurrentUser: getStoredUser,
  isAdmin: isAdminSession,
};

export default authServices;
