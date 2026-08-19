import { Routes, Route } from "react-router-dom";

import Home from "../pages/Home/Home";
import Project from "../pages/Project/Project";
import About from "../pages/About/About";
import Contact from "../pages/Contact/Contact";
function AppRoutes() {

  return (

    <Routes>

      <Route path="/" element={<Home />} />

    
      <Route
        path="/project"
        element={<Project />}
      />
      <Route path="/about" element={<About />} />
            <Route path="/contact" element={<Contact />} />

    </Routes>

  );

}

export default AppRoutes;