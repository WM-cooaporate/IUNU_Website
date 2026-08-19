import { Link } from "react-router-dom";
import "./Hero.css";

function Hero() {
  return (
    <section className="hero">

      {/* Background overlay */}
      <div className="hero-overlay"></div>

      {/* Hero content */}
      <div className="hero-content">

        <span className="hero-eyebrow">
          IUNU DEVELOPMENTS
        </span>

        <h1>
          Who we are
        </h1>

        <p>
          We Are Crafting Signature Destinations
        </p>

        <Link
          to="/project"
          className="hero-button"
        >
          <span>EXPLORE PROJECTS</span>

          <span className="hero-button-arrow">
            →
          </span>
        </Link>

      </div>

      {/* Scroll indicator */}
      <div className="hero-scroll">

        <span className="hero-scroll-line"></span>

        <span className="hero-scroll-text">
          SCROLL TO EXPLORE
        </span>

      </div>

    </section>
  );
}

export default Hero;