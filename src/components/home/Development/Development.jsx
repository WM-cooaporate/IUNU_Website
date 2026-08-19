import { useEffect, useRef } from "react";
import "./Development.css";
const galleryImages = [
  {
    id: 1,
    src: "/images/assets/1.jpg",
    alt: "IUNU Development interior",
  },
  {
    id: 2,
    src: "/images/assets/2.jpg",
    alt: "IUNU Development interior",
  },
  {
    id: 3,
    src: "/images/assets/3.jpg",
    alt: "IUNU Development pool",
  },
  {
    id: 4,
    src: "/images/assets/4.jpg",
    alt: "IUNU Development buildings",
  },
  {
    id: 5,
    src: "/images/assets/5.jpg",
    alt: "IUNU Development exterior",
  },
  {
    id: 6,
    src: "/images/assets/6.jpg",
    alt: "IUNU Development playground",
  },
];
function Development() {
  const sectionRef = useRef(null);

  useEffect(() => {
    const section = sectionRef.current;

    if (!section) return;

    const elements = section.querySelectorAll(
      ".development-reveal"
    );

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
      }
    );

    elements.forEach((element) => {
      observer.observe(element);
    });

    return () => observer.disconnect();
  }, []);

  return (
    <section
      ref={sectionRef}
      className="development-section"
    >
      {/* =========================
          INTRO
      ========================= */}

      <div className="development-intro development-reveal">

        <div className="development-intro-label">
          IUNU DEVELOPMENTS
        </div>

        <div className="development-intro-content">

          <h2>
            Designed with
            <br />
            <span>purpose.</span>
          </h2>

          <div className="development-intro-text">

            <p>
              We create considered spaces where architecture,
              community and everyday life come together.
            </p>
          </div>

        </div>

      </div>


      {/* =========================
          FEATURED DEVELOPMENT
      ========================= */}

      <div className="development-feature development-reveal">

        <div className="development-feature-image">

        <img
  src="/images/dev.jpg"
  alt="IUNU Development"
/>

          <div className="development-image-overlay"></div>

          <div className="development-image-caption">
            <span>
              FEATURED DEVELOPMENT
            </span>

            <strong>
              IUNU
            </strong>
          </div>

        </div>


        <div className="development-feature-content">

          <span className="development-eyebrow">
            OUR APPROACH
          </span>

          <h3>
            Spaces that
            <br />
            <em>remain.</em>
          </h3>

          <p>
            Every IUNU development is shaped around a
            simple idea: create places that feel relevant
            today and remain meaningful tomorrow.
          </p>

          <div className="development-details">

            <div>
              <span>01</span>
              <p>
                Thoughtful Architecture
              </p>
            </div>

            <div>
              <span>02</span>
              <p>
                Lasting Quality
              </p>
            </div>

            <div>
              <span>03</span>
              <p>
                Human-Centered Spaces
              </p>
            </div>

          </div>

        </div>

      </div>


      {/* =========================
          GALLERY
      ========================= */}

      <div className="development-gallery-wrapper">

        <div className="development-gallery-header development-reveal">

          <div>
            <span className="development-eyebrow">
              THE IUNU VISION
            </span>

            <h3>
              A glimpse into
              <br />
              what we create.
            </h3>
          </div>

          <p>
            From refined interiors to carefully planned
            outdoor spaces, every detail contributes to
            the experience.
          </p>

        </div>


        <div className="development-gallery">

          {galleryImages.map((image, index) => (
            <div
              className={`development-gallery-item development-reveal development-gallery-item-${index + 1}`}
              key={image.id}
            >
              <img
                src={image.src}
                alt={image.alt}
              />

              <div className="development-gallery-number">
                0{index + 1}
              </div>
            </div>
          ))}

        </div>

      </div>

    </section>
  );
}

export default Development;