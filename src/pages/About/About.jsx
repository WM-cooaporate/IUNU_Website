import { useEffect, useRef } from "react";
import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import "./About.css";

function About() {
  const aboutPageRef = useRef(null);

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
              IUNU DEVELOPMENTS
            </span>

            <h1 className="about-reveal about-reveal-up">
              Crafting
              <br />
              <em>Enduring Spaces</em>
            </h1>

          </div>

        </section>


        {/* ================= INTRO ================= */}

        <section className="about-intro">

          <h2 className="about-reveal about-reveal-up">
            Enduring Real Estate Solutions
          </h2>


          <div className="about-intro-content">

            <div className="about-intro-image about-reveal about-reveal-left">

              <div className="about-image-inner">
                <img
                  src="/images/about/about-main.jpg"
                  alt="IUNU Development"
                />
              </div>

            </div>


            <div className="about-intro-text about-reveal about-reveal-right">

              <span className="about-section-label">
                WHO WE ARE
              </span>

              <h3>
                What's IUNU?
              </h3>

              <p>
                IUNU Developments is a distinguished real estate
                developer in Cairo, Egypt, inspired by the rich
                legacy of ancient Egyptian civilization. The name
                "IUNU", derived from the ancient Egyptian city of
                Heliopolis, reflects the company's commitment to
                blending cultural heritage with modern innovation.
              </p>

              <p>
                With a focus on crafting iconic residential and
                commercial projects, IUNU Developments is reshaping
                urban landscapes while honoring Egypt's timeless
                history.
              </p>

            </div>

          </div>

        </section>


        {/* ================= STORY ================= */}

        <section className="about-story">

          <div className="about-story-image about-reveal about-reveal-image">

            <div className="about-story-image-inner">
              <img
                src="/images/about/about-building.jpg"
                alt="IUNU Development project"
              />
            </div>

          </div>


          <div className="about-story-content">

            <span className="about-small-title about-reveal about-reveal-up">
              ABOUT US
            </span>

            <h2 className="about-reveal about-reveal-up">
              Enduring Real Estate Solutions
            </h2>

            <p className="about-reveal about-reveal-up">
              IUNU Development stands out in real estate for
              creating enduring spaces. With a focus on legacy
              and thoughtful development, each project reflects
              a commitment to purpose and quality.
            </p>


            {/* ================= VALUES ================= */}

            <div className="about-values">

              <div className="about-value about-reveal about-reveal-value">

                <div className="value-icon value-icon-building">
                  <svg viewBox="0 0 64 64">
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
                  Thoughtfully
                  <br />
                  Designed
                  <br />
                  Spaces
                </h3>

              </div>


              <div className="about-value about-reveal about-reveal-value">

                <div className="value-icon value-icon-home">
                  <svg viewBox="0 0 64 64">
                    <path d="M10 30L32 11L54 30" />
                    <path d="M16 27V54H48V27" />
                    <path d="M27 54V39H37V54" />
                  </svg>
                </div>

                <h3>
                  Quiet
                  <br />
                  Confidence
                  <br />
                  in Projects
                </h3>

              </div>


              <div className="about-value about-reveal about-reveal-value">

                <div className="value-icon value-icon-bulb">
                  <svg viewBox="0 0 64 64">
                    <path d="M22 38C18 35 16 30 17 25C18 17 24 12 32 12C40 12 46 17 47 25C48 30 46 35 42 38C40 40 39 42 39 45H25C25 42 24 40 22 38Z" />
                    <path d="M25 51H39" />
                    <path d="M27 56H37" />
                  </svg>
                </div>

                <h3>
                  Inspired
                  <br />
                  by Legacy
                </h3>

              </div>

            </div>


            <button className="about-read-more about-reveal about-reveal-button">
              <span>READ MORE</span>
              <span className="about-read-more-arrow">
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