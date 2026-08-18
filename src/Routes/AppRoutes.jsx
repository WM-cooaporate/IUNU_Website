import { Routes, Route } from "react-router-dom";

import Home from "../pages/Home/Home";
import Login from "../pages/Auth/Login";
import Register from "../pages/Auth/Register";
import ForgotPassword from "../pages/Auth/ForgotPassword";
import Project from "../pages/Project/Project";
import About from "../pages/About/About";
import Contact from "../pages/Contact/Contact";
import PropertyDetails from "../pages/Project/PropertyDetails";

import ProtectedRoute from "../components/auth/ProtectedRoute";
import AdminDashboard from "../pages/Admin/AdminDashboard";

function AppRoutes() {
  return (
    <Routes>

      <Route path="/" element={<Login />} />

      <Route path="/login" element={<Login />} />

      <Route path="/register" element={<Register />} />

      <Route
        path="/forgot-password"
        element={<ForgotPassword />}
      />

      {/* Normal Users */}
      <Route element={<ProtectedRoute allowedRoles={["USER"]} />}>
        <Route path="/home" element={<Home />} />
      </Route>

      {/* Admin */}
      <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
        <Route
          path="/admin"
          element={<AdminDashboard />}
        />
      </Route>

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