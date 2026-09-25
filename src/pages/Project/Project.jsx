import { useState } from "react";
import { Link } from "react-router-dom";

import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import leadServices from "../../services/leadServices";
import { toUserMessage } from "../../services/apiClient";
import { useProperties } from "../../hooks/useProperties";

import { useLanguage } from "../../i18n/LanguageContext";
import {
  imageSrcSet,
  imageUrl,
  showPlaceholderOnError,
} from "../../utils/imageUrl";

import "./Project.css";

function Project() {
  const { t, localize } = useLanguage();

  // Same query (and cache entry) as the home page's properties section, so
  // arriving here from Home costs no request, and switching language no
  // longer refetches (the old effect depended on t, which changes with it).
  const { data, isPending, isError, error, refetch } = useProperties();
  const properties = data?.content ?? [];

  const [newsletterEmail, setNewsletterEmail] = useState("");
  const [newsletterBusy, setNewsletterBusy] = useState(false);
  const [newsletterStatus, setNewsletterStatus] = useState({ type: "", text: "" });

  const handleNewsletterSubmit = async (event) => {
    event.preventDefault();
    if (newsletterBusy) return; // ignore a second click while the first is in flight

    setNewsletterBusy(true);
    setNewsletterStatus({ type: "", text: "" });

    try {
      await leadServices.subscribeNewsletter(newsletterEmail);
      setNewsletterEmail("");
      setNewsletterStatus({
        type: "success",
        text: t("Thank you. You are subscribed to our updates."),
      });
    } catch (requestError) {
      setNewsletterStatus({
        type: "error",
        text: toUserMessage(
          requestError,
          t("We could not complete your sign up. Please try again.")
        ),
      });
    } finally {
      setNewsletterBusy(false);
    }
  };

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

            <h1>
              {t("Enduring Spaces for Tomorrow")}
            </h1>

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

          <h2>
            {t("Thoughtful Development")}
          </h2>

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

            <h2>
              {t("Discover Our Properties")}
            </h2>

            <p>
              {t(
                "Explore the properties currently available across the IUNU platform."
              )}
            </p>
          </div>

          {/* =========================
              LOADING
          ========================= */}

          {isPending && (
            <div className="project-loading" role="status">
              <div className="project-spinner" />

              <p>
                {t("Loading properties...")}
              </p>
            </div>
          )}

          {/* =========================
              ERROR
          ========================= */}

          {isError && !data && (
            <div className="project-message project-error" role="alert">
              <span>
                {t("ERROR")}
              </span>

              <h3>
                {t("Unable to Load Properties")}
              </h3>

              <p>
                {toUserMessage(error, t("Unable to Load Properties"))}
              </p>

              <button
                type="button"
                className="project-retry"
                onClick={() => refetch()}
              >
                {t("TRY AGAIN")}
              </button>
            </div>
          )}

          {/* =========================
              EMPTY
          ========================= */}

          {data &&
            properties.length === 0 && (
              <div className="project-message project-empty">
                <span>
                  {t("PROJECTS")}
                </span>

                <h3>
                  {t("No Properties Available")}
                </h3>

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

          {data &&
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
                            src={imageUrl(property.coverImageUrl, 800)}
                            srcSet={imageSrcSet(property.coverImageUrl) || undefined}
                            sizes="(max-width: 768px) 100vw, 33vw"
                            alt={localize(property, "title")}
                            loading="lazy"
                            decoding="async"
                            onError={showPlaceholderOnError}
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

                        <h3 dir="auto">
                          {localize(property, "title")}
                        </h3>

                        {localize(property, "location") && (
                          <p className="project-property-location" dir="auto">
                            {localize(property, "location")}
                          </p>
                        )}

                        {localize(property, "description") && (
                          <p className="project-property-description" dir="auto">
                            {localize(property, "description")}
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

            <h3>
              {t("Legacy Inspired Design")}
            </h3>

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

            <h3>
              {t("Confident Project Delivery")}
            </h3>

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

            <h3>
              {t("Enduring Spaces")}
            </h3>

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

          <h2>
            {t("Get in Touch Today")}
          </h2>

          <p>
            {t(
              "Reach out to us to discuss your real estate needs."
            )}
          </p>

          <form
            className="project-newsletter"
            onSubmit={handleNewsletterSubmit}
          >
            <input
              type="email"
              name="email"
              required
              value={newsletterEmail}
              onChange={(event) => setNewsletterEmail(event.target.value)}
              placeholder={t("Email")}
              aria-label={t("Email address")}
            />

            <button type="submit" disabled={newsletterBusy}>
              {newsletterBusy ? t("SIGNING UP...") : t("SIGN UP")}
            </button>
          </form>

          {newsletterStatus.text && (
            <p
              className={`project-newsletter-status project-newsletter-status-${newsletterStatus.type}`}
              role="status"
            >
              {newsletterStatus.text}
            </p>
          )}
        </section>
      </main>

      <Footer />
    </div>
  );
}

export default Project;