import axios from "axios";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
const REQUEST_TIMEOUT = 7000;

const getAuthHeaders = () => {
    const token = localStorage.getItem("accessToken");

    return {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
    };
};

const adminServices = {
    getProperties: async() => {
        const response = await axios.get(
            `${API_URL}/properties/admin`, {
                headers: getAuthHeaders(),
                timeout: REQUEST_TIMEOUT,
            }
        );

        return response.data;
    },

    getPropertyById: async(id) => {
        const response = await axios.get(
            `${API_URL}/properties/admin/${id}`, {
                headers: getAuthHeaders(),
                timeout: REQUEST_TIMEOUT,
            }
        );

        return response.data;
    },

    createProperty: async(propertyData) => {
        const response = await axios.post(
            `${API_URL}/properties`,
            propertyData, {
                headers: getAuthHeaders(),
                timeout: REQUEST_TIMEOUT,
            }
        );

        return response.data;
    },

    updateProperty: async(id, propertyData) => {
        const response = await axios.put(
            `${API_URL}/properties/${id}`,
            propertyData, {
                headers: getAuthHeaders(),
                timeout: REQUEST_TIMEOUT,
            }
        );

        return response.data;
    },

    deleteProperty: async(id) => {
        await axios.delete(
            `${API_URL}/properties/${id}`, {
                headers: getAuthHeaders(),
                timeout: REQUEST_TIMEOUT,
            }
        );
    },
};

export default adminServices;
