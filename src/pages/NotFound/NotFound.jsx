import { Link } from "react-router-dom";
import Navbar from "../../components/layout/Navbar/Navbar";
import Footer from "../../components/layout/Footer/Footer";
import { useLanguage } from "../../i18n/LanguageContext";
import "./NotFound.css";

/**
 * Catch-all for unmatched URLs. Without this, a typo or a stale link rendered
 * nothing at all - the router matched no route and the page came up blank.
 */
function NotFound() {
  const { t } = useLanguage();

  return (
    <div className="notfound-page">
      <Navbar />

      <main className="notfound-main">
        <span className="notfound-eyebrow">{t("IUNU DEVELOPMENTS")}</span>
        <h1>{t("Page not found")}</h1>
        <p>
          {t("The page you are looking for has moved or no longer exists.")}
        </p>

        <div className="notfound-actions">
          <Link className="notfound-primary" to="/home">
            {t("Back to home")}
          </Link>
          <Link className="notfound-secondary" to="/project">
            {t("View our projects")}
          </Link>
        </div>
      </main>

      <Footer />
    </div>
  );
}

export default NotFound;
