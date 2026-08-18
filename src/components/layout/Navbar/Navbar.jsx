import { NavLink, Link } from "react-router-dom";
import "./Navbar.css";

function Navbar() {

  const token = localStorage.getItem("accessToken");
  const userData = localStorage.getItem("user");

  let user;

  try {
    user = userData ? JSON.parse(userData) : null;
  } catch {
    user = null;
  }

  const homePath =
    token && user?.role === "ADMIN"
      ? "/admin"
      : token && user?.role === "USER"
        ? "/home"
        : "/login";

  return (
    <header className="navbar">

      <div className="navbar-container">

        {/* Logo */}

        <Link
          to={homePath}
          className="navbar-logo"
        >
          IUNU
        </Link>


        {/* Navigation */}

        <nav className="navbar-menu">

          <NavLink
            to={homePath}
            end
            className={({ isActive }) =>
              isActive
                ? "nav-link nav-link-active"
                : "nav-link"
            }
          >
            HOME
          </NavLink>


          <NavLink
            to="/project"
            className={({ isActive }) =>
              isActive
                ? "nav-link nav-link-active"
                : "nav-link"
            }
          >
            PROJECT
          </NavLink>


          <NavLink
            to="/about"
            className={({ isActive }) =>
              isActive
                ? "nav-link nav-link-active"
                : "nav-link"
            }
          >
            ABOUT
          </NavLink>


          <NavLink
            to="/contact"
            className={({ isActive }) =>
              isActive
                ? "nav-link nav-link-active"
                : "nav-link"
            }
          >
            CONTACT
          </NavLink>


          <NavLink
            to="/blank"
            className={({ isActive }) =>
              isActive
                ? "nav-link nav-link-active"
                : "nav-link"
            }
          >
            BLANK
          </NavLink>

        </nav>


        {/* Phone */}

        <a
          href="tel:17337"
          className="navbar-phone"
        >
          <span className="phone-icon">
            ☎
          </span>

          17337

        </a>

      </div>

    </header>
  );
}

export default Navbar;