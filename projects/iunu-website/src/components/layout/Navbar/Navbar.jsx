import { NavLink, Link } from "react-router-dom";
import "./Navbar.css";

function Navbar() {
  return (
    <header className="navbar">
      <div className="navbar-container">

        {/* Logo */}
        <Link to="/" className="navbar-logo">
          IUNU
        </Link>

        {/* Navigation */}
        <nav className="navbar-menu">

          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              isActive ? "nav-link nav-link-active" : "nav-link"
            }
          >
            HOME
          </NavLink>

          <NavLink
            to="/project"
            className={({ isActive }) =>
              isActive ? "nav-link nav-link-active" : "nav-link"
            }
          >
            PROJECT
          </NavLink>

          <NavLink
            to="/about"
            className={({ isActive }) =>
              isActive ? "nav-link nav-link-active" : "nav-link"
            }
          >
            ABOUT
          </NavLink>

          <NavLink
            to="/contact"
            className={({ isActive }) =>
              isActive ? "nav-link nav-link-active" : "nav-link"
            }
          >
            CONTACT
          </NavLink>

          <NavLink
            to="/blank"
            className={({ isActive }) =>
              isActive ? "nav-link nav-link-active" : "nav-link"
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
          <span className="phone-icon">☎</span>
          17337
        </a>

      </div>
    </header>
  );
}

export default Navbar;