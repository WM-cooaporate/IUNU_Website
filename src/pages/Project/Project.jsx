import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import propertyServices from "../../services/propertyServices";
import demoProperties from "../../data/demoProperties";
import "./Project.css";

function Project() {
  const [properties, setProperties] = useState(demoProperties);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    const loadProperties = async () => {
      try {
        setError("");

        const data = await propertyServices.getProperties();

        if (!cancelled) {
          setProperties(data.content?.length ? data.content : demoProperties);
        }
      } catch (error) {
        console.error("Properties loading error:", error);

        if (!cancelled) {
          setProperties(demoProperties);
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
            <span className="project-hero-eyebrow">
              IUNU DEVELOPMENTS
            </span>

            <h1>Enduring Spaces for Tomorrow</h1>

            <p>
              Discover thoughtfully developed spaces designed
              around quality, purpose, and lasting value.
            </p>

            <div className="project-hero-line" />
          </div>
        </section>

        {/* =========================
            INTRO
        ========================= */}

        <section className="project-intro">
          <span className="project-section-eyebrow">
            OUR APPROACH
          </span>

          <h2>Thoughtful Development</h2>

          <p>
            We create enduring spaces that balance thoughtful
            design, functionality, and long-term value.
          </p>
        </section>

        {/* =========================
            PROPERTIES
        ========================= */}

        <section className="project-properties">
          <div className="project-properties-header">
            <span className="project-eyebrow">
              OUR PROJECTS
            </span>

            <h2>Discover Our Properties</h2>

            <p>
              Explore the properties currently available
              across the IUNU platform.
            </p>
          </div>

          {/* =========================
              LOADING
          ========================= */}

          {loading && (
            <div className="project-loading">
              <div className="project-spinner" />

              <p>Loading properties...</p>
            </div>
          )}

          {/* =========================
              ERROR
          ========================= */}

          {!loading && error && (
            <div className="project-message project-error">
              <span>ERROR</span>

              <h3>Unable to Load Properties</h3>

              <p>{error}</p>
            </div>
          )}

          {/* =========================
              EMPTY
          ========================= */}

          {!loading &&
            !error &&
            properties.length === 0 && (
              <div className="project-message project-empty">
                <span>PROJECTS</span>

                <h3>No Properties Available</h3>

                <p>
                  There are currently no published
                  properties available.
                </p>
              </div>
            )}

          {/* =========================
              PROPERTY CARDS
          ========================= */}

          {!loading &&
            !error &&
            properties.length > 0 && (
              <div className="project-properties-grid">
                {properties.map((property, index) => {
                  const status =
                    property.status?.toLowerCase();

                  const formattedStatus =
                    property.status?.replaceAll("_", " ");

                  return (
                    <Link
                      to={`/project/${property.id}`}
                      className="project-property-card"
                      key={property.id}
                      style={{
                        "--card-index": index,
                      }}
                    >
                      {/* IMAGE */}

                      <div className="project-property-image">
                        {property.coverImageUrl ? (
                          <img
                            src={property.coverImageUrl}
                            alt={property.title}
                            loading="lazy"
                          />
                        ) : (
                          <div className="project-property-image-placeholder">
                            IUNU
                          </div>
                        )}

                        {property.status && (
                          <span
                            className={`project-property-status project-property-status-${status}`}
                          >
                            {formattedStatus}
                          </span>
                        )}

                        <div className="project-property-overlay">
                          <span>
                            VIEW PROPERTY
                          </span>

                          <span className="project-property-arrow">
                            →
                          </span>
                        </div>
                      </div>

                      {/* CONTENT */}

                      <div className="project-property-content">
                        {property.type && (
                          <span className="project-property-type">
                            {property.type}
                          </span>
                        )}

                        <h3>{property.title}</h3>

                        {property.location && (
                          <p className="project-property-location">
                            {property.location}
                          </p>
                        )}

                        {property.area != null && (
                          <p className="project-property-location">
                            {Number(property.area).toLocaleString()} m²
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

                          <span className="project-property-view">
                            EXPLORE →
                          </span>
                        </div>
                      </div>
                    </Link>
                  );
                })}
              </div>
            )}
        </section>

        {/* =========================
            FEATURES
        ========================= */}

        <section className="project-features">
          <div className="project-feature">
            <span className="project-feature-number">
              01
            </span>

            <h3>Legacy Inspired Design</h3>

            <p>
              Our projects prioritize community needs,
              thoughtful design, and sustainability.
            </p>
          </div>

          <div className="project-feature">
            <span className="project-feature-number">
              02
            </span>

            <h3>Confident Project Delivery</h3>

            <p>
              Experience reliable development with a
              focus on quality and integrity.
            </p>
          </div>

          <div className="project-feature">
            <span className="project-feature-number">
              03
            </span>

            <h3>Enduring Spaces</h3>

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
          <span className="project-section-eyebrow">
            CONNECT WITH US
          </span>

          <h2>Get in Touch Today</h2>

          <p>
            Reach out to us to discuss your real
            estate needs.
          </p>

          <form
            className="project-newsletter"
            onSubmit={(event) =>
              event.preventDefault()
            }
          >
            <input
              type="email"
              placeholder="Email"
              aria-label="Email address"
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
