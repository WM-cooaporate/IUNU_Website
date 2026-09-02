import "./Properties.css";
import { useLanguage } from "../../../i18n/LanguageContext";

function LegacySection() {
  const { t } = useLanguage();

  return (
    <section className="legacy-section">

      <div className="legacy-content">

        <span className="legacy-label">
          {t("CREATING ENDURING SPACES")}
        </span>

        <h2>
          {t("A Commitment to Legacy")}
        </h2>

        <p>
          {t(
            "IUNU Development focuses on thoughtful real estate projects designed with purpose, character, and lasting impact."
          )}
        </p>

      </div>

      <div className="legacy-image-wrapper">

        <div className="legacy-image-overlay"></div>

        <img
          src="/images/legacy.jpg"
          alt={t("IUNU Development")}
          className="legacy-image"
        />

        <div className="legacy-image-caption">
          <span>01</span>
          <span>{t("ENDURING SPACES")}</span>
        </div>

      </div>

    </section>
  );
}

export default LegacySection;