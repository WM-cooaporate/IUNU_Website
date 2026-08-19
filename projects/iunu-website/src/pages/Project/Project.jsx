import Navbar from "../../components/layout/Navbar/Navbar";
import "./Project.css";
import Footer from "../../components/layout/Footer/Footer";

function Project() {
  return (
    <div className="project-page">

      <Navbar />

      <main>

        {/* Hero */}
        <section className="project-hero">
          <div className="project-hero-content">
            <h1>Enduring Spaces for Tomorrow</h1>
          </div>
        </section>

        {/* Intro */}
        <section className="project-intro">
          <h2>Thoughtful Development</h2>
        </section>

        {/* Features */}
        <section className="project-features">

          <div className="project-feature">
            <h3>Legacy Inspired Design</h3>
            <p>
              Our projects prioritize community needs and sustainability.
            </p>
          </div>

          <div className="project-feature">
            <h3>Confident Project Delivery</h3>
            <p>
              Experience reliable development with a focus on quality and integrity.
            </p>
          </div>

          <div className="project-feature">
            <h3>Enduring Spaces</h3>
            <p>
              Creating impactful spaces that reflect purpose and longevity.
            </p>
          </div>

        </section>

        {/* Newsletter */}
        <section className="project-contact">
          <h2>Get in Touch Today</h2>

          <p>
            Reach out to us to discuss your real estate needs.
          </p>

          <form className="project-newsletter">
            <input
              type="email"
              placeholder="Email"
            />

            <button type="submit">
              SIGN UP
            </button>
          </form>
        </section>


      </main>
      <Footer />

    </div>
  );
}

export default Project;