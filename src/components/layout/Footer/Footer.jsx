import "./Footer.css";

function Footer() {
  return (
    <footer className="footer">

      <div className="footer-contact">

        <h3>Contact Info</h3>

        <div className="footer-contact-content">

          {/* Phone Numbers */}
          <div className="footer-phones">
            <a href="tel:17337">17337</a>
            <a href="tel:0225371444">02 253 71 444</a>
            <a href="tel:0225371443">02 253 71 443</a>
          </div>


          {/* Address */}
          <div className="footer-address">
            <p>
              Plot No. 306 307, Galaxy Mall, South 90th Street,
              second floor, Fifth Settlement, New Cairo, Egypt
            </p>
          </div>

        </div>


        {/* Social Icons */}
        <div className="footer-social">

          {/* Facebook */}
          <a
            href="https://www.facebook.com/iunudevelopments"
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Facebook"
            className="social-icon"
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
            className="social-icon"
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
                className="fill-icon"
              />
            </svg>
          </a>


          {/* WhatsApp */}
          <a
            href="https://wa.me/201000000000"
            target="_blank"
            rel="noopener noreferrer"
            aria-label="WhatsApp"
            className="social-icon"
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
            className="social-icon email-icon"
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


      {/* Copyright */}
      <div className="footer-bottom">

        <p>
          © {new Date().getFullYear()} IUNU Developments.
          All Rights Reserved. All content, designs, images, and trademarks
          displayed on this website are the property of IUNU Developments
          and may not be used without prior written permission.
        </p>

      </div>

    </footer>
  );
}

export default Footer;