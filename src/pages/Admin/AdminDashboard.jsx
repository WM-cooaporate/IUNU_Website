import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import adminServices from "../../services/adminServices";
import authServices from "../../services/authServices";
import {
  getStoredUser,
  isAdminSession,
  onSessionExpired,
  toUserMessage,
} from "../../services/apiClient";
import { getDemoProperties, saveDemoProperties } from "../../data/demoPropertyStorage";
import "./AdminDashboard.css";

const emptyForm = {
  title: "",
  description: "",
  type: "RESIDENTIAL",
  status: "AVAILABLE",
  location: "",
  titleAr: "",
  descriptionAr: "",
  locationAr: "",
  area: "",
  price: "",
  coverImageUrl: "",
  imageUrls: "",
  published: true,
};

/**
 * The three content fields that carry an Arabic copy, English name -> Arabic
 * name. Anything not listed here (type, status, area, price) is either an enum
 * the site translates from its own dictionary or a number.
 */
const ARABIC_FIELDS = { title: "titleAr", description: "descriptionAr", location: "locationAr" };

const NO_ARABIC_TOUCHED = { titleAr: false, descriptionAr: false, locationAr: false };

const formatDate = (value) =>
  value
    ? new Intl.DateTimeFormat("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      }).format(new Date(value))
    : "-";

/**
 * The dashboard is only shown for a stored ADMIN session that still has a
 * token. A stored user object on its own is not enough - that was how an
 * expired session used to render the whole dashboard and then fail every call.
 */
const getAdminUser = () => (isAdminSession() ? getStoredUser() : null);

/**
 * Demo mode renders the dashboard against browser-local sample data with no
 * backend. Nothing saved in it is ever sent to the server.
 *
 * It lives in sessionStorage, not localStorage, so it dies with the tab. It
 * used to persist in localStorage indefinitely, which is how someone could
 * click "Try demo dashboard" once and then, days later, "add a project" that
 * silently went nowhere - the dashboard listed it, the website never showed
 * it, and the save reported success.
 */
const DEMO_MODE_KEY = "adminDemoMode";

const demoModeEnabled = () => {
  try {
    return sessionStorage.getItem(DEMO_MODE_KEY) === "true";
  } catch {
    return false;
  }
};

const setDemoModeFlag = (enabled) => {
  try {
    if (enabled) sessionStorage.setItem(DEMO_MODE_KEY, "true");
    else sessionStorage.removeItem(DEMO_MODE_KEY);
    // Clear the old persistent flag left by earlier versions.
    localStorage.removeItem(DEMO_MODE_KEY);
  } catch {
    /* storage disabled - demo mode simply will not persist */
  }
};

function AdminLogin({ onLogin, onDemo }) {
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
        await authServices.logout();
        throw new Error("This account does not have administrator access.");
      }
      onLogin(result.user);
    } catch (requestError) {
      setError(
        requestError.response
          ? toUserMessage(requestError, "Unable to sign in. Check your credentials.")
          : requestError.message || "Unable to sign in. Check your credentials."
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
        <button className="demo-button" type="button" onClick={onDemo}>Try demo dashboard</button>
        <Link className="admin-back-link" to="/home">Back to website</Link>
      </section>
    </main>
  );
}

