/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useMemo, useState } from "react";

const translations = {
  ar: {
    HOME: "الرئيسية", PROJECT: "المشروعات", ABOUT: "من نحن", CONTACT: "تواصل معنا", CAREERS: "الوظائف",
    "Hotline 17337": "الخط الساخن 17337", "View location on Google Maps": "عرض الموقع على خرائط Google",
    "IUNU DEVELOPMENTS": "إيونو للتطوير العقاري", "Who we are": "من نحن", "We Are Crafting Signature Destinations": "نصنع وجهات استثنائية",
    "EXPLORE PROJECTS": "استكشف المشروعات", "SCROLL TO EXPLORE": "مرر للاستكشاف", "CREATING ENDURING SPACES": "نصنع مساحات تدوم",
    "A Commitment to Legacy": "التزام يصنع إرثًا", "ENDURING SPACES": "مساحات تدوم", "Designed with": "مصمم بهدف", "purpose.": "ورؤية.",
    "OUR APPROACH": "نهجنا", "Spaces that": "مساحات", "remain.": "تبقى.", "FEATURED DEVELOPMENT": "مشروع مميز",
    "THE IUNU VISION": "رؤية إيونو", "View image": "عرض الصورة", "Previous image": "الصورة السابقة", "Next image": "الصورة التالية",
    "Choose gallery image": "اختر صورة من المعرض", "Image preview": "معاينة الصورة", "Close image preview": "إغلاق معاينة الصورة",
    "OUR DEVELOPMENTS": "مشروعاتنا", "Spaces designed": "مساحات مصممة", "to belong.": "لتنتمي إليها.",
    "CONTACT US": "تواصل معنا", "Get In Touch": "تواصل معنا", "SEND A MESSAGE": "أرسل رسالة", "STAY CONNECTED": "ابقَ على تواصل",
    "First name": "الاسم الأول", "Last name": "اسم العائلة", "Enduring Spaces for Tomorrow": "مساحات تدوم للمستقبل",
    "Discover thoughtfully developed spaces designed around quality, purpose, and lasting value.": "اكتشف مساحات مصممة بعناية حول الجودة والهدف والقيمة المستدامة.",
    "Thoughtful Development": "تطوير مدروس", "We create enduring spaces that balance thoughtful design, functionality, and long-term value.": "نصنع مساحات تدوم وتوازن بين التصميم المدروس والوظائف والقيمة طويلة المدى.",
    "OUR PROJECTS": "مشروعاتنا", "Discover Our Properties": "اكتشف مشروعاتنا",
    "Follow Us Online": "تابعنا عبر الإنترنت", "JOIN OUR TEAM": "انضم إلى فريقنا", "Build what": "ابنِ ما", "remains.": "يدوم.",
    "CAREERS AT IUNU": "الوظائف في إيونو", "Make an impact": "اصنع تأثيرًا", "with us.": "معنا.", "SEND YOUR APPLICATION": "أرسل طلبك",
    "Tell us about yourself.": "حدثنا عن نفسك.", "SUBMIT APPLICATION": "إرسال طلب التوظيف", "Full name": "الاسم بالكامل", "Position": "الوظيفة",
    "Message": "الرسالة", "Phone": "الهاتف", "Email": "البريد الإلكتروني", "CV / Resume": "السيرة الذاتية",
    "Your full name": "اكتب اسمك بالكامل", "Your email address": "اكتب بريدك الإلكتروني", "Your phone number": "اكتب رقم هاتفك",
    "Position of interest": "الوظيفة المطلوبة", "Tell us a little about your experience...": "حدثنا قليلًا عن خبرتك...",
    "Thank you. Your application has been sent to our team.": "شكرًا لك. تم إرسال طلبك إلى فريقنا.",
    "Bring your talent, curiosity and ambition to a team shaping enduring spaces for tomorrow.": "ساهم بموهبتك وفضولك وطموحك مع فريق يصنع مساحات تدوم للمستقبل.",
    "We are always interested in meeting thoughtful people who care about quality, collaboration and the future of development.": "نسعد دائمًا بالتعرف على أشخاص يهتمون بالجودة والتعاون ومستقبل التطوير.",
    "Complete the form and our team will review your application.": "أكمل النموذج وسيقوم فريقنا بمراجعة طلبك.", "SENDING...": "جارٍ الإرسال...", "PDF only, up to 5 MB": "ملف PDF فقط، حتى 5 ميجابايت",
    "The CV file is too large. Please choose a PDF under 5 MB.": "ملف السيرة الذاتية كبير جدًا. اختر ملف PDF أقل من 5 ميجابايت.",
    "We could not send your application right now. Please try again or email info@iunu-eg.com.": "تعذر إرسال طلبك حاليًا. حاول مرة أخرى أو راسل info@iunu-eg.com.",
    "We create considered spaces where architecture, community and everyday life come together.": "نصنع مساحات مدروسة تجتمع فيها العمارة والمجتمع والحياة اليومية.",
    "IUNU Development focuses on thoughtful real estate projects designed with purpose, character, and lasting impact.": "تركز إيونو للتطوير على مشروعات عقارية مدروسة ذات هدف وشخصية وتأثير دائم.",
    "All Rights Reserved.": "جميع الحقوق محفوظة.", "Home": "الرئيسية", "About Us": "من نحن", "Projects": "المشروعات", "Contact": "تواصل معنا", "Careers": "الوظائف", "EXPLORE": "استكشف", "FIND US": "موقعنا"
  }
};

const LanguageContext = createContext(null);

export function LanguageProvider({ children }) {
  const [language, setLanguage] = useState(() => localStorage.getItem("iunu-language") || "en");

  useEffect(() => {
    localStorage.setItem("iunu-language", language);
    document.documentElement.lang = language;
    document.documentElement.dir = language === "ar" ? "rtl" : "ltr";
  }, [language]);

  const value = useMemo(() => ({
    language,
    setLanguage: (next) => setLanguage(next),
    t: (text) => language === "ar" ? translations.ar[text] || text : text,
  }), [language]);

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useLanguage() {
  return useContext(LanguageContext);
}
