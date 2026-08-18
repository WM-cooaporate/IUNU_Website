import axios from "axios";

const API_URL = "http://localhost:8080/api";

const propertyServices = {
    getProperties: async() => {
        const response = await axios.get(
            `${API_URL}/properties`
        );

        return response.data;
    },

    getPropertyById: async(id) => {
        const response = await axios.get(
            `${API_URL}/properties/${id}`
        );

        return response.data;
    },
};

export default propertyServices;