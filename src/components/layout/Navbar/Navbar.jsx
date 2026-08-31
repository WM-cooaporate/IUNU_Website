import { useState } from "react";
import { NavLink, Link } from "react-router-dom";
import "./Navbar.css";

function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false);

  const closeMenu = () => setMenuOpen(false);

  return (
    <header className="navbar">
      <div className="navbar-container">

        {/* Logo */}
        <Link to="/home" className="navbar-logo">
          <img
            src="/images/Logo.png"
            alt="IUNU Developments"
            className="navbar-logo-image"
          />
        </Link>

        {/* Navigation */}
        <button
          type="button"
          className={`navbar-toggle${menuOpen ? " is-open" : ""}`}
          aria-label={menuOpen ? "Close navigation menu" : "Open navigation menu"}
          aria-expanded={menuOpen}
          aria-controls="main-navigation"
          onClick={() => setMenuOpen((isOpen) => !isOpen)}
        >
          <span />
          <span />
          <span />
        </button>

        <nav
          id="main-navigation"
          className={`navbar-menu${menuOpen ? " is-open" : ""}`}
        >

          <NavLink
            to="/home"
            end
            className={({ isActive }) =>
              isActive
                ? "nav-link nav-link-active"
                : "nav-link"
            }
            onClick={closeMenu}
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
            onClick={closeMenu}
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
            onClick={closeMenu}
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
            onClick={closeMenu}
          >
            CONTACT
          </NavLink>

          <NavLink
            to="/careers"
            className={({ isActive }) => isActive ? "nav-link nav-link-active" : "nav-link"}
            onClick={closeMenu}
          >
            CAREERS
          </NavLink>

          <a href="tel:17337" className="navbar-menu-phone">
            <span className="phone-icon" aria-hidden="true">☎</span>
            <span>Hotline 17337</span>
          </a>

        </nav>

        {/* Phone */}
        <a
          href="tel:17337"
          className="navbar-phone navbar-desktop-phone"
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
