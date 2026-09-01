import { useEffect, useRef, useState } from "react";
import "./QuoteForm.css";
import { useLanguage } from "../../../i18n/LanguageContext";

const initialFormData = {
  name: "",
  phone: "",
  city: "",
  email: "",
  project: "",
  whatsapp: "",
  spaceType: "",
};

function QuoteForm() {
  const { t } = useLanguage();
  const [formData, setFormData] = useState(initialFormData);
  const [submitted, setSubmitted] = useState(false);
  const [isVisible, setIsVisible] = useState(false);

  const sectionRef = useRef(null);

  useEffect(() => {
    const section = sectionRef.current;

    if (!section) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.unobserve(section);
        }
      },
      {
        threshold: 0.15,
        rootMargin: "0px 0px -60px 0px",
      }
    );

    observer.observe(section);

    return () => {
      observer.disconnect();
    };
  }, []);

  const handleChange = (event) => {
    const { name, value } = event.target;

    setFormData((previousData) => ({
      ...previousData,
      [name]: value,
    }));

    if (submitted) {
      setSubmitted(false);
    }
  };

  const handleSubmit = (event) => {
    event.preventDefault();

    console.log("Quote Request:", formData);

    setSubmitted(true);
    setFormData(initialFormData);
  };

  return (
    <section
      ref={sectionRef}
      className={`quote-section ${isVisible ? "quote-section-visible" : ""}`}
    >
      <div className="quote-container">

        {/* Header */}
        <div className="quote-header">

          <span className="quote-eyebrow">
            {t("LET'S TALK")}
          </span>

          <h2>
            {t("Request a")} <em>{t("quote.")}</em>
          </h2>

          <p>
            {t("Tell us a little about what you're looking for and our team will be in touch shortly.")}
          </p>

        </div>


        {/* Form */}
        <form
          className="quote-form"
          onSubmit={handleSubmit}
        >

          {/* Row 1 */}
          <div className="quote-row">

            <div className="quote-field">
              <label htmlFor="name">
                {t("Name")}
              </label>

              <input
                id="name"
                name="name"
                type="text"
                placeholder="Enter your name"
                value={formData.name}
                onChange={handleChange}
                required
              />
            </div>


            <div className="quote-field">
              <label htmlFor="phone">
                {t("Phone number")}
              </label>

              <input
                id="phone"
                name="phone"
                type="tel"
                placeholder="Enter your phone number"
                value={formData.phone}
                onChange={handleChange}
                required
              />
            </div>

          </div>


          {/* Row 2 */}
          <div className="quote-row">

            <div className="quote-field">
              <label htmlFor="city">
                {t("City")}
              </label>

              <input
                id="city"
                name="city"
                type="text"
                placeholder="Enter your city"
                value={formData.city}
                onChange={handleChange}
                required
              />
            </div>


            <div className="quote-field">
              <label htmlFor="email">
                {t("Email")}
              </label>

              <input
                id="email"
                name="email"
                type="email"
                placeholder="Enter your email address"
                value={formData.email}
                onChange={handleChange}
                required
              />
            </div>

          </div>


          {/* Row 3 */}
          <div className="quote-row">

            <div className="quote-field">

              <label htmlFor="project">
                {t("Project")}
              </label>

              <select
                id="project"
                name="project"
                value={formData.project}
                onChange={handleChange}
                required
              >

                <option value="">
                  {t("Select your option")}
                </option>

                <option value="residential">
                  {t("Residential")}
                </option>

                <option value="commercial">
                  {t("Commercial")}
                </option>

                <option value="administrative">
                  {t("Administrative")}
                </option>

              </select>

            </div>


            <div className="quote-field">

              <label htmlFor="whatsapp">
              {t("WhatsApp number")}
              </label>

              <input
                id="whatsapp"
                name="whatsapp"
                type="tel"
                placeholder="Enter your WhatsApp number"
                value={formData.whatsapp}
                onChange={handleChange}
              />

            </div>

          </div>


          {/* Space Type */}
          <div className="quote-field quote-full-field">

            <label htmlFor="spaceType">
              {t("What is the most suitable space for your needs?")}
            </label>

            <select
              id="spaceType"
              name="spaceType"
              value={formData.spaceType}
              onChange={handleChange}
              required
            >

              <option value="">
                {t("Select your option")}
              </option>

              <option value="apartment">
                {t("Apartment")}
              </option>

              <option value="villa">
                {t("Villa")}
              </option>

              <option value="office">
                {t("Office")}
              </option>

              <option value="commercial">
                {t("Commercial Space")}
              </option>

            </select>

          </div>


          {/* Submit */}
          <div className="quote-submit">

            <button type="submit">
              <span>
                SUBMIT REQUEST
              </span>

              <span className="quote-submit-arrow">
                →
              </span>
            </button>

          </div>


          {/* Success */}
          {submitted && (
            <div className="quote-success">
              Thank you. Your request has been received.
            </div>
          )}

        </form>

      </div>
    </section>
  );
}

export default QuoteForm;
