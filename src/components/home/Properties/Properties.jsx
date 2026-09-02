import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import propertyServices from "../../../services/propertyServices";
import demoProperties from "../../../data/demoProperties";
import { useLanguage } from "../../../i18n/LanguageContext";
import "./Properties.css";

function Properties() {
  const { t } = useLanguage();
  const [properties, setProperties] = useState(demoProperties);

  const sectionRef = useRef(null);

  useEffect(() => {
    const loadProperties = async () => {
      try {
        const demoMode =
          localStorage.getItem("adminDemoMode") === "true";

        const data = await propertyServices.getAllProperties();

        console.log(
          "Properties API:",
          JSON.stringify(data, null, 2)
        );

        setProperties(
          demoMode
            ? demoProperties
            : data.length
              ? data
              : demoProperties
        );
      } catch (error) {
        console.error("Properties error:", error);
        setProperties(demoProperties);
      }
    };

    loadProperties();
  }, []);

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

  if (properties.length === 0) {
    return (
      <section className="properties-section">
        <div className="properties-empty">
          No properties available.
        </div>
      </section>
    );
  }

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
                  property.coverImageUrl ||
                  "/images/hh.jpg"
                }
                alt={property.title}
                className="property-image"
                loading="lazy"
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
              <div className="property-location">
                {property.location}
              </div>

              <h3>{property.title}</h3>

              <p>{property.description}</p>
            </div>
          </article>
        ))}
      </div>

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