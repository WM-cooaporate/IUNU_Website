import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { prepareImage, thumbnailDataUrl } from "../../utils/prepareImage";
import { imageUrl, isAllowedImageUrl, showPlaceholderOnError } from "../../utils/imageUrl";
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
  published: true,
};

/** Matches PropertyRequest.imageUrls @Size(max = 30) on the backend. */
const MAX_IMAGES = 30;

let tileCounter = 0;
const newTileId = () => `tile-${Date.now()}-${tileCounter++}`;

/** A gallery entry that already has a stored URL (existing, pasted, or finished uploading). */
const readyTile = (url) => ({ id: newTileId(), url, status: "ready", progress: 100, error: "", localPreview: "", name: "" });

const isBusy = (tile) => tile.status === "preparing" || tile.status === "uploading";

const formatBytes = (bytes) => (bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)}MB` : `${Math.max(1, Math.round(bytes / 1024))}KB`);

/**
 * The photo gallery inside the project form. Holds no state of its own - the
 * dashboard owns it, because Save needs it - so this is only the markup.
 * Reordering uses buttons rather than drag so it works on a phone.
 */
function GallerySection({ gallery, coverUrl, demoMode, wakeNotice, galleryError, urlInput, onUrlInput, onAddUrl, onAddFiles, onSetCover, onMove, onRemove, onRetry }) {
  const [dragActive, setDragActive] = useState(false);
  const readyUrls = gallery.filter((tile) => tile.status === "ready" && tile.url).map((tile) => tile.url);
  const effectiveCover = coverUrl && readyUrls.includes(coverUrl) ? coverUrl : readyUrls[0] || "";
  const full = gallery.length >= MAX_IMAGES;
  const handleDrop = (event) => { event.preventDefault(); setDragActive(false); if (event.dataTransfer?.files?.length) onAddFiles(event.dataTransfer.files); };

  return (
    <section className={dragActive ? "form-field form-field-full gallery-manager gallery-drag" : "form-field form-field-full gallery-manager"} onDragOver={(event) => { event.preventDefault(); if (!dragActive) setDragActive(true); }} onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setDragActive(false); }} onDrop={handleDrop} aria-label="Project photos">
      <div className="gallery-header"><span>Project photos</span><small>{gallery.length} / {MAX_IMAGES}</small></div>
      {wakeNotice && !demoMode && <p className="gallery-notice" role="status">Waking up the server — the first upload may take a moment.</p>}
      {galleryError && <p className="gallery-error" role="alert">{galleryError}</p>}
      {gallery.length === 0 ? <p className="gallery-empty">No photos yet. Choose files or drop them here. The first photo becomes the cover unless you pick another with the star.</p> : <ul className="gallery-grid">{gallery.map((tile, index) => <li key={tile.id} className={`gallery-tile gallery-tile-${tile.status}`}><div className="gallery-thumb">{tile.url ? <img src={imageUrl(tile.url, 400)} alt={`Photo ${index + 1}`} loading="lazy" decoding="async" onError={showPlaceholderOnError} /> : tile.localPreview ? <img src={tile.localPreview} alt={`Photo ${index + 1}`} /> : <span className="gallery-thumb-name">{tile.name || "Photo"}</span>}{tile.url && tile.url === effectiveCover && <span className="gallery-cover-badge">Cover</span>}{isBusy(tile) && <div className="gallery-progress"><span>{tile.status === "preparing" ? "Preparing..." : `Uploading ${tile.progress}%`}</span><div className="gallery-progress-bar"><div style={{ width: `${tile.status === "uploading" ? tile.progress : 0}%` }} /></div></div>}{tile.status === "error" && <div className="gallery-error-overlay"><span>{tile.error}</span>{tile.file && <button type="button" onClick={() => onRetry(tile.id)}>Retry</button>}</div>}</div>{tile.sizeNote && tile.status !== "error" && <small className="gallery-size">{tile.sizeNote}</small>}<div className="gallery-actions"><button type="button" title="Set as cover" aria-label={`Set photo ${index + 1} as cover`} aria-pressed={tile.url !== "" && tile.url === effectiveCover} disabled={tile.status !== "ready" || tile.url === effectiveCover} onClick={() => onSetCover(tile.url)}>★</button><button type="button" title="Move left" aria-label={`Move photo ${index + 1} left`} disabled={index === 0} onClick={() => onMove(tile.id, -1)}>←</button><button type="button" title="Move right" aria-label={`Move photo ${index + 1} right`} disabled={index === gallery.length - 1} onClick={() => onMove(tile.id, 1)}>→</button><button type="button" title="Remove" aria-label={`Remove photo ${index + 1}`} className="gallery-remove" onClick={() => onRemove(tile.id)}>×</button></div></li>)}</ul>}
      <div className="gallery-add"><label className={demoMode || full ? "gallery-file-button gallery-file-button-disabled" : "gallery-file-button"}>Add photos<input type="file" accept="image/jpeg,image/png,image/webp" multiple disabled={demoMode || full} onChange={(event) => { onAddFiles(event.target.files); event.target.value = ""; }} /></label><div className="gallery-url"><input type="url" value={urlInput} onChange={(event) => onUrlInput(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); onAddUrl(); } }} placeholder="Add Cloudinary image URL (https://res.cloudinary.com/...)" aria-label="Add image by URL" disabled={full} /><button type="button" className="cancel-button" onClick={onAddUrl} disabled={full || !urlInput.trim()}>Add URL</button></div></div>
      <small className="gallery-hint">Photos are resized automatically before upload. JPG, PNG or WebP. iPhone: Settings → Camera → Formats → Most Compatible.</small>
      {demoMode && <small className="gallery-hint">Image upload requires the backend. Exit demo mode and sign in to upload local files. You can still add images by URL.</small>}
    </section>
  );
}

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

/** Audit times are shown in Cairo time whatever the admin's own time zone is. */
const formatCairoTime = (value) =>
  value
    ? new Intl.DateTimeFormat("en-GB", {
        timeZone: "Africa/Cairo",
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }).format(new Date(value))
    : "-";

/** The backend's AuditAction names, in the order the filter lists them. */
const AUDIT_ACTIONS = [
  "LOGIN_SUCCEEDED",
  "PROPERTY_CREATED",
  "PROPERTY_UPDATED",
  "PROPERTY_PUBLISHED",
  "PROPERTY_UNPUBLISHED",
  "PROPERTY_DELETED",
  "IMAGE_UPLOADED",
  "PROJECT_CREATED",
  "PROJECT_UPDATED",
  "PROJECT_DELETED",
  "ADMIN_USER_CREATED",
  "PASSWORD_CHANGED",
  "TRANSLATION_BACKFILL_RUN",
  "IMAGES_MIGRATED",
  "IMAGES_SWEPT",
  "LEAD_MARKED_HANDLED",
];

const AUDIT_PAGE_SIZE = 20;

const humanize = (value) => (value ? value.replaceAll("_", " ").toLowerCase() : "-");

/**
 * Read-only view of who did what in the dashboard. There is nothing to edit
 * here on purpose: a trail an admin can change is not evidence of what that
 * admin did.
 */
function AdminActivity() {
  const [action, setAction] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const result = await adminServices.getAuditLog({
          page,
          size: AUDIT_PAGE_SIZE,
          ...(action ? { action } : {}),
        });
        if (!cancelled) setData(result);
      } catch (requestError) {
        if (!cancelled) setError(toUserMessage(requestError, "Unable to load the activity log."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [action, page]);

  const rows = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <section className="admin-properties">
      <div className="admin-section-header"><div><span className="admin-eyebrow">AUDIT TRAIL</span><h2>Activity</h2><p>Every change made in this dashboard, newest first. Times are Cairo time.</p></div><select className="admin-search" value={action} onChange={(event) => { setAction(event.target.value); setPage(0); }} aria-label="Filter by action"><option value="">All actions</option>{AUDIT_ACTIONS.map((name) => <option key={name} value={name}>{humanize(name)}</option>)}</select></div>
      {error && <div className="admin-alert admin-alert-error">{error}</div>}
      {loading ? <div className="admin-loading"><div className="admin-spinner" /><p>Loading activity...</p></div> : rows.length === 0 ? <div className="empty-properties"><h3>No activity yet</h3><p>Changes made in the dashboard will appear here.</p></div> : <div className="properties-table-wrapper"><table className="properties-table activity-table"><thead><tr><th>Time</th><th>Admin</th><th>Action</th><th>Target</th><th>IP</th></tr></thead><tbody>{rows.map((row) => <tr key={row.id}><td>{formatCairoTime(row.occurredAt)}</td><td>{row.actorEmail || "-"}</td><td><strong>{humanize(row.action)}</strong>{row.summary && <span className="activity-summary">{row.summary}</span>}</td><td>{row.targetType ? `${humanize(row.targetType)}${row.targetId ? ` #${row.targetId}` : ""}` : "-"}</td><td>{row.clientIp || "-"}</td></tr>)}</tbody></table></div>}
      {totalPages > 1 && <div className="activity-pager"><button className="cancel-button" type="button" onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page === 0 || loading}>Previous</button><span>Page {page + 1} of {totalPages}</span><button className="cancel-button" type="button" onClick={() => setPage((current) => current + 1)} disabled={page + 1 >= totalPages || loading}>Next</button></div>}
    </section>
  );
}

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
  /** Ordered photos in the open form: { id, url, status, progress, error, localPreview, name, file?, sizeNote? }. */
  const [gallery, setGallery] = useState([]);
  const [coverUrl, setCoverUrl] = useState("");
  /** URLs uploaded since the form opened - the ones the orphan sweep may later collect if the edit is abandoned. */
  const [sessionUploads, setSessionUploads] = useState([]);
  const [galleryError, setGalleryError] = useState("");
  const [urlInput, setUrlInput] = useState("");
  /** Progress of the current run of uploads, for the "Uploading 2 of 5..." label. */
  const [uploadBatch, setUploadBatch] = useState({ total: 0, done: 0 });
  const [wakeNotice, setWakeNotice] = useState(false);
  const [migrating, setMigrating] = useState(false);
  /** Files waiting to be prepared and uploaded, strictly one at a time. */
  const queueRef = useRef([]);
  const processingRef = useRef(false);
  /** Bumped whenever the form opens or closes, so late results from a closed form are dropped. */
  const formSessionRef = useRef(0);
  /**
   * Which Arabic fields the admin has typed in. An untouched field is cleared
   * whenever its English changes, so the backend re-translates it on save; a
   * touched one is the admin's own wording and is never thrown away.
   */
  const [arTouched, setArTouched] = useState(NO_ARABIC_TOUCHED);
  const [translating, setTranslating] = useState(false);
  const [translationNotice, setTranslationNotice] = useState("");
  const [backfilling, setBackfilling] = useState(false);
  const [tab, setTab] = useState("projects");

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
   * Starts waking the free-plan backend as soon as the dashboard opens, so it
   * is up by the time the admin has picked photos. The notice only appears
   * if the wake takes more than 3s - on a warm server nobody sees it.
   */
  useEffect(() => {
    if (demoMode || user?.role !== "ADMIN") return undefined;
    let settled = false;
    const noticeId = window.setTimeout(() => { if (!settled) setWakeNotice(true); }, 3000);
    adminServices.wakeBackend().finally(() => {
      settled = true;
      window.clearTimeout(noticeId);
      setWakeNotice(false);
    });
    return () => {
      settled = true;
      window.clearTimeout(noticeId);
    };
  }, [user, demoMode]);

  /**
   * Any 401 from any admin call - including one that happens while a save is
   * in flight - sends us straight back to the sign-in screen with an
   * explanation, rather than leaving a dashboard nobody can use on screen.
   */
  useEffect(() => onSessionExpired(() => {
    setUser(null);
    formSessionRef.current += 1;
    queueRef.current = [];
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

  const busyCount = gallery.filter(isBusy).length;

  const stats = {
    total: properties.length,
    published: properties.filter((property) => property.published).length,
    drafts: properties.filter((property) => !property.published).length,
  };

  /** Empties the photo state for a newly opened (or closed) form and orphans any work still in flight. */
  const resetGallery = (tiles = [], cover = "") => {
    formSessionRef.current += 1;
    queueRef.current = [];
    setGallery(tiles);
    setCoverUrl(cover);
    setSessionUploads([]);
    setGalleryError("");
    setUrlInput("");
    setUploadBatch({ total: 0, done: 0 });
  };

  const closeModal = () => {
    resetGallery();
    setModal(null);
  };

  /**
   * Cancel, ×, or a click outside. Photos upload as soon as they are picked,
   * so abandoning the form leaves them stored but unused; say so before
   * throwing the edit away. The nightly sweep removes them after 24h.
   */
  const requestCloseModal = () => {
    const pending = gallery.filter(isBusy).length;
    const unsaved = sessionUploads.length + pending;
    if (unsaved > 0 && !window.confirm(`Discard this edit? ${unsaved} photo(s) you added will not be saved to the project.`)) return;
    closeModal();
  };

  const openCreate = () => {
    setForm(emptyForm);
    resetGallery();
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
      published: property.published !== false,
    });
    // Cover first, then the gallery, each URL once.
    const urls = [property.coverImageUrl, ...(property.imageUrls || [])].filter((url, index, all) => url && all.indexOf(url) === index);
    resetGallery(urls.map(readyTile), property.coverImageUrl || "");
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

  const handleMigrateImages = async () => {
    if (migrating) return;
    setMigrating(true);
    setError("");
    setSuccess("");
    try {
      const result = await adminServices.migrateImagesToCloud();
      if (result?.enabled === false) {
        setError("Cloud image storage is not configured on the server.");
        return;
      }
      const missing = result?.missing || [];
      const titles = [...new Set(missing.map((item) => item.title).filter(Boolean))];
      setSuccess(`Moved ${result?.migrated ?? 0} image(s).${missing.length ? ` ${missing.length} image(s) were already lost — re-upload photos for: ${titles.join(", ")}` : ""}`);
      await loadProperties();
    } catch (requestError) {
      setError(toUserMessage(requestError, "Unable to move the images to the cloud."));
    } finally {
      setMigrating(false);
    }
  };

  const updateTile = (id, patch) => setGallery((current) => current.map((tile) => (tile.id === id ? { ...tile, ...patch } : tile)));

  /** Resize, then upload, one file. Results for a form that has since closed are dropped. */
  const runUpload = async ({ id, file }, session) => {
    const alive = () => session === formSessionRef.current;
    try {
      updateTile(id, { status: "preparing", progress: 0, error: "" });
      const prepared = await prepareImage(file);
      if (!alive()) return;
      let preview = "";
      try {
        preview = await thumbnailDataUrl(prepared.file);
      } catch {
        /* the preview is a nicety; the upload does not depend on it */
      }
      if (!alive()) return;
      updateTile(id, { status: "uploading", progress: 0, localPreview: preview, sizeNote: `${formatBytes(prepared.originalBytes)} → ${formatBytes(prepared.finalBytes)}` });
      const url = await adminServices.uploadPropertyImage(prepared.file, (progress) => { if (alive()) updateTile(id, { progress }); });
      if (!alive()) return;
      setSessionUploads((current) => (current.includes(url) ? current : [...current, url]));
      // Storage is content-addressed: the same photo twice is the same URL, so keep one tile.
      setGallery((current) => (current.some((tile) => tile.id !== id && tile.url === url)
        ? current.filter((tile) => tile.id !== id)
        : current.map((tile) => (tile.id === id ? { ...tile, url, status: "ready", progress: 100, error: "", file: undefined } : tile))));
    } catch (uploadError) {
      if (!alive()) return;
      updateTile(id, { status: "error", error: uploadError?.isAxiosError ? toUserMessage(uploadError, "Upload failed. Please try again.") : uploadError?.message || "Upload failed. Please try again." });
    } finally {
      if (alive()) setUploadBatch((current) => ({ ...current, done: Math.min(current.total, current.done + 1) }));
    }
  };

  /**
   * Works through the queue one file at a time. Sequential on purpose:
   * decoding several 48MP photos at once crashes mobile Safari, and parallel
   * uploads on a phone connection only make each one slower.
   */
  const processQueue = async () => {
    if (processingRef.current) return;
    processingRef.current = true;
    try {
      while (queueRef.current.length > 0) {
        const job = queueRef.current.shift();
        await runUpload(job, formSessionRef.current);
      }
    } finally {
      processingRef.current = false;
    }
  };

  const enqueue = (jobs) => {
    const idle = !gallery.some(isBusy);
    setUploadBatch((current) => (idle ? { total: jobs.length, done: 0 } : { ...current, total: current.total + jobs.length }));
    queueRef.current.push(...jobs);
    processQueue();
  };

  /** Adds picked or dropped files to the end of the gallery - never replaces what is there. */
  const handleAddFiles = (fileList) => {
    if (demoMode) {
      setGalleryError("Image upload requires the backend. Exit demo mode and sign in to upload local files.");
      return;
    }
    const files = Array.from(fileList || []);
    if (files.length === 0) return;
    const room = MAX_IMAGES - gallery.length;
    if (room <= 0) {
      setGalleryError(`A project can have at most ${MAX_IMAGES} images. Remove one to add another.`);
      return;
    }
    setGalleryError(files.length > room ? `A project can have at most ${MAX_IMAGES} images, so only the first ${room} were added.` : "");
    const tiles = files.slice(0, room).map((file) => ({ id: newTileId(), url: "", status: "preparing", progress: 0, error: "", localPreview: "", name: file.name, file }));
    setGallery((current) => [...current, ...tiles]);
    enqueue(tiles.map((tile) => ({ id: tile.id, file: tile.file })));
  };

  const handleRetryTile = (id) => {
    const tile = gallery.find((item) => item.id === id);
    if (!tile?.file) return;
    updateTile(id, { status: "preparing", progress: 0, error: "" });
    enqueue([{ id, file: tile.file }]);
  };

  const handleAddUrl = () => {
    const url = urlInput.trim();
    if (!url) return;
    // The site's CSP only loads images from Cloudinary and the API, so a link
    // to anywhere else would save fine and then never display.
    if (!/^https?:\/\/\S+$/i.test(url) || !isAllowedImageUrl(url)) {
      setGalleryError("Only Cloudinary image links (https://res.cloudinary.com/...) can be added by URL. Use \"Add photos\" to upload anything else.");
      return;
    }
    if (gallery.length >= MAX_IMAGES) {
      setGalleryError(`A project can have at most ${MAX_IMAGES} images. Remove one to add another.`);
      return;
    }
    if (gallery.some((tile) => tile.url === url)) {
      setGalleryError("That image is already in the gallery.");
      return;
    }
    setGalleryError("");
    setGallery((current) => [...current, readyTile(url)]);
    setUrlInput("");
  };

  const handleMoveTile = (id, direction) => setGallery((current) => {
    const index = current.findIndex((tile) => tile.id === id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= current.length) return current;
    const next = [...current];
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  });

  /** Removing only takes the photo off this project; the backend cleans up storage on save. */
  const handleRemoveTile = (id) => {
    const tile = gallery.find((item) => item.id === id);
    if (!tile) return;
    const queued = queueRef.current.some((job) => job.id === id);
    queueRef.current = queueRef.current.filter((job) => job.id !== id);
    if (queued) setUploadBatch((current) => ({ ...current, total: Math.max(current.done, current.total - 1) }));
    if (tile.url && tile.url === coverUrl) setCoverUrl("");
    setGallery((current) => current.filter((item) => item.id !== id));
  };

  const handleSave = async (event) => {
    event.preventDefault();
    if (saving) return; // a second click while the first request is in flight
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      if (gallery.some(isBusy)) {
        throw new Error("Wait for the photos to finish uploading, then save.");
      }
      const readyUrls = gallery.filter((tile) => tile.status === "ready" && tile.url).map((tile) => tile.url);
      if (readyUrls.length > MAX_IMAGES) {
        throw new Error(`A project can have at most ${MAX_IMAGES} images. Remove ${readyUrls.length - MAX_IMAGES} to save.`);
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
      // Photos were uploaded when they were picked; Save only sends URLs.
      coverImageUrl: (coverUrl && readyUrls.includes(coverUrl) ? coverUrl : readyUrls[0]) || null,
      imageUrls: readyUrls,
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
      closeModal();
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
        {wakeNotice && !demoMode && <div className="admin-wake-notice" role="status">Waking up the server — the first upload may take a moment.</div>}
        {error && <div className="admin-alert admin-alert-error">{error}</div>}
        {success && <div className="admin-alert admin-alert-success">{success}</div>}
        <div className="admin-page-heading"><div><span className="admin-eyebrow">CONTENT MANAGEMENT</span><h2>Projects</h2><p>Create, update and publish the projects visitors see.</p></div><div className="admin-heading-actions">{!demoMode && <button className="backfill-button" type="button" onClick={handleBackfillTranslations} disabled={backfilling}>{backfilling ? "Translating..." : "Translate missing Arabic"}</button>}{!demoMode && <button className="backfill-button" type="button" onClick={handleMigrateImages} disabled={migrating}>{migrating ? "Moving images..." : "Move images to cloud"}</button>}<button className="add-property-button" type="button" onClick={openCreate}><span>+</span> Add project</button></div></div>
        <section className="admin-stats">
          <div className="admin-stat-card"><span>Total projects</span><strong>{stats.total}</strong></div>
          <div className="admin-stat-card"><span>Published projects</span><strong>{stats.published}</strong></div>
          <div className="admin-stat-card"><span>Draft projects</span><strong>{stats.drafts}</strong></div>
        </section>
        {!demoMode && <div className="admin-tabs" role="tablist"><button type="button" role="tab" aria-selected={tab === "projects"} className={tab === "projects" ? "admin-tab admin-tab-active" : "admin-tab"} onClick={() => setTab("projects")}>Projects</button><button type="button" role="tab" aria-selected={tab === "activity"} className={tab === "activity" ? "admin-tab admin-tab-active" : "admin-tab"} onClick={() => setTab("activity")}>Activity</button></div>}
        {!demoMode && tab === "activity" ? <AdminActivity /> : <section className="admin-properties">
          <div className="admin-section-header"><div><span className="admin-eyebrow">PROJECT CATALOGUE</span><h2>All projects</h2><p>{filteredProperties.length} project{filteredProperties.length === 1 ? "" : "s"} shown</p></div><input className="admin-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search projects..." aria-label="Search projects" /></div>
          {loading ? <div className="admin-loading"><div className="admin-spinner" /><p>Loading projects...</p></div> : filteredProperties.length === 0 ? <div className="empty-properties"><h3>No projects found</h3><p>Create your first project or try another search.</p><button className="add-property-button" type="button" onClick={openCreate}>Add project</button></div> : <div className="properties-table-wrapper"><table className="properties-table"><thead><tr><th>Project</th><th>Location</th><th>Area</th><th>Status</th><th>Published</th><th>Created</th><th>Actions</th></tr></thead><tbody>{filteredProperties.map((property) => <tr key={property.id}><td><div className="property-name">{property.coverImageUrl ? <img src={imageUrl(property.coverImageUrl, 400)} alt="" loading="lazy" decoding="async" onError={showPlaceholderOnError} /> : <div className="property-thumb-placeholder">I</div>}<div><strong>{property.title}</strong><span>{property.type}</span></div></div></td><td>{property.location || "-"}</td><td>{property.area != null ? `${Number(property.area).toLocaleString()} m²` : "-"}</td><td><span className={`status-badge status-${property.status?.toLowerCase()}`}>{property.status?.replaceAll("_", " ")}</span></td><td><span className={property.published ? "published-yes" : "published-no"}>{property.published ? "Published" : "Draft"}</span></td><td>{formatDate(property.createdAt)}</td><td><div className="property-actions"><Link className="view-button" to={`/project/${property.id}`} target="_blank">View</Link><button className="edit-button" type="button" onClick={() => openEdit(property)}>Edit</button><button className="delete-button" type="button" onClick={() => handleDelete(property)}>Delete</button></div></td></tr>)}</tbody></table></div>}
        </section>}
      </main>

      {modal && <div className="admin-modal-overlay" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) requestCloseModal(); }}><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="project-form-title"><div className="admin-modal-header"><div><span className="admin-eyebrow">{modal.type === "edit" ? "UPDATE PROJECT" : "NEW PROJECT"}</span><h2 id="project-form-title">{modal.type === "edit" ? "Edit project" : "Add project"}</h2></div><button className="modal-close" type="button" onClick={requestCloseModal} aria-label="Close form">×</button></div><form className="property-form" onSubmit={handleSave}><div className="form-grid"><label className="form-field"><span>Project name *</span><input name="title" value={form.title} onChange={updateField} placeholder="IUNU Residence" required maxLength={200} /></label><label className="form-field"><span>Location</span><input name="location" value={form.location} onChange={updateField} placeholder="New Cairo" maxLength={200} /></label><label className="form-field form-field-full"><span>Description</span><textarea name="description" value={form.description} onChange={updateField} placeholder="Describe the project..." rows="5" maxLength={20000} /></label><fieldset className="form-field form-field-full arabic-fieldset"><legend>Arabic version</legend><p className="arabic-hint">Leave blank to translate automatically from English when you save. You can edit the Arabic before saving.</p><div className="arabic-grid"><label className="form-field"><span>اسم المشروع</span><input name="titleAr" value={form.titleAr} onChange={updateField} placeholder="اسم المشروع" dir="rtl" lang="ar" maxLength={400} /></label><label className="form-field"><span>الموقع</span><input name="locationAr" value={form.locationAr} onChange={updateField} placeholder="الموقع" dir="rtl" lang="ar" maxLength={400} /></label><label className="form-field form-field-full"><span>وصف المشروع</span><textarea name="descriptionAr" value={form.descriptionAr} onChange={updateField} placeholder="وصف المشروع" dir="rtl" lang="ar" rows="5" maxLength={40000} /></label></div><div className="arabic-actions"><button className="translate-button" type="button" onClick={handleTranslatePreview} disabled={translating || demoMode}>{translating ? "Translating..." : "Translate from English"}</button>{demoMode && <small>Translation requires the backend. Exit demo mode to use it.</small>}{translationNotice && <small className="arabic-notice">{translationNotice}</small>}</div></fieldset><label className="form-field"><span>Area of unit (m²)</span><input name="area" type="number" min="0" step="0.01" value={form.area} onChange={updateField} placeholder="120000" /></label><label className="form-field"><span>Project type *</span><select name="type" value={form.type} onChange={updateField}><option value="RESIDENTIAL">Residential</option><option value="COMMERCIAL">Commercial</option><option value="ADMINISTRATIVE">Administrative</option></select></label><label className="form-field"><span>Status</span><select name="status" value={form.status} onChange={updateField}><option value="AVAILABLE">Available</option><option value="COMING_SOON">Coming soon</option><option value="SOLD_OUT">Sold out</option></select></label><label className="form-field"><span>Price (optional)</span><input name="price" type="number" min="0" step="0.01" value={form.price} onChange={updateField} placeholder="Price on request" /></label><GallerySection gallery={gallery} coverUrl={coverUrl} demoMode={demoMode} wakeNotice={wakeNotice} galleryError={galleryError} urlInput={urlInput} onUrlInput={setUrlInput} onAddUrl={handleAddUrl} onAddFiles={handleAddFiles} onSetCover={setCoverUrl} onMove={handleMoveTile} onRemove={handleRemoveTile} onRetry={handleRetryTile} /><label className="form-checkbox"><input name="published" type="checkbox" checked={form.published} onChange={updateField} /><span>Publish this project on the website</span></label></div><div className="admin-modal-actions"><button className="cancel-button" type="button" onClick={requestCloseModal}>Cancel</button><button className="save-button" type="submit" disabled={saving || busyCount > 0}>{busyCount > 0 ? `Uploading ${Math.min(uploadBatch.done + 1, Math.max(uploadBatch.total, 1))} of ${Math.max(uploadBatch.total, 1)}...` : saving ? "Saving..." : modal.type === "edit" ? "Update project" : "Save project"}</button></div></form></section></div>}
    </div>
  );
}

export default AdminDashboard;
