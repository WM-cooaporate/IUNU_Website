import "./Hero.css";
import { FaPlayCircle } from "react-icons/fa";

function Hero() {
  return (
    <section className="hero">

      <div className="hero-container">

        <div className="hero-content">

          <span className="hero-badge">
            🚀 Learn Without Limits
          </span>

          <h1>
            Build Your Future
            <br />
            With Online Learning
          </h1>

          <p>
            Learn programming, design, business and many more
            from professional instructors anytime and anywhere.
          </p>

          <div className="hero-buttons">

            <button className="primary-btn">
              Explore Courses
            </button>

            <button className="secondary-btn">

              <FaPlayCircle />

              Watch Demo

            </button>

          </div>

          <div className="hero-stats">

            <div>

              <h2>15K+</h2>

              <span>Students</span>

            </div>

            <div>

              <h2>350+</h2>

              <span>Courses</span>

            </div>

            <div>

              <h2>120+</h2>

              <span>Teachers</span>

            </div>

          </div>

        </div>

        <div className="hero-image">

          <img
            src="https://images.unsplash.com/photo-1522202176988-66273c2fd55f?w=900"
            alt="Students"
          />

        </div>

      </div>

    </section>
  );
}

export default Hero;