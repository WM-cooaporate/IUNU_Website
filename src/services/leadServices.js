import { publicClient } from "./apiClient";

/**
 * The public lead-capture forms: contact, quote request and newsletter.
 *
 * Each payload is shaped to the backend DTO it posts to, so a submission
 * either succeeds or comes back with field errors the form can show. The
 * enum-ish values (project, spaceType) are lowercased because
 * QuoteRequestDto pins them to an exact lowercase set.
 */

const trim = (value) => (typeof value === "string" ? value.trim() : "");

const leadServices = {
  /** POST /api/contact -> ContactRequest */
  submitContact: async ({ firstName, lastName, phone, email, message }) => {
    const response = await publicClient.post("/contact", {
      firstName: trim(firstName),
      lastName: trim(lastName),
      phone: trim(phone),
      email: trim(email),
      message: trim(message),
    });
    return response.data;
  },

  /** POST /api/quotes -> QuoteRequestDto */
  submitQuote: async ({ name, phone, city, email, project, whatsapp, spaceType }) => {
    const response = await publicClient.post("/quotes", {
      name: trim(name),
      phone: trim(phone),
      city: trim(city),
      email: trim(email),
      project: trim(project).toLowerCase(),
      // The backend accepts an empty string here but not null.
      whatsapp: trim(whatsapp),
      spaceType: trim(spaceType).toLowerCase(),
    });
    return response.data;
  },

  /** POST /api/newsletter -> NewsletterRequest */
  subscribeNewsletter: async (email) => {
    const response = await publicClient.post("/newsletter", { email: trim(email) });
    return response.data;
  },
};

export default leadServices;
