import { useEffect, useRef } from "react";
import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import { useLanguage } from "../../i18n/LanguageContext";
import "./About.css";

function About() {
  const aboutPageRef = useRef(null);
  const { t } = useLanguage();

  useEffect(() => {
    const page = aboutPageRef.current;

    if (!page) return;

    const animatedElements = page.querySelectorAll(".about-reveal");

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        });
      },
      {
        threshold: 0.15,
        rootMargin: "0px 0px -60px 0px",
      }
    );

    animatedElements.forEach((element) => {
      observer.observe(element);
    });

    return () => {
      observer.disconnect();
    };
  }, []);

  return (
    <div className="about-page" ref={aboutPageRef}>
      <Navbar />

      <main>
        {/* ================= HERO ================= */}

        <section className="about-hero">
          <div className="about-hero-overlay">
            <span className="about-hero-label about-reveal">
              {t("IUNU DEVELOPMENTS")}
            </span>

            <h1 className="about-reveal about-reveal-up">
              {t("Crafting")}
              <br />
              <em>{t("Enduring Spaces")}</em>
            </h1>
          </div>
        </section>

        {/* ================= INTRO ================= */}

        <section className="about-intro">
          <h2 className="about-reveal about-reveal-up">
            {t("Enduring Real Estate Solutions")}
          </h2>

          <div className="about-intro-content">
            <div className="about-intro-image about-reveal about-reveal-left">
              <div className="about-image-inner">
                <img
                  src="/images/About%20copy.jpg"
                  alt={t("IUNU Development")}
                />
              </div>
            </div>

            <div className="about-intro-text about-reveal about-reveal-right">
              <span className="about-section-label">
                {t("WHO WE ARE")}
              </span>

              <h3>{t("What's IUNU?")}</h3>

              <p>
                {t(
                  'IUNU Developments is a distinguished real estate developer in Cairo, Egypt, inspired by the rich legacy of ancient Egyptian civilization. The name "IUNU", derived from the ancient Egyptian city of Heliopolis, reflects the company\'s commitment to blending cultural heritage with modern innovation.'
                )}
              </p>

              <p>
                {t(
                  "With a focus on crafting iconic residential and commercial projects, IUNU Developments is reshaping urban landscapes while honoring Egypt's timeless history."
                )}
              </p>
            </div>
          </div>
        </section>

        {/* ================= FOUNDER ================= */}

        <section className="about-founder">
          <div className="about-founder-content">
            <div className="about-founder-image about-reveal about-reveal-left">
              <div className="about-founder-image-inner">
                <img
                  src="/images/founder.jpg"
                  alt={t("Founder of IUNU Developments")}
                />
              </div>
            </div>

            <div className="about-founder-text">
              <span className="about-section-label about-reveal about-reveal-up">
                {t("THE FOUNDER")}
              </span>

              <h2 className="about-reveal about-reveal-up">
                {t("Who is the")}
                <br />
                <em>{t("Founder?")}</em>
              </h2>

              <h3 className="about-reveal about-reveal-up">
                {t("Mr. Wagdy Danial")}
              </h3>

              <p className="about-reveal about-reveal-up">
                {t(
                  "Mr. Wagdy Danial, a visionary entrepreneur from Upper Egypt, is the founder of IUNU Developments, a real estate investment company dedicated to transforming Cairo's urban landscape."
                )}
              </p>

              <p className="about-reveal about-reveal-up">
                {t(
                  "He began his career in 2006, drawing inspiration from the architectural grandeur of the Pharaohs and aiming to create modern developments that reflect the timeless elegance and innovative spirit of ancient Egyptian civilization."
                )}
              </p>

              <div className="about-founder-line about-reveal about-reveal-up" />

              <span className="about-founder-signature about-reveal about-reveal-up">
                {t("Founder & Chairman")}
                <br />
                IUNU Developments
              </span>
            </div>
          </div>
        </section>

        {/* ================= STORY ================= */}

        <section className="about-story">
          <div className="about-story-image about-reveal about-reveal-image">
            <div className="about-story-image-inner">
              <img
                src="/images/legacy.jpg"
                alt={t("IUNU Development project")}
              />
            </div>
          </div>

          <div className="about-story-content">
            <span className="about-small-title about-reveal about-reveal-up">
              {t("ABOUT US")}
            </span>

            <h2 className="about-reveal about-reveal-up">
              {t("Enduring Real Estate Solutions")}
            </h2>

            <p className="about-reveal about-reveal-up">
              {t(
                "IUNU Development stands out in real estate for creating enduring spaces. With a focus on legacy and thoughtful development, each project reflects a commitment to purpose and quality."
              )}
            </p>

            {/* ================= VALUES ================= */}

            <div className="about-values">
              <div className="about-value about-reveal about-reveal-value">
                <div className="value-icon value-icon-building">
                  <svg viewBox="0 0 64 64" aria-hidden="true">
                    <path d="M12 25L38 12L55 21L29 35L12 25Z" />
                    <path d="M12 25V39L29 49L55 35V21" />
                    <path d="M29 35V49" />
                    <path d="M17 27L43 14" />
                    <path d="M22 32L48 19" />
                    <path d="M29 24C32 22 36 23 38 26C40 29 38 32 35 33C32 34 28 33 27 30C26 27 27 25 29 24Z" />
                    <path d="M15 39L29 47L52 34" />
                  </svg>
                </div>

                <h3>
                  {t("Thoughtfully")}
                  <br />
                  {t("Designed")}
                  <br />
                  {t("Spaces")}
                </h3>
              </div>

              <div className="about-value about-reveal about-reveal-value">
                <div className="value-icon value-icon-home">
                  <svg viewBox="0 0 64 64" aria-hidden="true">
                    <path d="M10 30L32 11L54 30" />
                    <path d="M16 27V54H48V27" />
                    <path d="M27 54V39H37V54" />
                  </svg>
                </div>

                <h3>
                  {t("Quiet")}
                  <br />
                  {t("Confidence")}
                  <br />
                  {t("in Projects")}
                </h3>
              </div>

              <div className="about-value about-reveal about-reveal-value">
                <div className="value-icon value-icon-bulb">
                  <svg viewBox="0 0 64 64" aria-hidden="true">
                    <path d="M22 38C18 35 16 30 17 25C18 17 24 12 32 12C40 12 46 17 47 25C48 30 46 35 42 38C40 40 39 42 39 45H25C25 42 24 40 22 38Z" />
                    <path d="M25 51H39" />
                    <path d="M27 56H37" />
                  </svg>
                </div>

                <h3>
                  {t("Inspired")}
                  <br />
                  {t("by Legacy")}
                </h3>
              </div>
            </div>

            <button
              type="button"
              className="about-read-more about-reveal about-reveal-button"
            >
              <span>{t("READ MORE")}</span>

              <span className="about-read-more-arrow" aria-hidden="true">
                →
              </span>
            </button>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}

export default About;