import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import "./Footer.css";
import { useLanguage } from "../../../i18n/LanguageContext";

function Footer() {
  const { t } = useLanguage();

  const currentYear = new Date().getFullYear();

  const footerRef = useRef(null);

  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const footer = footerRef.current;

    if (!footer) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.unobserve(footer);
        }
      },
      {
        threshold: 0.12,
        rootMargin: "0px 0px -50px 0px",
      }
    );

    observer.observe(footer);

    return () => {
      observer.disconnect();
    };
  }, []);

  return (
    <footer
      ref={footerRef}
      className={`footer ${isVisible ? "footer-visible" : ""}`}
    >
      <div className="footer-container">

        {/* =========================
            TOP
        ========================= */}

        <div className="footer-top">

          {/* BRAND */}
          <div className="footer-brand">

            <span className="footer-brand-label">
              {t("IUNU DEVELOPMENTS")}
            </span>

            <h2>
              {t("Spaces")}
              <br />
              <em>{t("that remain.")}</em>
            </h2>

            <p>
              {t(
                "Creating considered spaces where architecture, community and everyday life come together."
              )}
            </p>

          </div>


          {/* NAVIGATION */}
          <div className="footer-column">

            <span className="footer-column-title">
              {t("EXPLORE")}
            </span>

            <Link to="/home">
              {t("Home")}
            </Link>

            <Link to="/about">
              {t("About Us")}
            </Link>

            <Link to="/project">
              {t("Projects")}
            </Link>

            <Link to="/contact">
              {t("Contact")}
            </Link>

          </div>


          {/* CONTACT */}
          <div className="footer-column footer-contact-column">

            <span className="footer-column-title">
              {t("CONTACT")}
            </span>

            <a href="tel:17337">
              17337
            </a>

            <a href="tel:0225371444">
              02 253 71 444
            </a>

            <a href="tel:0225371443">
              02 253 71 443
            </a>

            <a href="mailto:info@iunu-eg.com">
              info@iunu-eg.com
            </a>

          </div>


          {/* ADDRESS */}
          <div className="footer-column footer-address-column">

            <span className="footer-column-title">
              {t("FIND US")}
            </span>

            <p>
              {t(
                "Plot No. 306–307, Galaxy Mall, South 90th Street, Second Floor, Fifth Settlement, New Cairo, Egypt."
              )}
            </p>

          </div>

        </div>


        {/* DIVIDER */}

        <div className="footer-divider"></div>


        {/* BOTTOM */}

        <div className="footer-bottom">

          <span className="footer-copyright">
            © {currentYear} IUNU Developments
          </span>


          <div className="footer-social">

            {/* Facebook */}
            <a
              href="https://www.facebook.com/iunudevelopments"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Facebook"
              className="footer-social-link"
            >
              <svg viewBox="0 0 24 24">
                <path d="M14 8h3V4h-3c-3.31 0-5 1.69-5 5v3H6v4h3v8h4v-8h3.5l.5-4H13V9c0-.67.33-1 1-1z" />
              </svg>
            </a>


            {/* Instagram */}
            <a
              href="https://www.instagram.com/iunu.eg/?hl=en"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Instagram"
              className="footer-social-link"
            >
              <svg viewBox="0 0 24 24">

                <rect
                  x="3"
                  y="3"
                  width="18"
                  height="18"
                  rx="5"
                  ry="5"
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
                  className="footer-social-fill"
                />

              </svg>
            </a>


            {/* LinkedIn */}
            <a
              href="https://www.linkedin.com/company/iunudevelopments/posts/?feedView=all"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="LinkedIn"
              className="footer-social-link"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
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
              className="footer-social-link"
            >
              <svg viewBox="0 0 24 24">

                <path d="M20 11.5a8 8 0 0 1-11.8 7.1L4 20l1.4-4.1A8 8 0 1 1 20 11.5z" />

                <path d="M9 8.5c.2-.4.4-.4.7-.4h.5c.2 0 .4.1.5.4l.7 1.6c.1.2.1.4-.1.6l-.5.6c-.1.1-.1.3 0 .5.4.7 1 1.3 1.7 1.7.2.1.4.1.5 0l.6-.6c.2-.2.4-.2.6-.1l1.5.7c.2.1.3.3.3.5v.5c0 .3-.1.5-.4.7-.4.3-1 .5-1.5.4-1.1-.2-2.3-.9-3.4-2-1-1-1.7-2.2-2-3.4-.1-.6.1-1.1.4-1.5z" />

              </svg>
            </a>


            {/* Email */}
            <a
              href="mailto:info@iunu-eg.com"
              aria-label="Email"
              className="footer-social-link"
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


          <span className="footer-legal">
            {t("All Rights Reserved.")}
          </span>

        </div>

      </div>

    </footer>
  );
}

export default Footer;