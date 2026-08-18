
import { useCallback, useEffect, useState } from "react";
import adminServices from "../../services/adminServices";
import "./AdminDashboard.css";

const emptyForm = {
  title: "",
  description: "",
  type: "RESIDENTIAL",
  status: "AVAILABLE",
  location: "",
  price: "",
  coverImageUrl: "",
  imageUrls: "",
  published: true,
};

function AdminDashboard() {
  // =========================
  // STATE
  // =========================

  const [properties, setProperties] = useState([]);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState(null);

  const [formData, setFormData] = useState({
    ...emptyForm,
  });

  // =========================
  // LOAD PROPERTIES
  // =========================

  const loadProperties = useCallback(async () => {
    try {
      setLoading(true);
      setError("");

      const data = await adminServices.getProperties();

      console.log(
        "Admin Properties:",
        JSON.stringify(data, null, 2)
      );

      setProperties(data.content || []);
    } catch (error) {
      console.error("Admin properties error:", error);

      if (error.response?.status === 401) {
        setError(
          "Your session has expired. Please login again."
        );
      } else if (error.response?.status === 403) {
        setError(
          "You do not have permission to access this page."
        );
      } else {
        setError("Failed to load properties.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // =========================
  // INITIAL LOAD
  // =========================

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadProperties();
  }, [loadProperties]);

  // =========================
  // FORM CHANGE
  // =========================

  const handleChange = (event) => {
    const {
      name,
      value,
      type,
      checked,
    } = event.target;

    setFormData((previous) => ({
      ...previous,
      [name]:
        type === "checkbox"
          ? checked
          : value,
    }));
  };

  // =========================
  // OPEN ADD MODAL
  // =========================

  const handleAdd = () => {
    setEditingId(null);

    setFormData({
      ...emptyForm,
    });

    setError("");
    setSuccess("");

    setShowModal(true);
  };

  // =========================
  // OPEN EDIT MODAL
  // =========================

  const handleEdit = async (id) => {
    try {
      setError("");
      setSuccess("");

      const property =
        await adminServices.getPropertyById(id);

      setEditingId(id);

      setFormData({
        title: property.title || "",

        description:
          property.description || "",

        type:
          property.type || "RESIDENTIAL",

        status:
          property.status || "AVAILABLE",

        location:
          property.location || "",

        price:
          property.price ?? "",

        coverImageUrl:
          property.coverImageUrl || "",

        imageUrls:
          property.imageUrls
            ? property.imageUrls.join("\n")
            : "",

        published:
          property.published ?? true,
      });

      setShowModal(true);
    } catch (error) {
      console.error(
        "Get property error:",
        error
      );

      if (error.response?.status === 401) {
        setError(
          "Your session has expired. Please login again."
        );
      } else if (error.response?.status === 403) {
        setError(
          "You do not have permission to access this property."
        );
      } else {
        setError(
          "Failed to load property details."
        );
      }
    }
  };

  // =========================
  // CLOSE MODAL
  // =========================

  const closeModal = () => {
    if (saving) {
      return;
    }

    setShowModal(false);
    setEditingId(null);

    setFormData({
      ...emptyForm,
    });
  };

  // =========================
  // SAVE PROPERTY
  // =========================

  const handleSubmit = async (event) => {
    event.preventDefault();

    setError("");
    setSuccess("");

    // Validate title
    if (!formData.title.trim()) {
      setError(
        "Property title is required."
      );
      return;
    }

    // Validate type
    if (!formData.type) {
      setError(
        "Property type is required."
      );
      return;
    }

    try {
      setSaving(true);

      // Prepare data for backend
      const propertyData = {
        title:
          formData.title.trim(),

        description:
          formData.description.trim(),

        type:
          formData.type,

        status:
          formData.status,

        location:
          formData.location.trim(),

        price:
          formData.price === ""
            ? null
            : Number(formData.price),

        coverImageUrl:
          formData.coverImageUrl.trim(),

        imageUrls:
          formData.imageUrls
            .split("\n")
            .map((url) => url.trim())
            .filter(
              (url) => url !== ""
            ),

        published:
          formData.published,
      };

      // =========================
      // UPDATE
      // =========================

      if (editingId) {
        await adminServices.updateProperty(
          editingId,
          propertyData
        );

        setSuccess(
          "Property updated successfully."
        );
      }

      // =========================
      // CREATE
      // =========================

      else {
        await adminServices.createProperty(
          propertyData
        );

        setSuccess(
          "Property created successfully."
        );
      }

      // Close modal
      closeModal();

      // Reload properties
      await loadProperties();

    } catch (error) {
      console.error(
        "Save property error:",
        error
      );

      if (
        error.response?.data?.message
      ) {
        setError(
          error.response.data.message
        );
      } else {
        setError(
          editingId
            ? "Failed to update property."
            : "Failed to create property."
        );
      }
    } finally {
      setSaving(false);
    }
  };

  // =========================
  // DELETE PROPERTY
  // =========================

  const handleDelete = async (id) => {
    const confirmed =
      window.confirm(
        "Are you sure you want to delete this property?"
      );

    if (!confirmed) {
      return;
    }

    try {
      setError("");
      setSuccess("");

      await adminServices.deleteProperty(id);

      setSuccess(
        "Property deleted successfully."
      );

      await loadProperties();

    } catch (error) {
      console.error(
        "Delete property error:",
        error
      );

      if (
        error.response?.data?.message
      ) {
        setError(
          error.response.data.message
        );
      } else {
        setError(
          "Failed to delete property."
        );
      }
    }
  };

  // =========================
  // LOGOUT
  // =========================

  const handleLogout = () => {
    localStorage.removeItem(
      "accessToken"
    );

    localStorage.removeItem(
      "refreshToken"
    );

    localStorage.removeItem(
      "user"
    );

    window.location.href =
      "/login";
  };

  // =========================
  // STATS
  // =========================

  const totalProperties =
    properties.length;

  const publishedProperties =
    properties.filter(
      (property) =>
        property.published
    ).length;

  const availableProperties =
    properties.filter(
      (property) =>
        property.status ===
        "AVAILABLE"
    ).length;

  // =========================
  // RENDER
  // =========================

  return (
    <div className="admin-page">

      {/* =========================
          HEADER
      ========================= */}

      <header className="admin-header">

        <div className="admin-brand">

          <span className="admin-brand-name">
            IUNU
          </span>

          <div className="admin-brand-divider" />

          <div>
            <h1>
              Admin Dashboard
            </h1>

            <p>
              Real Estate Management
            </p>
          </div>

        </div>

        <div className="admin-user">

          <div className="admin-user-info">

            <span className="admin-user-name">
              Administrator
            </span>

            <span className="admin-user-role">
              ADMIN
            </span>

          </div>

          <button
            className="logout-button"
            onClick={handleLogout}
          >
            Logout
          </button>

        </div>

      </header>

      {/* =========================
          MAIN CONTENT
      ========================= */}

      <main className="admin-content">

        {/* =========================
            ERROR MESSAGE
        ========================= */}

        {error && (
          <div className="admin-alert admin-alert-error">
            {error}
          </div>
        )}

        {/* =========================
            SUCCESS MESSAGE
        ========================= */}

        {success && (
          <div className="admin-alert admin-alert-success">
            {success}
          </div>
        )}

        {/* =========================
            STATS
        ========================= */}

        <section className="admin-stats">

          <div className="admin-stat-card">

            <span>
              Total Properties
            </span>

            <strong>
              {totalProperties}
            </strong>

          </div>

          <div className="admin-stat-card">

            <span>
              Published
            </span>

            <strong>
              {publishedProperties}
            </strong>

          </div>

          <div className="admin-stat-card">

            <span>
              Available
            </span>

            <strong>
              {availableProperties}
            </strong>

          </div>

        </section>

        {/* =========================
            PROPERTIES
        ========================= */}

        <section className="admin-properties">

          {/* SECTION HEADER */}

          <div className="admin-section-header">

            <div>

              <span className="admin-eyebrow">
                PROPERTY MANAGEMENT
              </span>

              <h2>
                Properties
              </h2>

              <p>
                Manage the properties displayed
                across the IUNU platform.
              </p>

            </div>

            <button
              className="add-property-button"
              onClick={handleAdd}
            >
              <span>+</span>
              Add Property
            </button>

          </div>

          {/* =========================
              LOADING
          ========================= */}

          {loading ? (

            <div className="admin-loading">

              <div className="admin-spinner" />

              <p>
                Loading properties...
              </p>

            </div>

          ) : properties.length === 0 ? (

            /* =========================
               EMPTY STATE
            ========================= */

            <div className="empty-properties">

              <h3>
                No Properties Yet
              </h3>

              <p>
                Add your first property
                to start managing the
                IUNU portfolio.
              </p>

              <button
                className="add-property-button"
                onClick={handleAdd}
              >
                + Add Property
              </button>

            </div>

          ) : (

            /* =========================
               TABLE
            ========================= */

            <div className="properties-table-wrapper">

              <table className="properties-table">

                <thead>

                  <tr>

                    <th>
                      PROPERTY
                    </th>

                    <th>
                      TYPE
                    </th>

                    <th>
                      LOCATION
                    </th>

                    <th>
                      PRICE
                    </th>

                    <th>
                      STATUS
                    </th>

                    <th>
                      VISIBILITY
                    </th>

                    <th>
                      ACTIONS
                    </th>

                  </tr>

                </thead>

                <tbody>

                  {properties.map(
                    (property) => (

                      <tr
                        key={property.id}
                      >

                        {/* PROPERTY */}

                        <td>

                          <div className="property-name">

                            <strong>
                              {property.title}
                            </strong>

                            <span>
                              ID #{property.id}
                            </span>

                          </div>

                        </td>

                        {/* TYPE */}

                        <td>

                          <span className="property-type">
                            {property.type}
                          </span>

                        </td>

                        {/* LOCATION */}

                        <td>
                          {property.location ||
                            "—"}
                        </td>

                        {/* PRICE */}

                        <td>

                          <strong className="property-price">

                            {property.price !=
                            null
                              ? `${Number(
                                  property.price
                                ).toLocaleString()} EGP`
                              : "—"}

                          </strong>

                        </td>

                        {/* STATUS */}

                        <td>

                          <span
                            className={`status-badge status-${property.status?.toLowerCase()}`}
                          >
                            {property.status
                              ?.replace(
                                "_",
                                " "
                              )}
                          </span>

                        </td>

                        {/* VISIBILITY */}

                        <td>

                          <span
                            className={
                              property.published
                                ? "published-yes"
                                : "published-no"
                            }
                          >
                            {property.published
                              ? "Published"
                              : "Hidden"}
                          </span>

                        </td>

                        {/* ACTIONS */}

                        <td>

                          <div className="property-actions">

                            <button
                              className="edit-button"
                              onClick={() =>
                                handleEdit(
                                  property.id
                                )
                              }
                            >
                              Edit
                            </button>

                            <button
                              className="delete-button"
                              onClick={() =>
                                handleDelete(
                                  property.id
                                )
                              }
                            >
                              Delete
                            </button>

                          </div>

                        </td>

                      </tr>

                    )
                  )}

                </tbody>

              </table>

            </div>

          )}

        </section>

      </main>

      {/* =========================
          ADD / EDIT MODAL
      ========================= */}

      {showModal && (

        <div
          className="admin-modal-overlay"
          onMouseDown={(event) => {

            if (
              event.target ===
              event.currentTarget
            ) {
              closeModal();
            }

          }}
        >

          <div className="admin-modal">

            {/* MODAL HEADER */}

            <div className="admin-modal-header">

              <div>

                <span className="admin-eyebrow">

                  {editingId
                    ? "EDIT PROPERTY"
                    : "NEW PROPERTY"}

                </span>

                <h2>

                  {editingId
                    ? "Edit Property"
                    : "Add Property"}

                </h2>

              </div>

              <button
                className="modal-close"
                onClick={closeModal}
                disabled={saving}
              >
                ×
              </button>

            </div>

            {/* FORM */}

            <form
              className="property-form"
              onSubmit={handleSubmit}
            >

              <div className="form-grid">

                {/* TITLE */}

                <div className="form-field form-field-full">

                  <label>
                    Title *
                  </label>

                  <input
                    name="title"
                    value={formData.title}
                    onChange={handleChange}
                    placeholder="Modern Luxury Villa"
                    required
                  />

                </div>

                {/* TYPE */}

                <div className="form-field">

                  <label>
                    Type *
                  </label>

                  <select
                    name="type"
                    value={formData.type}
                    onChange={handleChange}
                  >

                    <option value="RESIDENTIAL">
                      Residential
                    </option>

                    <option value="COMMERCIAL">
                      Commercial
                    </option>

                    <option value="ADMINISTRATIVE">
                      Administrative
                    </option>

                  </select>

                </div>

                {/* STATUS */}

                <div className="form-field">

                  <label>
                    Status
                  </label>

                  <select
                    name="status"
                    value={formData.status}
                    onChange={handleChange}
                  >

                    <option value="AVAILABLE">
                      Available
                    </option>

                    <option value="SOLD_OUT">
                      Sold Out
                    </option>

                    <option value="COMING_SOON">
                      Coming Soon
                    </option>

                  </select>

                </div>

                {/* LOCATION */}

                <div className="form-field">

                  <label>
                    Location
                  </label>

                  <input
                    name="location"
                    value={formData.location}
                    onChange={handleChange}
                    placeholder="Cairo, Egypt"
                  />

                </div>

                {/* PRICE */}

                <div className="form-field">

                  <label>
                    Price (EGP)
                  </label>

                  <input
                    name="price"
                    type="number"
                    min="0"
                    value={formData.price}
                    onChange={handleChange}
                    placeholder="5000000"
                  />

                </div>

                {/* DESCRIPTION */}

                <div className="form-field form-field-full">

                  <label>
                    Description
                  </label>

                  <textarea
                    name="description"
                    value={
                      formData.description
                    }
                    onChange={handleChange}
                    placeholder="Property description..."
                    rows="4"
                  />

                </div>

                {/* COVER IMAGE */}

                <div className="form-field form-field-full">

                  <label>
                    Cover Image URL
                  </label>

                  <input
                    name="coverImageUrl"
                    value={
                      formData.coverImageUrl
                    }
                    onChange={handleChange}
                    placeholder="https://example.com/image.jpg"
                  />

                </div>

                {/* GALLERY */}

                <div className="form-field form-field-full">

                  <label>
                    Gallery Image URLs
                  </label>

                  <textarea
                    name="imageUrls"
                    value={
                      formData.imageUrls
                    }
                    onChange={handleChange}
                    placeholder={
                      "One URL per line\nhttps://example.com/image-1.jpg\nhttps://example.com/image-2.jpg"
                    }
                    rows="4"
                  />

                  <small>
                    Add one image URL per line.
                  </small>

                </div>

                {/* PUBLISHED */}

                <div className="form-checkbox">

                  <label>

                    <input
                      type="checkbox"
                      name="published"
                      checked={
                        formData.published
                      }
                      onChange={handleChange}
                    />

                    <span>
                      Publish this property
                    </span>

                  </label>

                </div>

              </div>

              {/* MODAL ACTIONS */}

              <div className="admin-modal-actions">

                <button
                  type="button"
                  className="cancel-button"
                  onClick={closeModal}
                  disabled={saving}
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  className="save-button"
                  disabled={saving}
                >

                  {saving
                    ? "Saving..."
                    : editingId
                    ? "Save Changes"
                    : "Create Property"}

                </button>

              </div>

            </form>

          </div>

        </div>

      )}

    </div>
  );
}

export default AdminDashboard;
