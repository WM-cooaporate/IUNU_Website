import axios from "axios";

const API_URL =
    import.meta.env.VITE_API_URL || "http://localhost:8080/api";
const REQUEST_TIMEOUT = 5000;

const propertyServices = {
    getProperties: async() => {
        const response = await axios.get(`${API_URL}/properties`, {
            timeout: REQUEST_TIMEOUT,
        });

        return response.data;
    },

    getAllProperties: async() => {
        const properties = [];
        let page = 0;
        while (true) {
            const response = await axios.get(`${API_URL}/properties`, {
                params: { page, size: 50 },
                timeout: REQUEST_TIMEOUT,
            });
            const data = response.data;

            properties.push(...(data.content || []));
            page += 1;

            if (page >= (data.totalPages || 1)) break;
        }

        return properties;
    },

    getPropertyById: async(id) => {
        const response = await axios.get(`${API_URL}/properties/${id}`, {
            timeout: REQUEST_TIMEOUT,
        });

        return response.data;
    },
};

export default propertyServices;
