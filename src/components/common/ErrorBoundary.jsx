import { Component } from "react";

/**
 * Catches render-time crashes anywhere below it and shows a readable message
 * instead of an empty page.
 *
 * A blank white screen with nothing in the console is the worst possible
 * failure: it looks identical to a hosting problem, a bad build and a bug in
 * the app. Anything that throws during render should say so on the page and
 * leave a stack in the console.
 *
 * Note this catches render/lifecycle errors only - React error boundaries do
 * not catch errors thrown while modules are being imported, or inside event
 * handlers and async callbacks. Those still need their own handling.
 */
class ErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error("Unhandled render error:", error, info?.componentStack);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <div
        role="alert"
        style={{
          maxWidth: "40rem",
          margin: "4rem auto",
          padding: "0 1.5rem",
          fontFamily: "system-ui, sans-serif",
          lineHeight: 1.6,
          color: "#1a1a1a",
        }}
      >
        <h1 style={{ fontSize: "1.5rem", marginBottom: "0.5rem" }}>
          Something went wrong
        </h1>
        <p style={{ marginBottom: "1.5rem" }}>
          This page failed to load. Reloading may help. If it keeps happening,
          the details below are in the browser console.
        </p>
        <pre
          style={{
            padding: "1rem",
            background: "#f4f4f4",
            border: "1px solid #ddd",
            borderRadius: "4px",
            overflowX: "auto",
            whiteSpace: "pre-wrap",
            wordBreak: "break-word",
            fontSize: "0.875rem",
          }}
        >
          {error.message || String(error)}
        </pre>
        <button
          type="button"
          onClick={() => window.location.reload()}
          style={{
            marginTop: "1.5rem",
            padding: "0.6rem 1.2rem",
            fontSize: "1rem",
            cursor: "pointer",
          }}
        >
          Reload the page
        </button>
      </div>
    );
  }
}

export default ErrorBoundary;
