import { useLocation } from "react-router-dom";
import AppRoutes from "./Routes/AppRoutes";

function App() {
  const location = useLocation();

  return (
    <div key={location.pathname} className="page-transition">
      <AppRoutes />
    </div>
  );
}

export default App;