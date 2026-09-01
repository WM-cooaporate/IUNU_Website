import { useState } from "react";
import { NavLink, Link } from "react-router-dom";
import { useLanguage } from "../../../i18n/LanguageContext";
import "./Navbar.css";

function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false);
  const { language, setLanguage, t } = useLanguage();

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
            {t("HOME")}
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
            {t("PROJECT")}
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
            {t("ABOUT")}
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
            {t("CONTACT")}
          </NavLink>

          <NavLink
            to="/careers"
            className={({ isActive }) => isActive ? "nav-link nav-link-active" : "nav-link"}
            onClick={closeMenu}
          >
            {t("CAREERS")}
          </NavLink>

          <a href="tel:17337" className="navbar-menu-phone">
            <span className="phone-icon" aria-hidden="true">☎</span>
            <span>{t("Hotline 17337")}</span>
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

        <button
          type="button"
          className="navbar-language-toggle"
          onClick={() => setLanguage(language === "en" ? "ar" : "en")}
          aria-label={language === "en" ? "Switch to Arabic" : "التبديل إلى الإنجليزية"}
        >
          {language === "en" ? "AR" : "EN"}
        </button>

      </div>
    </header>
  );
}

export default Navbar;
