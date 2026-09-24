import { Routes, Route, Navigate } from "react-router-dom";

import Home from "../pages/Home/Home";
import Project from "../pages/Project/Project";
import About from "../pages/About/About";
import Contact from "../pages/Contact/Contact";
import PropertyDetails from "../pages/Project/PropertyDetails";
import AdminDashboard from "../pages/Admin/AdminDashboard";
import Careers from "../pages/Careers/Careers";
import NotFound from "../pages/NotFound/NotFound";

function AppRoutes() {
  return (
    <Routes>

      {/* Default route */}
      <Route
        path="/"
        element={<Navigate to="/home" replace />}
      />

      {/* Home */}
      <Route
        path="/home"
        element={<Home />}
      />

      {/* Projects */}
      <Route
        path="/project"
        element={<Project />}
      />

      {/* Property Details */}
      <Route
        path="/project/:id"
        element={<PropertyDetails />}
      />

      {/* About */}
      <Route
        path="/about"
        element={<About />}
      />

      {/* Contact */}
      <Route
        path="/contact"
        element={<Contact />}
      />

      <Route
        path="/careers"
        element={<Careers />}
      />

      {/* AdminDashboard gates itself: without a stored ADMIN session it
          renders the sign-in card instead of the dashboard, and any 401 from
          the API drops straight back to it. The backend is still the real
          authority - nothing under /api/admin answers without an ADMIN token.
          React 19 hoists the <meta> into <head> while this route is mounted,
          so search engines that reach /admin despite robots.txt drop it. */}
      <Route
        path="/admin"
        element={
          <>
            <meta name="robots" content="noindex, nofollow" />
            <AdminDashboard />
          </>
        }
      />

      {/* Catch-all: an unmatched URL used to render a blank page. */}
      <Route
        path="*"
        element={<NotFound />}
      />

    </Routes>
  );
}

export default AppRoutes;
