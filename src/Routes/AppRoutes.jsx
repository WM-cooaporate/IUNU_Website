import { Routes, Route } from "react-router-dom";

import Home from "../pages/Home/Home";
import Project from "../pages/Project/Project";
import About from "../pages/About/About";
import Contact from "../pages/Contact/Contact";
import PropertyDetails from "../pages/Project/PropertyDetails";

function AppRoutes() {
  return (
    <Routes>
      <Route path="/home" element={<Home />} />

      <Route path="/project" element={<Project />} />

      <Route
        path="/project/:id"
        element={<PropertyDetails />}
      />

      <Route path="/about" element={<About />} />

      <Route path="/contact" element={<Contact />} />
    </Routes>
  );
}

export default AppRoutes;