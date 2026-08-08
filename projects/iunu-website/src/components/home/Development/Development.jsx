import "./Development.css";

const galleryImages = [
  {
    id: 1,
    src: "/src/assets/gallery-1.jpg",
    alt: "IUNU Development interior",
  },
  {
    id: 2,
    src: "/src/assets/gallery-2.jpg",
    alt: "IUNU Development interior",
  },
  {
    id: 3,
    src: "/src/assets/gallery-3.jpg",
    alt: "IUNU Development pool",
  },
  {
    id: 4,
    src: "/src/assets/gallery-4.jpg",
    alt: "IUNU Development buildings",
  },
  {
    id: 5,
    src: "/src/assets/gallery-5.jpg",
    alt: "IUNU Development exterior",
  },
  {
    id: 6,
    src: "/src/assets/gallery-6.jpg",
    alt: "IUNU Development playground",
  },
];

function Development() {
  return (
    <section className="development-section">

      {/* Main Image */}
      <div className="development-main-image">
        <img
          src="/src/assets/development.jpg"
          alt="IUNU Development"
        />
      </div>

      {/* Content */}
      <div className="development-content">

        <h2>IUNU Development</h2>

        <p>
          Explore our thoughtful real estate developments designed
          with purpose and lasting impact.
        </p>

      </div>

      {/* Gallery */}
      <div className="development-gallery">

        {galleryImages.map((image) => (
          <div
            className="development-gallery-item"
            key={image.id}
          >
            <img
              src={image.src}
              alt={image.alt}
            />
          </div>
        ))}

      </div>

    </section>
  );
}

export default Development;