function AdminDashboard() {
  const [user, setUser] = useState(getAdminUser);
  const [demoMode, setDemoMode] = useState(demoModeEnabled);
  const [properties, setProperties] = useState(demoMode ? getDemoProperties() : []);
  const [loading, setLoading] = useState(!demoMode);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [search, setSearch] = useState("");
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [imageFiles, setImageFiles] = useState([]);
  /**
   * Which Arabic fields the admin has typed in. An untouched field is cleared
   * whenever its English changes, so the backend re-translates it on save; a
   * touched one is the admin's own wording and is never thrown away.
   */
  const [arTouched, setArTouched] = useState(NO_ARABIC_TOUCHED);
  const [translating, setTranslating] = useState(false);
  const [translationNotice, setTranslationNotice] = useState("");
  const [backfilling, setBackfilling] = useState(false);

  const loadProperties = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await adminServices.getAllProperties();
      setProperties(data);
    } catch (requestError) {
      // A 401 has already cleared the session in the axios interceptor; the
      // subscription below drops us back to the login screen. A 403 means the
      // token is valid but not an admin's, so end the session here too.
      if (requestError.response?.status === 403) {
        await authServices.logout();
        setUser(null);
      }
      setError(toUserMessage(requestError, "Unable to load projects from the server."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (demoMode || user?.role !== "ADMIN") return undefined;

    const timeoutId = window.setTimeout(() => {
      loadProperties();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [user, demoMode, loadProperties]);

  /**
   * Any 401 from any admin call - including one that happens while a save is
   * in flight - sends us straight back to the sign-in screen with an
   * explanation, rather than leaving a dashboard nobody can use on screen.
   */
  useEffect(() => onSessionExpired(() => {
    setUser(null);
    setModal(null);
    setSaving(false);
    setError("Your admin session has expired. Please sign in again.");
  }), []);

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
    setImageFiles([]);
    setArTouched(NO_ARABIC_TOUCHED);
    setTranslationNotice("");
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
      titleAr: property.titleAr || "",
      descriptionAr: property.descriptionAr || "",
      locationAr: property.locationAr || "",
      area: property.area ?? "",
      price: property.price ?? "",
      coverImageUrl: property.coverImageUrl || "",
      imageUrls: (property.imageUrls || []).join("\n"),
      published: property.published !== false,
    });
    setImageFiles([]);
    // Arabic already on the row was either machine-translated or hand-written;
    // either way editing the English should refresh it, so it starts untouched.
    setArTouched(NO_ARABIC_TOUCHED);
    setTranslationNotice("");
    setModal({ type: "edit", id: property.id });
    setError("");
  };

  const updateField = (event) => {
    const { name, value, type, checked } = event.target;
    if (Object.values(ARABIC_FIELDS).includes(name)) setArTouched((current) => ({ ...current, [name]: true }));
    setForm((current) => {
      const next = { ...current, [name]: type === "checkbox" ? checked : value };
      // Editing the English invalidates an Arabic copy nobody has hand-edited.
      // Clearing it is what tells the backend to translate it again on save.
      const arabicField = ARABIC_FIELDS[name];
      if (arabicField && !arTouched[arabicField]) next[arabicField] = "";
      return next;
    });
  };

  /** Fills the three Arabic inputs from the English ones, without saving. */
  const handleTranslatePreview = async () => {
    if (translating) return;
    setTranslating(true);
    setError("");
    setTranslationNotice("");
    try {
      const result = await adminServices.previewTranslation({
        title: form.title.trim(),
        description: form.description.trim(),
        location: form.location.trim(),
      });
      if (result?.enabled === false) {
        setTranslationNotice("Automatic translation is not configured on the server.");
        return;
      }
      setForm((current) => ({ ...current, titleAr: result?.titleAr || "", descriptionAr: result?.descriptionAr || "", locationAr: result?.locationAr || "" }));
      // The admin asked for these values, so they count as theirs: editing the
      // English afterwards must not silently wipe them.
      setArTouched({ titleAr: true, descriptionAr: true, locationAr: true });
    } catch (requestError) {
      setError(toUserMessage(requestError, "Unable to translate this project right now."));
    } finally {
      setTranslating(false);
    }
  };

  const handleBackfillTranslations = async () => {
    if (backfilling) return;
    setBackfilling(true);
    setError("");
    setSuccess("");
    try {
      const result = await adminServices.backfillTranslations();
      if (result?.enabled === false) {
        setError("Automatic translation is not configured on the server.");
        return;
      }
      setSuccess(`Translated ${result?.updated ?? 0} project(s).`);
      await loadProperties();
    } catch (requestError) {
      setError(toUserMessage(requestError, "Unable to translate the existing projects."));
    } finally {
      setBackfilling(false);
    }
  };

  const handleImageFiles = (event) => {
    setImageFiles(Array.from(event.target.files || []));
  };

  const handleSave = async (event) => {
    event.preventDefault();
    if (saving) return; // a second click while the first request is in flight
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      let uploadedImages = [];
      if (imageFiles.length > 0) {
        if (demoMode) {
          throw new Error("Image upload requires the backend. Exit demo mode and sign in to upload local files.");
        }
        uploadedImages = await adminServices.uploadPropertyImages(imageFiles);
      }

      const payload = {
      title: form.title.trim(),
      description: form.description.trim(),
      type: form.type,
      status: form.status,
      location: form.location.trim(),
      // Blank is meaningful: it asks the backend to translate from the English
      // above. A filled value is the admin's own Arabic and is stored as typed.
      titleAr: form.titleAr.trim(),
      descriptionAr: form.descriptionAr.trim(),
      locationAr: form.locationAr.trim(),
      area: form.area === "" ? null : Number(form.area),
      price: form.price === "" ? null : Number(form.price),
      coverImageUrl: uploadedImages[0] || form.coverImageUrl.trim() || null,
      imageUrls: uploadedImages.length > 0
        ? uploadedImages
        : form.imageUrls.split("\n").map((url) => url.trim()).filter(Boolean),
      published: form.published,
      };

      if (demoMode) {
        if (modal?.type === "edit") {
          setProperties((current) => {
            const next = current.map((item) => item.id === modal.id ? { ...item, ...payload, createdAt: item.createdAt } : item);
            saveDemoProperties(next);
            return next;
          });
        } else {
          setProperties((current) => {
            const next = [{ ...payload, id: `demo-admin-${Date.now()}`, createdAt: new Date().toISOString() }, ...current];
            saveDemoProperties(next);
            return next;
          });
        }
      } else {
        if (modal?.type === "edit") await adminServices.updateProperty(modal.id, payload);
        else await adminServices.createProperty(payload);
        await loadProperties();
      }
      setModal(null);
      const action = modal?.type === "edit" ? "Project updated" : "Project created";
      setSuccess(demoMode
        ? `${action} in this browser only. Demo mode does not save to the server, so this will NOT appear on the website. Exit demo and sign in to publish for real.`
        : `${action} successfully.`);
    } catch (requestError) {
      // Errors thrown locally (demo mode) carry no response object.
      setError(
        requestError.response
          ? toUserMessage(requestError, "Unable to save this project.")
          : requestError.message || "Unable to save this project."
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (property) => {
    if (!window.confirm(`Delete ${property.title}? This cannot be undone.`)) return;
    setError("");
    setSuccess("");
    try {
      if (!demoMode) await adminServices.deleteProperty(property.id);
      setProperties((current) => {
        const next = current.filter((item) => item.id !== property.id);
        if (demoMode) saveDemoProperties(next);
        return next;
      });
      setSuccess(demoMode
        ? "Project deleted in this browser only. Demo mode does not save to the server."
        : "Project deleted successfully.");
    } catch (requestError) {
      setError(toUserMessage(requestError, "Unable to delete this project."));
    }
  };

  const handleLogout = async () => {
    await authServices.logout();
    setUser(null);
  };

  const handleDemoMode = async () => {
    await authServices.logout();
    setDemoModeFlag(true);
    setDemoMode(true);
    const demoData = getDemoProperties();
    setProperties(demoData);
    saveDemoProperties(demoData);
    setLoading(false);
  };

  if ((!user || user.role !== "ADMIN") && !demoMode) {
    return (
      <>
        {error && <div className="admin-alert admin-alert-error admin-session-alert">{error}</div>}
        <AdminLogin
          onLogin={(loggedInUser) => {
            setError("");
            setUser(loggedInUser);
          }}
          onDemo={handleDemoMode}
        />
      </>
    );
  }

  return (
    <div className="admin-page">
      <header className="admin-header">
        <div className="admin-brand">
          <Link to="/home" className="admin-logo-link"><img src="/images/Logo.png" alt="IUNU Developments" /></Link>
          <span className="admin-brand-divider" />
          <div><h1>Projects</h1><p>Manage the public project catalogue</p></div>
        </div>
        <div className="admin-user">
          <div className="admin-user-info"><span className="admin-user-name">{demoMode ? "Demo Administrator" : user.fullName || user.email}</span><span className="admin-user-role">{demoMode ? "DEMO MODE" : "ADMINISTRATOR"}</span></div>
          <button className="logout-button" type="button" onClick={demoMode ? () => { setDemoModeFlag(false); setDemoMode(false); } : handleLogout}>{demoMode ? "Exit demo" : "Log out"}</button>
        </div>
      </header>

      <main className="admin-content">
        {demoMode && (
          <div className="admin-demo-banner" role="status">
            <strong>Demo mode.</strong> Everything here is sample data stored in this browser.
            Projects you add are <strong>not saved to the server</strong> and will not appear on
            the website. Exit demo and sign in to manage the real catalogue.
          </div>
        )}
        {error && <div className="admin-alert admin-alert-error">{error}</div>}
        {success && <div className="admin-alert admin-alert-success">{success}</div>}
        <div className="admin-page-heading"><div><span className="admin-eyebrow">CONTENT MANAGEMENT</span><h2>Projects</h2><p>Create, update and publish the projects visitors see.</p></div><div className="admin-heading-actions">{!demoMode && <button className="backfill-button" type="button" onClick={handleBackfillTranslations} disabled={backfilling}>{backfilling ? "Translating..." : "Translate missing Arabic"}</button>}<button className="add-property-button" type="button" onClick={openCreate}><span>+</span> Add project</button></div></div>
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

      {modal && <div className="admin-modal-overlay" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setModal(null); }}><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="project-form-title"><div className="admin-modal-header"><div><span className="admin-eyebrow">{modal.type === "edit" ? "UPDATE PROJECT" : "NEW PROJECT"}</span><h2 id="project-form-title">{modal.type === "edit" ? "Edit project" : "Add project"}</h2></div><button className="modal-close" type="button" onClick={() => setModal(null)} aria-label="Close form">×</button></div><form className="property-form" onSubmit={handleSave}><div className="form-grid"><label className="form-field"><span>Project name *</span><input name="title" value={form.title} onChange={updateField} placeholder="IUNU Residence" required maxLength={200} /></label><label className="form-field"><span>Location</span><input name="location" value={form.location} onChange={updateField} placeholder="New Cairo" maxLength={200} /></label><label className="form-field form-field-full"><span>Description</span><textarea name="description" value={form.description} onChange={updateField} placeholder="Describe the project..." rows="5" maxLength={20000} /></label><fieldset className="form-field form-field-full arabic-fieldset"><legend>Arabic version</legend><p className="arabic-hint">Leave blank to translate automatically from English when you save. You can edit the Arabic before saving.</p><div className="arabic-grid"><label className="form-field"><span>اسم المشروع</span><input name="titleAr" value={form.titleAr} onChange={updateField} placeholder="اسم المشروع" dir="rtl" lang="ar" maxLength={400} /></label><label className="form-field"><span>الموقع</span><input name="locationAr" value={form.locationAr} onChange={updateField} placeholder="الموقع" dir="rtl" lang="ar" maxLength={400} /></label><label className="form-field form-field-full"><span>وصف المشروع</span><textarea name="descriptionAr" value={form.descriptionAr} onChange={updateField} placeholder="وصف المشروع" dir="rtl" lang="ar" rows="5" maxLength={40000} /></label></div><div className="arabic-actions"><button className="translate-button" type="button" onClick={handleTranslatePreview} disabled={translating || demoMode}>{translating ? "Translating..." : "Translate from English"}</button>{demoMode && <small>Translation requires the backend. Exit demo mode to use it.</small>}{translationNotice && <small className="arabic-notice">{translationNotice}</small>}</div></fieldset><label className="form-field"><span>Area of unit (m²)</span><input name="area" type="number" min="0" step="0.01" value={form.area} onChange={updateField} placeholder="120000" /></label><label className="form-field"><span>Project type *</span><select name="type" value={form.type} onChange={updateField}><option value="RESIDENTIAL">Residential</option><option value="COMMERCIAL">Commercial</option><option value="ADMINISTRATIVE">Administrative</option></select></label><label className="form-field"><span>Status</span><select name="status" value={form.status} onChange={updateField}><option value="AVAILABLE">Available</option><option value="COMING_SOON">Coming soon</option><option value="SOLD_OUT">Sold out</option></select></label><label className="form-field"><span>Price (optional)</span><input name="price" type="number" min="0" step="0.01" value={form.price} onChange={updateField} placeholder="Price on request" /></label><label className="form-field form-field-full"><span>Project photos</span><input type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={handleImageFiles} /><small>Select JPG, PNG or WEBP files from your device. The first selected image becomes the cover. Existing images stay unchanged when no new files are selected.{imageFiles.length > 0 ? ` ${imageFiles.length} file${imageFiles.length === 1 ? "" : "s"} selected.` : ""}</small></label><label className="form-checkbox"><input name="published" type="checkbox" checked={form.published} onChange={updateField} /><span>Publish this project on the website</span></label></div><div className="admin-modal-actions"><button className="cancel-button" type="button" onClick={() => setModal(null)}>Cancel</button><button className="save-button" type="submit" disabled={saving}>{saving ? "Saving..." : modal.type === "edit" ? "Update project" : "Save project"}</button></div></form></section></div>}
    </div>
  );
}

export default AdminDashboard;
