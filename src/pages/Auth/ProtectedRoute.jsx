import { Navigate, Outlet } from "react-router-dom";

function ProtectedRoute() {
  const token = localStorage.getItem("accessToken");

  console.log("ProtectedRoute token:", token);

  if (!token) {
    console.log("No token → redirecting to login");
    return <Navigate to="/login" replace />;
  }

  console.log("Token exists → access granted");

  return <Outlet />;
}

export default ProtectedRoute;