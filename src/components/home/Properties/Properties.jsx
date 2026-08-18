import { useEffect, useState } from "react";
import propertyServices from "../../../services/propertyServices";

function Properties() {
  const [properties, setProperties] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const loadProperties = async () => {
      try {
        const data = await propertyServices.getProperties();

console.log(
  "Properties API:",
  JSON.stringify(data, null, 2)
);
        setProperties(data.content || []);
      } catch (error) {
        console.error("Properties error:", error);
        setError("Failed to load properties.");
      } finally {
        setLoading(false);
      }
    };

    loadProperties();
  }, []);

  if (loading) {
    return <p>Loading properties...</p>;
  }

  if (error) {
    return <p>{error}</p>;
  }

  if (properties.length === 0) {
    return <p>No properties available.</p>;
  }

  return (
    <section>
      <h2>Our Properties</h2>

      {properties.map((property) => (
        <div key={property.id}>
          <h3>{property.title}</h3>
        </div>
      ))}
    </section>
  );
}

export default Properties;