/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useMemo, useState } from "react";

const translations = {
  ar: {
    HOME: "الرئيسية", PROJECT: "المشروعات", ABOUT: "من نحن", CONTACT: "تواصل معنا", CAREERS: "الوظائف",
    "Hotline 17337": "الخط الساخن 17337", "View location on Google Maps": "عرض الموقع على خرائط Google",
    "IUNU DEVELOPMENTS": "إيونو للتطوير العقاري", "Who we are": "من نحن", "We Are Crafting Signature Destinations": "نصنع وجهات استثنائية", "Spaces": "مساحات", "that remain.": "التي تدوم.", "LET'S TALK": "لنتحدث", "Request a": "اطلب", "quote.": "عرضًا.", "Tell us a little about what you're looking for and our team will be in touch shortly.": "أخبرنا قليلًا عما تبحث عنه وسيتواصل معك فريقنا قريبًا.",
    "EXPLORE PROJECTS": "استكشف المشروعات", "SCROLL TO EXPLORE": "مرر للاستكشاف", "CREATING ENDURING SPACES": "نصنع مساحات تدوم",
    "A Commitment to Legacy": "التزام يصنع إرثًا", "ENDURING SPACES": "مساحات تدوم", "Designed with": "مصمم بهدف", "purpose.": "ورؤية.", "A glimpse into": "لمحة عما", "what we create.": "نصنعه.",
    "OUR APPROACH": "نهجنا", "Spaces that": "مساحات", "remain.": "تبقى.", "FEATURED DEVELOPMENT": "مشروع مميز",
    "THE IUNU VISION": "رؤية إيونو", "View image": "عرض الصورة", "Previous image": "الصورة السابقة", "Next image": "الصورة التالية",
    "Choose gallery image": "اختر صورة من المعرض", "Image preview": "معاينة الصورة", "Close image preview": "إغلاق معاينة الصورة",
    "OUR DEVELOPMENTS": "مشروعاتنا", "Spaces designed": "مساحات مصممة", "to belong.": "لتنتمي إليها.", "VIEW PROJECT": "عرض المشروع", "VIEW ALL": "عرض الكل", "DISCOVER ALL DEVELOPMENTS": "اكتشف كل المشروعات",
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
    "All Rights Reserved.": "جميع الحقوق محفوظة.", "Home": "الرئيسية", "About Us": "من نحن", "Projects": "المشروعات", "Contact": "تواصل معنا", "Careers": "الوظائف", "EXPLORE": "استكشف", "FIND US": "موقعنا",

    "Connect with us on social media and stay updated with our latest properties and news.": "تواصل معنا عبر وسائل التواصل الاجتماعي وابقَ على اطلاع بآخر مشروعاتنا وأخبارنا.",
    "Visit Our Office": "زر مكتبنا", "Fill in your details and our team will get back to you shortly.": "اكتب بياناتك وسيتواصل معك فريقنا قريبًا.",
    "Spaces that remain.": "مساحات تدوم.", "Creating considered spaces where architecture, community and everyday life come together.": "نصنع مساحات مدروسة تجتمع فيها العمارة والمجتمع والحياة اليومية.",
    "We're here to answer your questions and help you find the right property.": "نحن هنا للإجابة عن أسئلتك ومساعدتك في العثور على العقار المناسب.",
    "Crafting": "نصنع", "Enduring Spaces": "مساحات تدوم", "Enduring Real Estate Solutions": "حلول عقارية تدوم", "WHO WE ARE": "من نحن", "What's IUNU?": "ما هي إيونو؟",
    "IUNU Developments is a distinguished real estate developer in Cairo, Egypt, inspired by the rich legacy of ancient Egyptian civilization. The name \"IUNU\", derived from the ancient Egyptian city of Heliopolis, reflects the company's commitment to blending cultural heritage with modern innovation.": "إيونو للتطوير العقاري شركة متميزة في القاهرة، مصر، تستلهم إرث الحضارة المصرية القديمة. واسم إيونو، المشتق من مدينة هليوبوليس القديمة، يعكس التزام الشركة بالجمع بين التراث الثقافي والابتكار الحديث.",
    "With a focus on crafting iconic residential and commercial projects, IUNU Developments is reshaping urban landscapes while honoring Egypt's timeless history.": "ومن خلال التركيز على ابتكار مشروعات سكنية وتجارية مميزة، تعيد إيونو للتطوير تشكيل المشهد العمراني مع الحفاظ على تاريخ مصر الخالد.",
    "ABOUT US": "من نحن", "IUNU Development stands out in real estate for creating enduring spaces. With a focus on legacy and thoughtful development, each project reflects a commitment to purpose and quality.": "تتميز إيونو للتطوير في مجال العقارات بصناعة مساحات تدوم. ومع التركيز على الإرث والتطوير المدروس، يعكس كل مشروع التزامًا بالهدف والجودة.",
    "Thoughtfully Designed Spaces": "مساحات مصممة بعناية", "Quiet Confidence in Projects": "ثقة هادئة في المشروعات", "Inspired by Legacy": "مستوحاة من الإرث", "Explore the properties currently available across the IUNU platform.": "استكشف العقارات المتاحة حاليًا عبر منصة إيونو.",
    "Legacy Inspired Design": "تصميم مستوحى من الإرث", "Our projects prioritize community needs, thoughtful design, and sustainability.": "تضع مشروعاتنا احتياجات المجتمع والتصميم المدروس والاستدامة في مقدمة أولوياتها.", "Confident Project Delivery": "تنفيذ موثوق للمشروعات", "Experience reliable development with a focus on quality and integrity.": "اختبر تطويرًا موثوقًا يركز على الجودة والنزاهة.", "Creating impactful spaces that reflect purpose and longevity.": "نصنع مساحات مؤثرة تعكس الهدف والاستمرارية.",
    "CONNECT WITH US": "تواصل معنا", "Get in Touch Today": "تواصل معنا اليوم", "Reach out to us to discuss your real estate needs.": "تواصل معنا لمناقشة احتياجاتك العقارية.", "SIGN UP": "اشترك",
    "Designed with purpose.": "مصمم بهدف.", "Every IUNU development is shaped around a simple idea: create places that feel relevant today and remain meaningful tomorrow.": "يقوم كل مشروع من إيونو على فكرة بسيطة: نصنع أماكن مناسبة اليوم وذات قيمة غدًا.", "Thoughtful Architecture": "عمارة مدروسة", "Lasting Quality": "جودة تدوم", "Human-Centered Spaces": "مساحات تتمحور حول الإنسان", "A glimpse into what we create.": "لمحة مما نصنعه.", "From refined interiors to carefully planned outdoor spaces, every detail contributes to the experience.": "من التصميمات الداخلية الراقية إلى المساحات الخارجية المخططة بعناية، يساهم كل تفصيل في التجربة.",
    "Discover thoughtfully designed destinations created around quality, community and lasting value.": "اكتشف وجهات مصممة بعناية تقوم على الجودة والمجتمع والقيمة المستدامة.", "Request a quote.": "اطلب عرضًا.", "Name": "الاسم", "Phone number": "رقم الهاتف", "City": "المدينة", "Project": "المشروع", "Select your option": "اختر خيارك", "Residential": "سكني", "Commercial": "تجاري", "Administrative": "إداري", "WhatsApp number": "رقم واتساب", "What is the most suitable space for your needs?": "ما المساحة الأنسب لاحتياجاتك؟", "Apartment": "شقة", "Villa": "فيلا", "Office": "مكتب", "Commercial Space": "مساحة تجارية", "SUBMIT REQUEST": "إرسال الطلب",
"THE FOUNDER": "المؤسس",
"Who is the": "من هو",
"Founder?": "المؤسس؟",
"Founder of IUNU Developments": "مؤسس إيونو للتطوير",
"Founder & Chairman": "المؤسس ورئيس مجلس الإدارة",
"READ MORE": "اقرأ المزيد",
"Thoughtfully": "مصممة بعناية",
"Designed": "بتصميم",
"Your first name":"الأسم الأول",
"Your last name":"الأسم الأخير",
"Tell us how we can help...":" اخبرنا كيف نساعدك",
"SEND MESSAGE":"أرسل رسالة",


// eslint-disable-next-line no-dupe-keys
"Spaces": "مساحات",

"Quiet": "ثقة",
"Confidence": "هادئة",
"in Projects": "في المشروعات",

"Inspired": "مستوحاة",
"by Legacy": "من الإرث",
"Mr. Wagdy Danial": "السيد وجدي دانيال",

"Mr. Wagdy Danial, a visionary entrepreneur from Upper Egypt, is the founder of IUNU Developments, a real estate investment company dedicated to transforming Cairo's urban landscape.":
  "السيد وجدي دانيال، رائد أعمال طموح من صعيد مصر، هو مؤسس شركة IUNU Developments، وهي شركة استثمار عقاري مكرسة لتحويل المشهد العمراني في القاهرة.",

"He began his career in 2006, drawing inspiration from the architectural grandeur of the Pharaohs and aiming to create modern developments that reflect the timeless elegance and innovative spirit of ancient Egyptian civilization.":
  "بدأ مسيرته المهنية في عام 2006، مستلهمًا العظمة المعمارية للفراعنة وساعيًا إلى إنشاء مشروعات حديثة تعكس الأناقة الخالدة والروح الابتكارية للحضارة المصرية.",
    // New translations
    "PHONE": "الهاتف",
    "EMAIL": "البريد الإلكتروني",
    "WORKING HOURS": "ساعات العمل",
    "Saturday - Thursday": "السبت - الخميس",
    "11:00 AM to 07:00 PM": "من 11:00 صباحًا إلى 07:00 مساءً",
    "OUR OFFICE": "مكتبنا",
    "Plot No. 306 307, Galaxy Mall, South 90th Street, second floor, Fifth Settlement, New Cairo, Egypt": "قطعة رقم 306، 307، جالاكسي مول، شارع التسعين الجنوبي، الدور الثاني، التجمع الخامس، القاهرة الجديدة، مصر",
    "Social Network": "شبكة التواصل الاجتماعي",
    "01": "01",
    "02": "02",
    "03": "03",
    "17337": "17337"
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