import axios from "axios";

const API_URL = "http://localhost:8080/api";

const authServices = {
    login: async(loginData) => {
        const response = await axios.post(
            `${API_URL}/auth/login`,
            loginData
        );

        localStorage.setItem(
            "accessToken",
            response.data.accessToken
        );

        localStorage.setItem(
            "refreshToken",
            response.data.refreshToken
        );

        localStorage.setItem(
            "user",
            JSON.stringify(response.data.user)
        );

        return {
            success: true,
            ...response.data,
        };
    },

    register: async(registerData) => {
        const response = await axios.post(
            `${API_URL}/auth/register`,
            registerData
        );

        return response.data;
    },

    logout: () => {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
        localStorage.removeItem("user");
        localStorage.removeItem("userEmail");
    },
};

export default authServices;