import apiClient from "./apiClient";

/** Matches spring.servlet.multipart.max-file-size on the backend. */
export const MAX_RESUME_BYTES = 5 * 1024 * 1024;

const careerServices = {
  apply: async (application) => {
    const formData = new FormData();

    Object.entries(application).forEach(([key, value]) => {
      if (value != null && value !== "") formData.append(key, value);
    });

    // Career emails can carry a 5MB attachment, so this one gets longer than
    // the shared default.
    await apiClient.post("/careers", formData, { timeout: 30000 });
  },
};

export default careerServices;
