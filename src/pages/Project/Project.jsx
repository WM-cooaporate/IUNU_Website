import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import propertyServices from "../../services/propertyServices";
import demoProperties from "../../data/demoProperties";

import { useLanguage } from "../../i18n/LanguageContext";

import "./Project.css";

function Project() {
  const { t } = useLanguage();

  const [properties, setProperties] = useState(demoProperties);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    const loadProperties = async () => {
      try {
        setLoading(true);
        setError("");

        const data = await propertyServices.getProperties();

        if (!cancelled) {
          setProperties(
            data.content?.length ? data.content : demoProperties
          );
        }
      } catch (error) {
        console.error("Properties loading error:", error);

        if (!cancelled) {
          setError(t("Unable to Load Properties"));
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
  }, [t]);

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
              {t("IUNU DEVELOPMENTS")}
            </span>

            <h1>{t("Enduring Spaces for Tomorrow")}</h1>

            <p>
              {t(
                "Discover thoughtfully developed spaces designed around quality, purpose, and lasting value."
              )}
            </p>

            <div className="project-hero-line" />
          </div>
        </section>

        {/* =========================
            INTRO
        ========================= */}

        <section className="project-intro">
          <span className="project-section-eyebrow">
            {t("OUR APPROACH")}
          </span>

          <h2>{t("Thoughtful Development")}</h2>

          <p>
            {t(
              "We create enduring spaces that balance thoughtful design, functionality, and long-term value."
            )}
          </p>
        </section>

        {/* =========================
            PROPERTIES
        ========================= */}

        <section className="project-properties">
          <div className="project-properties-header">
            <span className="project-eyebrow">
              {t("OUR PROJECTS")}
            </span>

            <h2>{t("Discover Our Properties")}</h2>

            <p>
              {t(
                "Explore the properties currently available across the IUNU platform."
              )}
            </p>
          </div>

          {/* =========================
              LOADING
          ========================= */}

          {loading && (
            <div className="project-loading">
              <div className="project-spinner" />

              <p>{t("Loading properties...")}</p>
            </div>
          )}

          {/* =========================
              ERROR
          ========================= */}

          {!loading && error && (
            <div className="project-message project-error">
              <span>{t("ERROR")}</span>

              <h3>{t("Unable to Load Properties")}</h3>

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
                <span>{t("PROJECTS")}</span>

                <h3>{t("No Properties Available")}</h3>

                <p>
                  {t(
                    "There are currently no published properties available."
                  )}
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
                            {t("VIEW PROPERTY")}
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
                                ).toLocaleString()} ${t("EGP")}`
                              : t("Price on request")}
                          </strong>

                          <span className="project-property-view">
                            {t("EXPLORE")} →
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

            <h3>{t("Legacy Inspired Design")}</h3>

            <p>
              {t(
                "Our projects prioritize community needs, thoughtful design, and sustainability."
              )}
            </p>
          </div>

          <div className="project-feature">
            <span className="project-feature-number">
              02
            </span>

            <h3>{t("Confident Project Delivery")}</h3>

            <p>
              {t(
                "Experience reliable development with a focus on quality and integrity."
              )}
            </p>
          </div>

          <div className="project-feature">
            <span className="project-feature-number">
              03
            </span>

            <h3>{t("Enduring Spaces")}</h3>

            <p>
              {t(
                "Creating impactful spaces that reflect purpose and longevity."
              )}
            </p>
          </div>
        </section>

        {/* =========================
            CONTACT
        ========================= */}

        <section className="project-contact">
          <span className="project-section-eyebrow">
            {t("CONNECT WITH US")}
          </span>

          <h2>{t("Get in Touch Today")}</h2>

          <p>
            {t(
              "Reach out to us to discuss your real estate needs."
            )}
          </p>

          <form
            className="project-newsletter"
            onSubmit={(event) =>
              event.preventDefault()
            }
          >
            <input
              type="email"
              placeholder={t("Email")}
              aria-label={t("Email address")}
            />

            <button type="submit">
              {t("SIGN UP")}
            </button>
          </form>
        </section>
      </main>

      <Footer />
    </div>
  );
}

export default Project;