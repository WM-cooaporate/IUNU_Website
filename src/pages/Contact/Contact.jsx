import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import propertyServices from "../../services/propertyServices";
import { useLanguage } from "../../i18n/LanguageContext";

import "./Contact.css";

function Contact() {
  const { t } = useLanguage();

  const [searchParams] = useSearchParams();
  const propertyId = searchParams.get("property");

  const [property, setProperty] = useState(null);
  const [propertyLoading, setPropertyLoading] = useState(false);

  const [formData, setFormData] = useState({
    firstName: "",
    lastName: "",
    phone: "",
    email: "",
    message: "",
  });

  useEffect(() => {
    if (!propertyId) return;

    const loadProperty = async () => {
      try {
        setPropertyLoading(true);

        const data =
          await propertyServices.getPropertyById(propertyId);

        setProperty(data);
      } catch (error) {
        console.error("Contact property loading error:", error);
      } finally {
        setPropertyLoading(false);
      }
    };

    loadProperty();
  }, [propertyId]);

  const handleChange = (event) => {
    const { name, value } = event.target;

    setFormData((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleSubmit = (event) => {
    event.preventDefault();

    console.log("Contact form:", {
      ...formData,
      propertyId,
      propertyTitle: property?.title || null,
    });
  };

  return (
    <div className="contact-page">
      <Navbar />

      <main>
        {/* ================= HERO ================= */}

        <section className="contact-hero">
          <div className="contact-hero-content">

            <span className="contact-eyebrow">
              {t("CONTACT US")}
            </span>

            <h1>
              {t("Get In Touch")}
            </h1>

            <p>
              {t(
                "We're here to answer your questions and help you find the right property."
              )}
            </p>

          </div>
        </section>


        {/* ================= CONTENT ================= */}

        <section className="contact-content">

          {/* ================= FORM ================= */}

          <div className="contact-form-section">

            <div className="section-heading">

              <span className="section-label">
                {t("SEND A MESSAGE")}
              </span>

              <h2>
                {property
                  ? `${t("Interested in")} ${property.title}`
                  : t("Visit Our Office")}
              </h2>

              <p>
                {t(
                  "Fill in your details and our team will get back to you shortly."
                )}
              </p>

            </div>


            {/* PROPERTY INFO */}

            {propertyId && (
              <div className="contact-property-info">

                {propertyLoading ? (
                  <div className="property-loading">

                    <span className="loading-line loading-small" />

                    <span className="loading-line loading-large" />

                    <span className="loading-line loading-medium" />

                  </div>

                ) : property ? (
                  <>
                    <span className="property-inquiry-label">
                      {t("PROPERTY INQUIRY")}
                    </span>

                    <strong>
                      {property.title}
                    </strong>

                    {property.location && (
                      <small>
                        {property.location}
                      </small>
                    )}

                    {property.price != null && (
                      <small>
                        {Number(property.price).toLocaleString()} EGP
                      </small>
                    )}
                  </>
                ) : (
                  <p>
                    {t("Property information unavailable.")}
                  </p>
                )}

              </div>
            )}


            {/* FORM */}

            <form
              className="contact-form"
              onSubmit={handleSubmit}
            >

              <div className="form-row">

                <div className="form-field">

                  <label htmlFor="firstName">
                    {t("First name")}
                  </label>

                  <input
                    id="firstName"
                    type="text"
                    name="firstName"
                    placeholder={t("Your first name")}
                    value={formData.firstName}
                    onChange={handleChange}
                    required
                  />

                </div>


                <div className="form-field">

                  <label htmlFor="lastName">
                    {t("Last name")}
                  </label>

                  <input
                    id="lastName"
                    type="text"
                    name="lastName"
                    placeholder={t("Your last name")}
                    value={formData.lastName}
                    onChange={handleChange}
                    required
                  />

                </div>

              </div>


              <div className="form-row">

                <div className="form-field">

                  <label htmlFor="phone">
                    {t("Phone")}
                  </label>

                  <input
                    id="phone"
                    type="tel"
                    name="phone"
                    placeholder={t("Your phone number")}
                    value={formData.phone}
                    onChange={handleChange}
                    required
                  />

                </div>


                <div className="form-field">

                  <label htmlFor="email">
                    {t("Email")}
                  </label>

                  <input
                    id="email"
                    type="email"
                    name="email"
                    placeholder={t("Your email address")}
                    value={formData.email}
                    onChange={handleChange}
                    required
                  />

                </div>

              </div>


              <div className="form-field">

                <label htmlFor="message">
                  {t("Message")}
                </label>

                <textarea
                  id="message"
                  name="message"
                  placeholder={
                    property
                      ? `${t("I'm interested in")} ${property.title}...`
                      : t("Tell us how we can help...")
                  }
                  value={formData.message}
                  onChange={handleChange}
                  required
                />

              </div>


              <button
                className="contact-submit"
                type="submit"
              >

                <span>
                  {t("SEND MESSAGE")}
                </span>

                <svg
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M5 12h13" />
                  <path d="m13 6 6 6-6 6" />
                </svg>

              </button>

            </form>

          </div>


          {/* ================= INFORMATION ================= */}

          <div className="contact-info-section">

            <div className="section-heading">

              <span className="section-label">
                {t("STAY CONNECTED")}
              </span>

              <h2>
                {t("Follow Us Online")}
              </h2>

              <p>
                {t(
                  "Connect with us on social media and stay updated with our latest properties and news."
                )}
              </p>

            </div>


            <div className="contact-info-grid">

              {/* PHONE */}

              <div className="info-item">

                <div className="info-icon">
                  <svg viewBox="0 0 24 24">
                    <path d="M6.5 3.5h3l1.5 4-2 1.5a14 14 0 0 0 6 6l1.5-2 4 1.5v3c0 1-1 2-2 2C10.5 19.5 4.5 13.5 4.5 5.5c0-1 1-2 2-2Z" />
                  </svg>
                </div>

                <div>

                  <span className="info-label">
                    {t("PHONE")}
                  </span>

                  <span className="info-value">
                    {t('17337')}
                  </span>

                </div>

              </div>


              {/* EMAIL */}

              <div className="info-item">

                <div className="info-icon">
                  <svg viewBox="0 0 24 24">
                    <rect
                      x="3"
                      y="5"
                      width="18"
                      height="14"
                      rx="2"
                    />

                    <path d="m4 7 8 6 8-6" />
                  </svg>
                </div>

                <div>

                  <span className="info-label">
                    {t("EMAIL")}
                  </span>

                  <span className="info-value">
                    info@iunu-eg.com
                  </span>

                </div>

              </div>


              {/* HOURS */}

              <div className="info-item">

                <div className="info-icon">
                  <svg viewBox="0 0 24 24">

                    <rect
                      x="4"
                      y="3"
                      width="16"
                      height="18"
                      rx="2"
                    />

                    <path d="M8 7h8" />
                    <path d="M8 11h3" />
                    <path d="M13 11h3" />
                    <path d="M8 15h3" />
                    <path d="M13 15h3" />

                  </svg>
                </div>

                <div>

                  <span className="info-label">
                    {t("WORKING HOURS")}
                  </span>

                  <span className="info-value">
                    {t("Saturday - Thursday")}
                  </span>

                  <span className="info-secondary">
                    {t("11:00 AM to 07:00 PM")}
                  </span>

                </div>

              </div>


              {/* ADDRESS */}

              <div className="info-item">

                <div className="info-icon">
                  <svg viewBox="0 0 24 24">

                    <path d="M4 10 12 3l8 7" />
                    <path d="M6 9v11h12V9" />
                    <path d="M10 20v-6h4v6" />

                  </svg>
                </div>

                <div>

                  <span className="info-label">
                    {t("OUR OFFICE")}
                  </span>

                  <span className="info-value">
                    {t(
                      "Plot No. 306 307, Galaxy Mall, South 90th Street, second floor, Fifth Settlement, New Cairo, Egypt"
                    )}
                  </span>

                </div>

              </div>

            </div>


            {/* SOCIAL */}

            <div className="social-section">

              <span className="social-title">
                {t("Social Network")}
              </span>

              <div className="social-links">

                {/* Facebook */}

                <a
                  href="https://www.facebook.com/iunudevelopments"
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label="Facebook"
                  className="social-link"
                >
                  <svg viewBox="0 0 24 24">
                    <path d="M14 8h3V4h-3c-3.3 0-5 1.7-5 5v3H6v4h3v8h4v-8h3.5l.5-4H13V9c0-.7.3-1 1-1Z" />
                  </svg>
                </a>


                {/* Instagram */}

                <a
                  href="https://www.instagram.com/iunu.eg/?hl=en"
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label="Instagram"
                  className="social-link"
                >
                  <svg viewBox="0 0 24 24">

                    <rect
                      x="3"
                      y="3"
                      width="18"
                      height="18"
                      rx="5"
                    />

                    <circle
                      cx="12"
                      cy="12"
                      r="4"
                    />

                    <circle
                      cx="17.5"
                      cy="6.5"
                      r="1"
                      className="fill-icon"
                    />

                  </svg>
                </a>

{/* LinkedIn */}

<a
  href="https://www.linkedin.com/company/iunudevelopments/posts/?feedView=all"
  target="_blank"
  rel="noopener noreferrer"
  aria-label="LinkedIn"
  className="social-link"
>
  <svg viewBox="0 0 24 24">
    <path d="M6.5 8.5A2 2 0 1 0 6.5 4.5a2 2 0 0 0 0 4Z" />
    <path d="M4.8 10h3.4v9.5H4.8z" />
    <path d="M10.2 10h3.2v1.3c.7-1 1.8-1.7 3.5-1.7 3 0 4.3 1.8 4.3 5.1v4.8h-3.4v-4.4c0-1.6 0-2.9-1.9-2.9s-2.2 1.4-2.2 2.9v4.4h-3.4V10Z" />
  </svg>
</a>

                {/* WhatsApp */}

                <a
                  href="https://wa.me/201091218088"
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label="WhatsApp"
                  className="social-link"
                >
                  <svg viewBox="0 0 24 24">

                    <path d="M20 11.5a8 8 0 0 1-11.8 7.1L4 20l1.4-4.1A8 8 0 1 1 20 11.5Z" />

                    <path d="M9 8.5c.2-.4.4-.4.7-.4h.5c.2 0 .4.1.5.4l.7 1.6c.1.2.1.4-.1.6l-.5.6c-.1.1-.1.3 0 .5.4.7 1 1.3 1.7 1.7.2.1.4.1.5 0l.6-.6c.2-.2.4-.2.6-.1l1.5.7c.2.1.3.3.3.5v.5c0 .3-.1.5-.4.7-.4.3-1 .5-1.5.4-1.1-.2-2.3-.9-3.4-2-1-1-1.7-2.2-2-3.4-.1-.6.1-1.1.4-1.5Z" />

                  </svg>
                </a>


                {/* Email */}

                <a
                  href="mailto:info@iunu-eg.com"
                  aria-label="Email"
                  className="social-link"
                >
                  <svg viewBox="0 0 24 24">

                    <rect
                      x="3"
                      y="5"
                      width="18"
                      height="14"
                      rx="2"
                    />

                    <path d="m4 7 8 6 8-6" />

                  </svg>
                </a>

              </div>

            </div>

          </div>

        </section>
      </main>

      <Footer />
    </div>
  );
}

export default Contact;