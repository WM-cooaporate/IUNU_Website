import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import propertyServices from "../../services/propertyServices";
import "./Project.css";

function Project() {
  const [properties, setProperties] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    const loadProperties = async () => {
      try {
        setLoading(true);
        setError("");

        const data = await propertyServices.getProperties();

        if (!cancelled) {
          setProperties(data.content || []);
        }
      } catch (error) {
        console.error("Properties loading error:", error);

        if (!cancelled) {
          setError("Failed to load properties.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    loadProperties();

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="project-page">

      <Navbar />

      <main>

        {/* =========================
            HERO
        ========================= */}

        <section className="project-hero">
          <div className="project-hero-content">
            <h1>Enduring Spaces for Tomorrow</h1>
          </div>
        </section>

        {/* =========================
            INTRO
        ========================= */}

        <section className="project-intro">
          <h2>Thoughtful Development</h2>
        </section>

        {/* =========================
            PROPERTIES
        ========================= */}

        <section className="project-properties">

          <div className="project-properties-header">

            <span className="project-eyebrow">
              OUR PROJECTS
            </span>

            <h2>
              Discover Our Properties
            </h2>

            <p>
              Explore the properties currently available
              across the IUNU platform.
            </p>

          </div>

          {/* LOADING */}

          {loading && (
            <div className="project-loading">
              <div className="project-spinner" />
              <p>Loading properties...</p>
            </div>
          )}

          {/* ERROR */}

          {!loading && error && (
            <div className="project-error">
              {error}
            </div>
          )}

          {/* EMPTY */}

          {!loading &&
            !error &&
            properties.length === 0 && (
              <div className="project-empty">
                <h3>No Properties Available</h3>

                <p>
                  There are currently no published
                  properties available.
                </p>
              </div>
            )}

          {/* PROPERTY CARDS */}

          {!loading &&
            !error &&
            properties.length > 0 && (
              <div className="project-properties-grid">

                {properties.map((property) => (
// eslint-disable-next-line no-undef
<Link
  to={`/project/${property.id}`}
  className="project-property-card"
  key={property.id}
>

                    {/* IMAGE */}

                    <div className="project-property-image">

                      {property.coverImageUrl ? (
                        <img
                          src={property.coverImageUrl}
                          alt={property.title}
                        />
                      ) : (
                        <div className="project-property-image-placeholder">
                          IUNU
                        </div>
                      )}

                      <span
                        className={`project-property-status project-property-status-${property.status?.toLowerCase()}`}
                      >
                        {property.status
                          ?.replaceAll("_", " ")}
                      </span>

                    </div>

                    {/* CONTENT */}

                    <div className="project-property-content">

                      <span className="project-property-type">
                        {property.type}
                      </span>

                      <h3>
                        {property.title}
                      </h3>

                      {property.location && (
                        <p className="project-property-location">
                          {property.location}
                        </p>
                      )}

                      {property.description && (
                        <p className="project-property-description">
                          {property.description}
                        </p>
                      )}

                      <div className="project-property-footer">

                        <strong>
                          {property.price != null
                            ? `${Number(
                                property.price
                              ).toLocaleString()} EGP`
                            : "Price on request"}
                        </strong>

                      </div>

                    </div>

                  </Link>

                ))}

              </div>
            )}

        </section>

        {/* =========================
            FEATURES
        ========================= */}

        <section className="project-features">

          <div className="project-feature">

            <h3>
              Legacy Inspired Design
            </h3>

            <p>
              Our projects prioritize community
              needs and sustainability.
            </p>

          </div>

          <div className="project-feature">

            <h3>
              Confident Project Delivery
            </h3>

            <p>
              Experience reliable development with
              a focus on quality and integrity.
            </p>

          </div>

          <div className="project-feature">

            <h3>
              Enduring Spaces
            </h3>

            <p>
              Creating impactful spaces that reflect
              purpose and longevity.
            </p>

          </div>

        </section>

        {/* =========================
            CONTACT
        ========================= */}

        <section className="project-contact">

          <h2>
            Get in Touch Today
          </h2>

          <p>
            Reach out to us to discuss your real
            estate needs.
          </p>

          <form
            className="project-newsletter"
            onSubmit={(event) => event.preventDefault()}
          >

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