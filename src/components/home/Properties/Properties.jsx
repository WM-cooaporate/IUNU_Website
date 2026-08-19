import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import propertyServices from "../../../services/propertyServices";
import "./Properties.css";

function Properties() {
  const [properties, setProperties] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const sectionRef = useRef(null);

  useEffect(() => {
    const loadProperties = async () => {
      try {
        const data = await propertyServices.getProperties();

        console.log(
          "Properties API:",
          JSON.stringify(data, null, 2)
        );

        setProperties(data.content || []);
      } catch (error) {
        console.error("Properties error:", error);
        setError("Failed to load properties.");
      } finally {
        setLoading(false);
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

  if (loading) {
    return (
      <section className="properties-section">
        <div className="properties-loading">
          Loading properties...
        </div>
      </section>
    );
  }

  if (error) {
    return (
      <section className="properties-section">
        <div className="properties-error">
          {error}
        </div>
      </section>
    );
  }

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

      {/* =========================
          HEADER
      ========================= */}

      <div className="properties-header properties-reveal">

        <div className="properties-header-left">

          <span className="properties-eyebrow">
            OUR DEVELOPMENTS
          </span>

          <h2>
            Spaces designed
            <br />
            <em>to belong.</em>
          </h2>

        </div>

        <div className="properties-header-right">

          <p>
            Discover thoughtfully designed destinations
            created around quality, community and
            lasting value.
          </p>

          <span className="properties-header-number">
            03
          </span>

        </div>

      </div>


      {/* =========================
          PROPERTY CARDS
      ========================= */}

      <div className="properties-grid">

        {properties.map((property, index) => (

          <article
            className={`property-card properties-reveal property-card-${index + 1}`}
            key={property.id}
          >

            {/* Image */}

            <Link
              to={`/project/${property.id}`}
              className="property-image-wrapper"
            ><img
  src="/images/hh.jpg"
  alt={property.title}
  className="property-image"
/>

              <div className="property-image-overlay"></div>

              <div className="property-image-top">

                <span>
                  {property.type}
                </span>

                <span>
                  {property.status}
                </span>

              </div>

              <div className="property-image-bottom">

                <span>
                  VIEW PROJECT
                </span>

                <span className="property-arrow">
                  →
                </span>

              </div>

            </Link>


            {/* Content */}

            <div className="property-content">

              <div className="property-location">
                {property.location}
              </div>

              <h3>
                {property.title}
              </h3>

              <p>
                {property.description}
              </p>

            </div>

          </article>

        ))}

      </div>


      {/* =========================
          FOOTER
      ========================= */}

      <div className="properties-footer properties-reveal">

        <span>
          DISCOVER ALL DEVELOPMENTS
        </span>

        <Link to="/project">
          VIEW ALL
          <span>→</span>
        </Link>

      </div>

    </section>
  );
}

export default Properties;