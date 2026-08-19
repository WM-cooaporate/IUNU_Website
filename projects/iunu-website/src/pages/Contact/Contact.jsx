import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import "./Contact.css";

function Contact() {
  return (
    <div className="contact-page">
      <Navbar />

      <main>
        {/* Hero */}
        <section className="contact-hero">
          <h1>Get In Touch</h1>
          <p>We're here to answer your queries.</p>
        </section>

        {/* Contact Content */}
        <section className="contact-content">

          {/* Form */}
          <div className="contact-form-section">
            <h2>Visit Our Office</h2>

            <form className="contact-form">

              <div className="form-row">
                <input
                  type="text"
                  placeholder="First name"
                />

                <input
                  type="text"
                  placeholder="Last name"
                />
              </div>

              <div className="form-row">
                <input
                  type="tel"
                  placeholder="Phone"
                />

                <input
                  type="email"
                  placeholder="Email"
                />
              </div>

              <textarea
                placeholder="Message"
              ></textarea>

              <button type="submit">
                SEND MESSAGE
              </button>

            </form>
          </div>

          {/* Information */}
          <div className="contact-info-section">

            <h2>Follow Us Online</h2>

            <p className="contact-description">
              Connect with us on social media for updates.
            </p>

            <div className="contact-info-grid">

              <div className="info-item">
                <div className="info-icon">☎</div>

                <div>
                  <span>17337</span>
                </div>
              </div>

              <div className="info-item">
                <div className="info-icon">✉</div>

                <div>
                  <span>info@iunu-eg.com</span>
                </div>
              </div>
<div className="info-item">
  <div className="info-icon info-icon-building">
    <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <path d="M18 54V20L32 12L46 20V54" />
      <path d="M12 54H52" />
      <path d="M24 28H28" />
      <path d="M36 28H40" />
      <path d="M24 36H28" />
      <path d="M36 36H40" />
      <path d="M24 44H28" />
      <path d="M36 44H40" />
      <path d="M29 54V44H35V54" />
    </svg>
  </div>

  <div>
    <span>Saturday - Thursday</span>
    <span>11:00 AM to 07:00 PM</span>
  </div>
</div>


<div className="info-item">
  <div className="info-icon info-icon-home">
    <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <path d="M20 30L32 18L44 30" />
      <path d="M23 28V47H41V28" />
      <path d="M29 47V36H35V47" />
    </svg>
  </div>

  <div>
    <span>
      Plot No. 306 307, Galaxy Mall,
      South 90th Street, second floor,
      Fifth Settlement, New Cairo,
      Egypt
    </span>
  </div>
</div>

            </div>
<div className="social-section">
  <span>Social Network</span>

  <div className="social-links">

    {/* Facebook */}
    <a href="#" aria-label="Facebook" className="social-link">
      <svg viewBox="0 0 24 24">
        <path d="M14 8h3V4h-3c-3.3 0-5 1.7-5 5v3H6v4h3v8h4v-8h3.5l.5-4H13V9c0-.7.3-1 1-1z" />
      </svg>
    </a>

    {/* Instagram */}
    <a href="#" aria-label="Instagram" className="social-link">
      <svg viewBox="0 0 24 24">
        <rect x="3" y="3" width="18" height="18" rx="5" />
        <circle cx="12" cy="12" r="4" />
        <circle cx="17.5" cy="6.5" r="1" className="fill-icon" />
      </svg>
    </a>

    {/* WhatsApp */}
    <a href="#" aria-label="WhatsApp" className="social-link">
      <svg viewBox="0 0 24 24">
        <path d="M20 11.5a8 8 0 0 1-11.8 7.1L4 20l1.4-4.1A8 8 0 1 1 20 11.5z" />
        <path d="M9 8.5c.2-.4.4-.4.7-.4h.5c.2 0 .4.1.5.4l.7 1.6c.1.2.1.4-.1.6l-.5.6c-.1.1-.1.3 0 .5.4.7 1 1.3 1.7 1.7.2.1.4.1.5 0l.6-.6c.2-.2.4-.2.6-.1l1.5.7c.2.1.3.3.3.5v.5c0 .3-.1.5-.4.7-.4.3-1 .5-1.5.4-1.1-.2-2.3-.9-3.4-2-1-1-1.7-2.2-2-3.4-.1-.6.1-1.1.4-1.5z" />
      </svg>
    </a>

    {/* Email */}
    <a href="#" aria-label="Email" className="social-link email">
      <svg viewBox="0 0 24 24">
        <rect x="3" y="5" width="18" height="14" rx="2" />
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