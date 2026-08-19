import Navbar from "../../components/layout/Navbar/Navbar";
import Hero from "../../components/home/Hero/Hero";
import LegacySection from "../../components/home/LegacySection/LegacySection";
import Development from "../../components/home/Development/Development";
import Properties from "../../components/home/Properties/Properties";
import QuoteForm from "../../components/home/QuoteForm/QuoteForm";
import Footer from "../../components/layout/Footer/Footer";

function Home() {
  return (
    <div className="home-page">

      <Navbar />

      <main className="home-content">
        <Hero />
        <LegacySection />
        <Development />
        <Properties />
        <QuoteForm />
      </main>

      <Footer />

    </div>
  );
}

export default Home;