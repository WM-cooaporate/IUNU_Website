import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import adminServices from "../../services/adminServices";
import authServices from "../../services/authServices";
import "./AdminDashboard.css";

const emptyForm = {
  title: "",
  description: "",
  type: "RESIDENTIAL",
  status: "AVAILABLE",
  location: "",
  area: "",
  price: "",
  coverImageUrl: "",
  imageUrls: "",
  published: true,
};

const formatDate = (value) =>
  value
    ? new Intl.DateTimeFormat("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      }).format(new Date(value))
    : "-";

const getStoredUser = () => {
  try {
    return JSON.parse(localStorage.getItem("user") || "null");
  } catch {
    return null;
  }
};

function AdminLogin({ onLogin }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      const result = await authServices.login({ email, password });
      if (result.user?.role !== "ADMIN") {
        authServices.logout();
        throw new Error("This account does not have administrator access.");
      }
      onLogin(result.user);
    } catch (requestError) {
      setError(
        requestError.response?.data?.message ||
          requestError.message ||
          "Unable to sign in. Check your credentials."
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="admin-auth-page">
      <section className="admin-auth-card">
        <span className="admin-eyebrow">IUNU DEVELOPMENTS</span>
        <h1>Admin Portal</h1>
        <p>Sign in to manage the projects shown on the website.</p>
        {error && <div className="admin-alert admin-alert-error">{error}</div>}
        <form className="admin-auth-form" onSubmit={handleSubmit}>
          <label>
            Email
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          </label>
          <button className="save-button" type="submit" disabled={submitting}>
            {submitting ? "Signing in..." : "Sign in"}
          </button>
        </form>
        <Link className="admin-back-link" to="/home">Back to website</Link>
      </section>
    </main>
  );
}

function AdminDashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(getStoredUser);
  const [properties, setProperties] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [search, setSearch] = useState("");
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState(emptyForm);

  const loadProperties = async () => {
    setLoading(true);
    setError("");
    try {
      const data = await adminServices.getProperties();
      setProperties(data.content || []);
    } catch (requestError) {
      if (requestError.response?.status === 401 || requestError.response?.status === 403) {
        authServices.logout();
        setUser(null);
      }
      setError(requestError.response?.data?.message || "Unable to load projects from the server.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user?.role !== "ADMIN") return undefined;

    const timeoutId = window.setTimeout(() => {
      loadProperties();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [user]);

  const filteredProperties = useMemo(() => {
    const query = search.trim().toLowerCase();
    return query
      ? properties.filter((property) =>
          [property.title, property.location, property.type, property.status]
            .filter(Boolean)
            .join(" ")
            .toLowerCase()
            .includes(query)
        )
      : properties;
  }, [properties, search]);

  const stats = {
    total: properties.length,
    published: properties.filter((property) => property.published).length,
    drafts: properties.filter((property) => !property.published).length,
  };

  const openCreate = () => {
    setForm(emptyForm);
    setModal("create");
    setError("");
  };

  const openEdit = (property) => {
    setForm({
      title: property.title || "",
      description: property.description || "",
      type: property.type || "RESIDENTIAL",
      status: property.status || "AVAILABLE",
      location: property.location || "",
      area: property.area ?? "",
      price: property.price ?? "",
      coverImageUrl: property.coverImageUrl || "",
      imageUrls: (property.imageUrls || []).join("\n"),
      published: property.published !== false,
    });
    setModal({ type: "edit", id: property.id });
    setError("");
  };

  const updateField = (event) => {
    const { name, value, type, checked } = event.target;
    setForm((current) => ({ ...current, [name]: type === "checkbox" ? checked : value }));
  };

  const handleSave = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    setSuccess("");
    const payload = {
      title: form.title.trim(),
      description: form.description.trim(),
      type: form.type,
      status: form.status,
      location: form.location.trim(),
      area: form.area === "" ? null : Number(form.area),
      price: form.price === "" ? null : Number(form.price),
      coverImageUrl: form.coverImageUrl.trim() || null,
      imageUrls: form.imageUrls.split("\n").map((url) => url.trim()).filter(Boolean),
      published: form.published,
    };

    try {
      if (modal?.type === "edit") await adminServices.updateProperty(modal.id, payload);
      else await adminServices.createProperty(payload);
      await loadProperties();
      setModal(null);
      setSuccess(modal?.type === "edit" ? "Project updated successfully." : "Project created successfully.");
    } catch (requestError) {
      setError(requestError.response?.data?.message || "Unable to save this project.");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (property) => {
    if (!window.confirm(`Delete ${property.title}? This cannot be undone.`)) return;
    setError("");
    setSuccess("");
    try {
      await adminServices.deleteProperty(property.id);
      setProperties((current) => current.filter((item) => item.id !== property.id));
      setSuccess("Project deleted successfully.");
    } catch (requestError) {
      setError(requestError.response?.data?.message || "Unable to delete this project.");
    }
  };

  const handleLogout = () => {
    authServices.logout();
    setUser(null);
    navigate("/admin");
  };

  if (!user || user.role !== "ADMIN") return <AdminLogin onLogin={setUser} />;

  return (
    <div className="admin-page">
      <header className="admin-header">
        <div className="admin-brand">
          <Link to="/home" className="admin-logo-link"><img src="/images/Logo.png" alt="IUNU Developments" /></Link>
          <span className="admin-brand-divider" />
          <div><h1>Projects</h1><p>Manage the public project catalogue</p></div>
        </div>
        <div className="admin-user">
          <div className="admin-user-info"><span className="admin-user-name">{user.fullName || user.email}</span><span className="admin-user-role">ADMINISTRATOR</span></div>
          <button className="logout-button" type="button" onClick={handleLogout}>Log out</button>
        </div>
      </header>

      <main className="admin-content">
        {error && <div className="admin-alert admin-alert-error">{error}</div>}
        {success && <div className="admin-alert admin-alert-success">{success}</div>}
        <div className="admin-page-heading"><div><span className="admin-eyebrow">CONTENT MANAGEMENT</span><h2>Projects</h2><p>Create, update and publish the projects visitors see.</p></div><button className="add-property-button" type="button" onClick={openCreate}><span>+</span> Add project</button></div>
        <section className="admin-stats">
          <div className="admin-stat-card"><span>Total projects</span><strong>{stats.total}</strong></div>
          <div className="admin-stat-card"><span>Published projects</span><strong>{stats.published}</strong></div>
          <div className="admin-stat-card"><span>Draft projects</span><strong>{stats.drafts}</strong></div>
        </section>
        <section className="admin-properties">
          <div className="admin-section-header"><div><span className="admin-eyebrow">PROJECT CATALOGUE</span><h2>All projects</h2><p>{filteredProperties.length} project{filteredProperties.length === 1 ? "" : "s"} shown</p></div><input className="admin-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search projects..." aria-label="Search projects" /></div>
          {loading ? <div className="admin-loading"><div className="admin-spinner" /><p>Loading projects...</p></div> : filteredProperties.length === 0 ? <div className="empty-properties"><h3>No projects found</h3><p>Create your first project or try another search.</p><button className="add-property-button" type="button" onClick={openCreate}>Add project</button></div> : <div className="properties-table-wrapper"><table className="properties-table"><thead><tr><th>Project</th><th>Location</th><th>Area</th><th>Status</th><th>Published</th><th>Created</th><th>Actions</th></tr></thead><tbody>{filteredProperties.map((property) => <tr key={property.id}><td><div className="property-name">{property.coverImageUrl ? <img src={property.coverImageUrl} alt="" /> : <div className="property-thumb-placeholder">I</div>}<div><strong>{property.title}</strong><span>{property.type}</span></div></div></td><td>{property.location || "-"}</td><td>{property.area != null ? `${Number(property.area).toLocaleString()} m²` : "-"}</td><td><span className={`status-badge status-${property.status?.toLowerCase()}`}>{property.status?.replaceAll("_", " ")}</span></td><td><span className={property.published ? "published-yes" : "published-no"}>{property.published ? "Published" : "Draft"}</span></td><td>{formatDate(property.createdAt)}</td><td><div className="property-actions"><Link className="view-button" to={`/project/${property.id}`} target="_blank">View</Link><button className="edit-button" type="button" onClick={() => openEdit(property)}>Edit</button><button className="delete-button" type="button" onClick={() => handleDelete(property)}>Delete</button></div></td></tr>)}</tbody></table></div>}
        </section>
      </main>

      {modal && <div className="admin-modal-overlay" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setModal(null); }}><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="project-form-title"><div className="admin-modal-header"><div><span className="admin-eyebrow">{modal.type === "edit" ? "UPDATE PROJECT" : "NEW PROJECT"}</span><h2 id="project-form-title">{modal.type === "edit" ? "Edit project" : "Add project"}</h2></div><button className="modal-close" type="button" onClick={() => setModal(null)} aria-label="Close form">×</button></div><form className="property-form" onSubmit={handleSave}><div className="form-grid"><label className="form-field"><span>Project name *</span><input name="title" value={form.title} onChange={updateField} placeholder="IUNU Residence" required maxLength={200} /></label><label className="form-field"><span>Location</span><input name="location" value={form.location} onChange={updateField} placeholder="New Cairo" maxLength={200} /></label><label className="form-field form-field-full"><span>Description</span><textarea name="description" value={form.description} onChange={updateField} placeholder="Describe the project..." rows="5" maxLength={20000} /></label><label className="form-field"><span>Area of unit (m²)</span><input name="area" type="number" min="0" step="0.01" value={form.area} onChange={updateField} placeholder="120000" /></label><label className="form-field"><span>Project type *</span><select name="type" value={form.type} onChange={updateField}><option value="RESIDENTIAL">Residential</option><option value="COMMERCIAL">Commercial</option><option value="ADMINISTRATIVE">Administrative</option></select></label><label className="form-field"><span>Status</span><select name="status" value={form.status} onChange={updateField}><option value="AVAILABLE">Available</option><option value="COMING_SOON">Coming soon</option><option value="SOLD_OUT">Sold out</option></select></label><label className="form-field"><span>Price (optional)</span><input name="price" type="number" min="0" step="0.01" value={form.price} onChange={updateField} placeholder="Price on request" /></label><label className="form-field form-field-full"><span>Cover image URL</span><input name="coverImageUrl" type="url" value={form.coverImageUrl} onChange={updateField} placeholder="https://.../cover.jpg" /></label><label className="form-field form-field-full"><span>Project photos</span><textarea name="imageUrls" value={form.imageUrls} onChange={updateField} placeholder="Add one image URL per line" rows="4" /><small>Use one public JPG or PNG URL per line. The first image is used as the cover if no cover is provided.</small></label><label className="form-checkbox"><input name="published" type="checkbox" checked={form.published} onChange={updateField} /><span>Publish this project on the website</span></label></div><div className="admin-modal-actions"><button className="cancel-button" type="button" onClick={() => setModal(null)}>Cancel</button><button className="save-button" type="submit" disabled={saving}>{saving ? "Saving..." : modal.type === "edit" ? "Update project" : "Save project"}</button></div></form></section></div>}
    </div>
  );
}

export default AdminDashboard;
