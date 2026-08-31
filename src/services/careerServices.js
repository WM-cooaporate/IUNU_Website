import axios from "axios";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

const careerServices = {
    apply: async(application) => {
        const formData = new FormData();
        Object.entries(application).forEach(([key, value]) => {
            if (value != null) formData.append(key, value);
        });
        await axios.post(`${API_URL}/careers`, formData, { timeout: 15000 });
    },
};

export default careerServices;
