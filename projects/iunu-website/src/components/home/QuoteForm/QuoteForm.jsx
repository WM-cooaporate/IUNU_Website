import { useState } from "react";
import "./QuoteForm.css";

function QuoteForm() {
  const [formData, setFormData] = useState({
    name: "",
    phone: "",
    city: "",
    email: "",
    project: "",
    whatsapp: "",
    spaceType: "",
  });

  const handleChange = (event) => {
    const { name, value } = event.target;

    setFormData((previousData) => ({
      ...previousData,
      [name]: value,
    }));
  };

  const handleSubmit = (event) => {
    event.preventDefault();

    console.log("Quote Request:", formData);

    alert("Your request has been submitted successfully.");

    setFormData({
      name: "",
      phone: "",
      city: "",
      email: "",
      project: "",
      whatsapp: "",
      spaceType: "",
    });
  };

  return (
    <section className="quote-section">

      <div className="quote-container">

        <div className="quote-header">
          <h2>Request A Quote</h2>

          <p>
            Fill in your details and we will be in touch shortly.
          </p>
        </div>

        <form
          className="quote-form"
          onSubmit={handleSubmit}
        >

          {/* Row 1 */}
          <div className="quote-row">

            <div className="quote-field">
              <label htmlFor="name">
                Name
              </label>

              <input
                id="name"
                name="name"
                type="text"
                placeholder="Enter your name"
                value={formData.name}
                onChange={handleChange}
                required
              />
            </div>

            <div className="quote-field">
              <label htmlFor="phone">
                Phone number
              </label>

              <input
                id="phone"
                name="phone"
                type="tel"
                placeholder="Enter your phone number"
                value={formData.phone}
                onChange={handleChange}
                required
              />
            </div>

          </div>


          {/* Row 2 */}
          <div className="quote-row">

            <div className="quote-field">
              <label htmlFor="city">
                City
              </label>

              <input
                id="city"
                name="city"
                type="text"
                placeholder="Enter your City"
                value={formData.city}
                onChange={handleChange}
                required
              />
            </div>

            <div className="quote-field">
              <label htmlFor="email">
                Email
              </label>

              <input
                id="email"
                name="email"
                type="email"
                placeholder="Enter your email address"
                value={formData.email}
                onChange={handleChange}
                required
              />
            </div>

          </div>


          {/* Row 3 */}
          <div className="quote-row">

            <div className="quote-field">
              <label htmlFor="project">
                Project
              </label>

              <select
                id="project"
                name="project"
                value={formData.project}
                onChange={handleChange}
                required
              >
                <option value="">
                  Select your option
                </option>

                <option value="residential">
                  Residential
                </option>

                <option value="commercial">
                  Commercial
                </option>

                <option value="administrative">
                  Administrative
                </option>
              </select>
            </div>

            <div className="quote-field">
              <label htmlFor="whatsapp">
                What's App Number
              </label>

              <input
                id="whatsapp"
                name="whatsapp"
                type="tel"
                placeholder="Enter your What's App Number"
                value={formData.whatsapp}
                onChange={handleChange}
              />
            </div>

          </div>


          {/* Space Type */}
          <div className="quote-field quote-full-field">

            <label htmlFor="spaceType">
              What is the most suitable space for your needs?
            </label>

            <select
              id="spaceType"
              name="spaceType"
              value={formData.spaceType}
              onChange={handleChange}
              required
            >
              <option value="">
                Select your option
              </option>

              <option value="apartment">
                Apartment
              </option>

              <option value="villa">
                Villa
              </option>

              <option value="office">
                Office
              </option>

              <option value="commercial">
                Commercial Space
              </option>
            </select>

          </div>


          {/* Submit */}
          <div className="quote-submit">

            <button type="submit">
              SUBMIT
            </button>

          </div>

        </form>

      </div>

    </section>
  );
}

export default QuoteForm;