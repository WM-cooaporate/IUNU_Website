import { useEffect, useRef, useState } from "react";
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
  const [selectedImage, setSelectedImage] = useState(null);
  const [activeIndex, setActiveIndex] = useState(0);

  const changeImage = (direction) => {
    setActiveIndex((currentIndex) =>
      (currentIndex + direction + galleryImages.length) % galleryImages.length
    );
  };

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

  useEffect(() => {
    if (!selectedImage) return undefined;

    const handleKeyDown = (event) => {
      if (event.key === "Escape") setSelectedImage(null);
    };

    document.addEventListener("keydown", handleKeyDown);
    document.body.style.overflow = "hidden";

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "";
    };
  }, [selectedImage]);

  useEffect(() => {
    const interval = window.setInterval(() => {
      setActiveIndex((currentIndex) =>
        (currentIndex + 1) % galleryImages.length
      );
    }, 5500);

    return () => window.clearInterval(interval);
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


        <div className="development-gallery-shell development-reveal">
          <div className="development-gallery-stage">
            <button
              type="button"
              className="development-gallery-feature"
              onClick={() => setSelectedImage(galleryImages[activeIndex])}
              aria-label={`Open ${galleryImages[activeIndex].alt}`}
            >
              <img
                src={galleryImages[activeIndex].src}
                alt={galleryImages[activeIndex].alt}
              />
              <span className="development-gallery-feature-shade" />
              <span className="development-gallery-feature-label">
                IUNU / {String(activeIndex + 1).padStart(2, "0")}
              </span>
              <span className="development-gallery-feature-caption">
                View image
                <span aria-hidden="true">&#8599;</span>
              </span>
            </button>

            <div className="development-gallery-controls" aria-label="Gallery controls">
              <span className="development-gallery-progress">
                <strong>{String(activeIndex + 1).padStart(2, "0")}</strong>
                <span>/ {String(galleryImages.length).padStart(2, "0")}</span>
              </span>
              <button
                type="button"
                className="development-gallery-control"
                onClick={() => changeImage(-1)}
                aria-label="Previous image"
              >
                <span aria-hidden="true">&#8592;</span>
              </button>
              <button
                type="button"
                className="development-gallery-control development-gallery-control-next"
                onClick={() => changeImage(1)}
                aria-label="Next image"
              >
                <span aria-hidden="true">&#8594;</span>
              </button>
            </div>
          </div>

          <div className="development-gallery-thumbs" aria-label="Choose gallery image">
            {galleryImages.map((image, index) => (
              <button
                type="button"
                className={`development-gallery-thumb${index === activeIndex ? " is-active" : ""}`}
                key={image.id}
                onClick={() => setActiveIndex(index)}
                aria-label={`Show image ${index + 1}`}
                aria-pressed={index === activeIndex}
              >
                <img src={image.src} alt="" loading="lazy" />
                <span>{String(index + 1).padStart(2, "0")}</span>
              </button>
            ))}
          </div>
        </div>

      </div>

      {selectedImage && (
        <div
          className="development-lightbox"
          role="dialog"
          aria-modal="true"
          aria-label="Image preview"
          onClick={() => setSelectedImage(null)}
        >
          <button
            type="button"
            className="development-lightbox-close"
            onClick={() => setSelectedImage(null)}
            aria-label="Close image preview"
          >
            <span aria-hidden="true">&times;</span>
          </button>
          <img
            className="development-lightbox-image"
            src={selectedImage.src}
            alt={selectedImage.alt}
            onClick={(event) => event.stopPropagation()}
          />
        </div>
      )}

    </section>
  );
}

export default Development;
