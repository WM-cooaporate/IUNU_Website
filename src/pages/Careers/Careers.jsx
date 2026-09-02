import { useState } from "react";
import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import careerServices from "../../services/careerServices";
import { useLanguage } from "../../i18n/LanguageContext";
import "./Careers.css";

const initialForm = { fullName: "", email: "", phone: "", position: "", message: "", resume: null };

function Careers() {
  const [form, setForm] = useState(initialForm);
  const [status, setStatus] = useState({ type: "", text: "" });
  const [submitting, setSubmitting] = useState(false);
  const { t } = useLanguage();

  const handleChange = (event) => {
    const { name, value, files } = event.target;
    setForm((current) => ({ ...current, [name]: files ? files[0] : value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setStatus({ type: "", text: "" });

    try {
      await careerServices.apply(form);
      setForm(initialForm);
      setStatus({ type: "success", text: t("Thank you. Your application has been sent to our team.") });
    } catch (error) {
      setStatus({
        type: "error",
        text: error.response?.status === 413
          ? t("The CV file is too large. Please choose a PDF under 5 MB.")
          : t("We could not send your application right now. Please try again or email info@iunu-eg.com."),
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="careers-page">
      <Navbar />
      <main>
        <section className="careers-hero">
          <div className="careers-hero-content">
            <span className="careers-eyebrow">{t("JOIN OUR TEAM")}</span>
            <h1>{t("Build what")}<br /><em>{t("remains.")}</em></h1>
            <p>{t("Bring your talent, curiosity and ambition to a team shaping enduring spaces for tomorrow.")}</p>
          </div>
        </section>

        <section className="careers-content">
          <div className="careers-intro">
            <span className="careers-section-label">{t("CAREERS AT IUNU")}</span>
            <h2>{t("Make an impact")}<br /><em>{t("with us.")}</em></h2>
            <p>{t("We are always interested in meeting thoughtful people who care about quality, collaboration and the future of development.")}</p>
            <a className="careers-email" href="mailto:info@iunu-eg.com">info@iunu-eg.com <span aria-hidden="true">↗</span></a>
          </div>

          <div className="careers-form-wrap">
            <div className="careers-form-heading">
              <span className="careers-section-label">{t("SEND YOUR APPLICATION")}</span>
              <h2>{t("Tell us about yourself.")}</h2>
              <p>{t("Complete the form and our team will review your application.")}</p>
            </div>
            <form className="careers-form" onSubmit={handleSubmit}>
              <div className="careers-form-row">
                <label>{t("Full name")}<input name="fullName" value={form.fullName} onChange={handleChange} placeholder={t("Your full name")} required /></label>
                <label>{t("Email")}<input name="email" type="email" value={form.email} onChange={handleChange} placeholder={t("Your email address")} required /></label>
              </div>
              <div className="careers-form-row">
                <label>{t("Phone")}<input name="phone" type="tel" value={form.phone} onChange={handleChange} placeholder={t("Your phone number")} required /></label>
                <label>{t("Position")}<input name="position" value={form.position} onChange={handleChange} placeholder={t("Position of interest")} required /></label>
              </div>
              <label>{t("Message")}<textarea name="message" value={form.message} onChange={handleChange} placeholder={t("Tell us a little about your experience...")} rows="5" required /></label>
              <label className="careers-file-field">{t("CV / Resume")} <input name="resume" type="file" accept="application/pdf,.pdf" onChange={handleChange} /><small>{form.resume ? form.resume.name : t("PDF only, up to 5 MB")}</small></label>
              {status.text && <div className={`careers-status careers-status-${status.type}`}>{status.text}</div>}
              <button className="careers-submit" type="submit" disabled={submitting}><span>{submitting ? t("SENDING...") : t("SUBMIT APPLICATION")}</span><span aria-hidden="true">↗</span></button>
            </form>
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}

export default Careers;
