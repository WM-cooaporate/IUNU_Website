import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import propertyServices from "../../services/propertyServices";
import { getDemoProperties } from "../../data/demoPropertyStorage";
import "./PropertyDetails.css";

function PropertyDetails() {
  const { id } = useParams();
  const demoProperty = getDemoProperties().find(
    (item) => item.id === id
  );

  const [property, setProperty] = useState(demoProperty || null);
  const [loading, setLoading] = useState(!demoProperty);
  const [error, setError] = useState("");

  const [selectedImage, setSelectedImage] = useState(
    demoProperty?.coverImageUrl || demoProperty?.imageUrls?.[0] || ""
  );

  useEffect(() => {
    let cancelled = false;

    const loadProperty = async () => {
      try {
        if (!demoProperty) {
          setLoading(true);
        }
        setError("");

        const data = await propertyServices.getPropertyById(id);

        if (!cancelled) {
          setProperty(data);

          if (data.coverImageUrl) {
            setSelectedImage(data.coverImageUrl);
          } else if (data.imageUrls?.length > 0) {
            setSelectedImage(data.imageUrls[0]);
          }
        }
      } catch (error) {
        console.error("Property details error:", error);

        if (!cancelled) {
          if (demoProperty) {
            setProperty(demoProperty);
            setError("");
          } else if (error.response?.status === 404) {
            setError("Property not found.");
          } else {
            setError("Failed to load property details.");
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
  }, [id, demoProperty]);

  if (loading) {
    return (
      <div className="property-details-page">
        <Navbar />

        <div className="property-details-loading">
          <div className="property-details-spinner" />
          <p>Loading property...</p>
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

          <span>PROPERTY</span>

          <h1>
            {error || "Property not found."}
          </h1>

          <Link to="/project">
            ← Back to Properties
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
      image && array.indexOf(image) === index
  );

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
              ← Back to Properties
            </Link>

            <span className="property-details-eyebrow">
              {property.type}
            </span>

            <h1>
              {property.title}
            </h1>

            {property.location && (
              <p>
                {property.location}
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
                    src={selectedImage}
                    alt={property.title}
                  />
                ) : (
                  <div className="property-no-image">
                    IUNU
                  </div>
                )}
<span className="property-details-status">
  {property.status?.replaceAll("_", " ")}
</span>

              </div>

              {galleryImages.length > 1 && (
                <div className="property-thumbnails">

                  {galleryImages.map((image, index) => (

                    <button
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
                        alt={`${property.title} ${index + 1}`}
                      />

                    </button>

                  ))}

                </div>
              )}

            </div>

            {/* INFORMATION */}

            <div className="property-information">

              <span className="property-information-type">
                {property.type}
              </span>

              <h2>
                {property.title}
              </h2>

              {property.location && (
                <div className="property-information-location">
                  <span>LOCATION</span>
                  <strong>
                    {property.location}
                  </strong>
                </div>
              )}

              {property.area != null && (
                <div className="property-information-location">
                  <span>AREA</span>
                  <strong>{Number(property.area).toLocaleString()} m²</strong>
                </div>
              )}

              <div className="property-information-price">

                <span>PRICE</span>

                <strong>
                  {property.price != null
                    ? `${Number(
                        property.price
                      ).toLocaleString()} EGP`
                    : "Price on request"}
                </strong>

              </div>

              <div className="property-information-divider" />

              <div className="property-information-description">

                <span>DESCRIPTION</span>

                <p>
                  {property.description ||
                    "No description available for this property."}
                </p>

              </div>
<Link
  to={`/contact?property=${property.id}`}
  className="property-contact-button"
>
  CONTACT US
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

              <span>PROPERTY TYPE</span>

              <strong>
                {property.type}
              </strong>

            </div>

            <div className="property-extra-item">

              <span>STATUS</span>

              <strong>
                {property.status?.replaceAll("_", " ")}
              </strong>

            </div>

            <div className="property-extra-item">

              <span>LOCATION</span>

              <strong>
                {property.location || "Not specified"}
              </strong>

            </div>

            <div className="property-extra-item">

              <span>PROPERTY ID</span>

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
