import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import propertyServices from "../../services/propertyServices";
import demoProperties from "../../data/demoProperties";

import { useLanguage } from "../../i18n/LanguageContext";
import {
  showPlaceholderOnError,
} from "../../utils/imageUrl";

import "./PropertyDetails.css";

function PropertyDetails() {
  const { id } = useParams();
  const { t, localize } = useLanguage();

  const demoProperty = demoProperties.find(
    (item) => item.id === id
  );

  const [property, setProperty] = useState(
    demoProperty || null
  );

  const [loading, setLoading] = useState(!demoProperty);
  // "notFound" | "failed" | "" - translated at render, so switching language
  // does not refetch the property.
  const [error, setError] = useState("");
  // Bumped by "Try again" to re-run the load effect.
  const [attempt, setAttempt] = useState(0);

  const [selectedImage, setSelectedImage] = useState(
    demoProperty?.coverImageUrl ||
      demoProperty?.imageUrls?.[0] ||
      ""
  );

  useEffect(() => {
    let cancelled = false;

    const loadProperty = async () => {
      try {
        if (!demoProperty) {
          setLoading(true);
        }

        setError("");

        const data =
          await propertyServices.getPropertyById(id);

        if (!cancelled) {
          setProperty(data);

          if (data?.coverImageUrl) {
            setSelectedImage(data.coverImageUrl);
          } else if (data?.imageUrls?.length > 0) {
            setSelectedImage(data.imageUrls[0]);
          }
        }
      } catch (error) {
        console.error(
          "Property details error:",
          error
        );

        if (!cancelled) {
          if (demoProperty) {
            setProperty(demoProperty);
            setError("");

            setSelectedImage(
              demoProperty.coverImageUrl ||
                demoProperty.imageUrls?.[0] ||
                ""
            );
          } else if (
            error.response?.status === 404
          ) {
            setError("notFound");
          } else {
            setError("failed");
          }
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    loadProperty();

    return () => {
      cancelled = true;
    };
  }, [id, demoProperty, attempt]);

  if (loading) {
    return (
      <div className="property-details-page">
        <Navbar />

        <div className="property-details-loading">
          <div className="property-details-spinner" />

          <p>
            {t("Loading property...")}
          </p>
        </div>

        <Footer />
      </div>
    );
  }

  if (error || !property) {
    return (
      <div className="property-details-page">
        <Navbar />

        <main className="property-details-error">
          <span>
            {t("PROPERTY")}
          </span>

          <h1>
            {error === "failed"
              ? t("Failed to load property details.")
              : t("Property not found.")}
          </h1>

          {error === "failed" && (
            <button
              type="button"
              className="property-details-retry"
              onClick={() => setAttempt((count) => count + 1)}
            >
              {t("TRY AGAIN")}
            </button>
          )}

          <Link to="/project">
            ← {t("Back to Properties")}
          </Link>
        </main>

        <Footer />
      </div>
    );
  }

  const galleryImages = [
    ...(property.coverImageUrl
      ? [property.coverImageUrl]
      : []),
    ...(property.imageUrls || []),
  ].filter(
    (image, index, array) =>
      image &&
      array.indexOf(image) === index
  );

  const formattedStatus =
    property.status?.replaceAll("_", " ");

  return (
    <div className="property-details-page">
      <Navbar />

      <main>
        {/* =========================
            HERO
        ========================= */}

        <section className="property-details-hero">
          <div className="property-details-hero-content">
            <Link
              to="/project"
              className="property-back-link"
            >
              ← {t("Back to Properties")}
            </Link>

            <span className="property-details-eyebrow">
              {property.type}
            </span>

            <h1 dir="auto">
              {localize(property, "title")}
            </h1>

            {localize(property, "location") && (
              <p dir="auto">
                {localize(property, "location")}
              </p>
            )}
          </div>
        </section>

        {/* =========================
            PROPERTY DETAILS
        ========================= */}

        <section className="property-details-section">
          <div className="property-details-container">
            {/* GALLERY */}

            <div className="property-gallery">
              <div className="property-main-image">
                {selectedImage ? (
                  <img
                    // Keyed on the image so switching thumbnails gets a
                    // fresh element - and a fresh onError guard - each time.
                    key={selectedImage}
                    src={selectedImage}
                    alt={localize(property, "title")}
                    loading="eager"
                    fetchPriority="high"
                    decoding="async"
                    onError={showPlaceholderOnError}
                  />
                ) : (
                  <div className="property-no-image">
                    IUNU
                  </div>
                )}

                {property.status && (
                  <span className="property-details-status">
                    {t(formattedStatus || "")}
                  </span>
                )}
              </div>

              {galleryImages.length > 1 && (
                <div className="property-thumbnails">
                  {galleryImages.map(
                    (image, index) => (
                      <button
                        type="button"
                        key={`${image}-${index}`}
                        className={
                          selectedImage === image
                            ? "property-thumbnail active"
                            : "property-thumbnail"
                        }
                        onClick={() =>
                          setSelectedImage(image)
                        }
                      >
                        <img
                          src={image}
                          alt={`${localize(property, "title")} ${
                            index + 1
                          }`}
                          loading="lazy"
                          decoding="async"
                          onError={showPlaceholderOnError}
                        />
                      </button>
                    )
                  )}
                </div>
              )}
            </div>

            {/* INFORMATION */}

            <div className="property-information">
              <span className="property-information-type">
                {property.type}
              </span>

              <h2 dir="auto">
                {localize(property, "title")}
              </h2>

              {localize(property, "location") && (
                <div className="property-information-location">
                  <span>
                    {t("LOCATION")}
                  </span>

                  <strong dir="auto">
                    {localize(property, "location")}
                  </strong>
                </div>
              )}

              <div className="property-information-price">
                <span>
                  {t("PRICE")}
                </span>

                <strong>
                  {property.price != null
                    ? `${Number(
                        property.price
                      ).toLocaleString()} EGP`
                    : t("Price on request")}
                </strong>
              </div>

              <div className="property-information-divider" />

              <div className="property-information-description">
                <span>
                  {t("DESCRIPTION")}
                </span>

                <p dir="auto">
                  {localize(property, "description") ||
                    t(
                      "No description available for this property."
                    )}
                </p>
              </div>

              <Link
                to={`/contact?property=${property.id}`}
                className="property-contact-button"
              >
                {t("CONTACT US")}
              </Link>
            </div>
          </div>
        </section>

        {/* =========================
            PROPERTY INFORMATION
        ========================= */}

        <section className="property-extra-section">
          <div className="property-extra-container">
            <div className="property-extra-item">
              <span>
                {t("PROPERTY TYPE")}
              </span>

              <strong>
                {property.type}
              </strong>
            </div>

            <div className="property-extra-item">
              <span>
                {t("STATUS")}
              </span>

              <strong>
                {t(formattedStatus || "")}
              </strong>
            </div>

            <div className="property-extra-item">
              <span>
                {t("LOCATION")}
              </span>

              <strong dir="auto">
                {localize(property, "location") ||
                  t("Not specified")}
              </strong>
            </div>

            <div className="property-extra-item">
              <span>
                {t("PROPERTY ID")}
              </span>

              <strong>
                #{property.id}
              </strong>
            </div>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}

export default PropertyDetails;