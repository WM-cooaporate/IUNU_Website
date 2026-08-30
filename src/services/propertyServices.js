import axios from "axios";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
const REQUEST_TIMEOUT = 5000;

const propertyServices = {
    getProperties: async() => {
        const response = await axios.get(
            `${API_URL}/properties`,
            { timeout: REQUEST_TIMEOUT }
        );

        return response.data;
    },

    getPropertyById: async(id) => {
        const response = await axios.get(
            `${API_URL}/properties/${id}`,
            { timeout: REQUEST_TIMEOUT }
        );

        return response.data;
    },
};

export default propertyServices;
