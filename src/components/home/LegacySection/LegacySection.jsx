import "./LegacySection.css";

function LegacySection() {
  return (
    <section className="legacy-section">

      <div className="legacy-content">

        <span className="legacy-label">
          CREATING ENDURING SPACES
        </span>

        <h2>
          A Commitment to Legacy
        </h2>

        <p>
          IUNU Development focuses on thoughtful real estate projects
          designed with purpose, character, and lasting impact.
        </p>

      </div>

      <div className="legacy-image-wrapper">

        <div className="legacy-image-overlay"></div>

        <img
          src="/images/legacy.jpg"
          alt="IUNU Development"
          className="legacy-image"
        />

        <div className="legacy-image-caption">
          <span>01</span>
          <span>ENDURING SPACES</span>
        </div>

      </div>

    </section>
  );
}

export default LegacySection;