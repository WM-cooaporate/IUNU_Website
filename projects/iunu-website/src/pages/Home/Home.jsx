import Navbar from "../../components/layout/Navbar/Navbar";
import Hero from "../../components/home/Hero/Hero";
import LegacySection from "../../components/home/LegacySection/LegacySection";"../../components/home/LegacySection/LegacySection";
import Development from "../../components/home/Development/Development";
import QuoteForm from "../../components/home/QuoteForm/QuoteForm";
import Footer from "../../components/layout/Footer/Footer";
function Home() {
  return (
    <div className="home-page">

      <Navbar />

      <main>
        <Hero />
                <LegacySection />
                <Development/>
                <QuoteForm/>
                <Footer/>
      </main>

    </div>
  );
}

export default Home;