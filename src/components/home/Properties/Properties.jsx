import { useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { useProperties } from "../../../hooks/useProperties";
import { toUserMessage } from "../../../services/apiClient";
import { useLanguage } from "../../../i18n/LanguageContext";
import {
  imageSrcSet,
  imageUrl,
  PLACEHOLDER_IMAGE,
  showPlaceholderOnError,
} from "../../../utils/imageUrl";
import "./Properties.css";

/** Stable empty list, so the reveal effect below does not re-run every render while loading. */
const NO_PROPERTIES = [];

function Properties() {
  const { t, localize } = useLanguage();
  // Same query (and cache entry) as the Projects page. This is a public page:
  // it renders what the public API returns and nothing else - no placeholder
  // catalogue while loading or when the request fails.
  const { data, isPending, isError, error, refetch } = useProperties();
  const properties = data?.content ?? NO_PROPERTIES;

  const sectionRef = useRef(null);

  useEffect(() => {
    const section = sectionRef.current;

    if (!section) return;

    const elements = section.querySelectorAll(
      ".properties-reveal"
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
  }, [properties]);

  return (
    <section
      ref={sectionRef}
      className="properties-section"
    >
      <div className="properties-header properties-reveal">
        <div className="properties-header-left">
          <span className="properties-eyebrow">
            {t("OUR DEVELOPMENTS")}
          </span>

          <h2>
            {t("Spaces designed")}
            <br />
            <em>{t("to belong.")}</em>
          </h2>
        </div>

        <div className="properties-header-right">
          <p>
            {t(
              "Discover thoughtfully designed destinations created around quality, community and lasting value."
            )}
          </p>
        </div>
      </div>

      {isPending && (
        <div className="properties-loading" role="status">
          {t("Loading properties...")}
        </div>
      )}

      {isError && !data && (
        <div className="properties-error" role="alert">
          <p>{toUserMessage(error, t("Unable to Load Properties"))}</p>

          <button
            type="button"
            className="properties-retry"
            onClick={() => refetch()}
          >
            {t("TRY AGAIN")}
          </button>
        </div>
      )}

      {data && properties.length === 0 && (
        <div className="properties-empty">
          {t("No Properties Available")}
        </div>
      )}

      {properties.length > 0 && (
        <div className="properties-grid">
          {properties.map((property, index) => (
            <article
              className={`property-card properties-reveal property-card-${
                index + 1
              }`}
              key={property.id}
            >
              <Link
                to={`/project/${property.id}`}
                className="property-image-wrapper"
              >
                <img
                  src={
                    imageUrl(property.coverImageUrl, 800) ||
                    PLACEHOLDER_IMAGE
                  }
                  srcSet={imageSrcSet(property.coverImageUrl) || undefined}
                  sizes="(max-width: 768px) 100vw, 33vw"
                  alt={localize(property, "title")}
                  className="property-image"
                  loading="lazy"
                  decoding="async"
                  onError={showPlaceholderOnError}
                />

                <div className="property-image-overlay" />

                <div className="property-image-top">
                  <span>{property.type}</span>
                  <span>{property.status}</span>
                </div>

                <div className="property-image-bottom">
                  <span>{t("VIEW PROJECT")}</span>

                  <span className="property-arrow">
                    →
                  </span>
                </div>
              </Link>

              <div className="property-content">
                <div className="property-location" dir="auto">
                  {localize(property, "location")}
                </div>

                <h3 dir="auto">{localize(property, "title")}</h3>

                <p dir="auto">{localize(property, "description")}</p>
              </div>
            </article>
          ))}
        </div>
      )}

      <div className="properties-footer properties-reveal">
        <span>
          {t("DISCOVER ALL DEVELOPMENTS")}
        </span>

        <Link to="/project">
          {t("VIEW ALL")}
          <span>→</span>
        </Link>
      </div>
    </section>
  );
}

export default Properties;