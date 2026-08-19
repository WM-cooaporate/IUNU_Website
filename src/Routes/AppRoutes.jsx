import { Routes, Route, Navigate } from "react-router-dom";

import Home from "../pages/Home/Home";
import Project from "../pages/Project/Project";
import About from "../pages/About/About";
import Contact from "../pages/Contact/Contact";
import PropertyDetails from "../pages/Project/PropertyDetails";

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

    </Routes>
  );
}

export default AppRoutes;