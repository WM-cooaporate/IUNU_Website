// Public site smoke tests against the production build, with the API stubbed.
// Fixture titles ("Cypress Test Tower", "Cypress Fixture Plaza") do not exist in
// src/data/demoProperties.js, so they can only appear if the stubbed API
// response was rendered.

const stubPropertyList = () =>
  cy
    .intercept({ method: "GET", pathname: "/api/properties" }, { fixture: "properties.json" })
    .as("propertyList");

describe("Public site", () => {
  beforeEach(() => {
    stubPropertyList();
  });

  it("home page loads and shows a property from the API", () => {
    cy.visit("/");

    cy.location("pathname").should("eq", "/home");
    cy.wait("@propertyList");
    cy.contains("h3", "Cypress Test Tower").scrollIntoView().should("be.visible");
  });

  it("projects page lists both properties", () => {
    cy.visit("/project");

    cy.wait("@propertyList");
    cy.contains("a", "Cypress Test Tower").should("be.visible");
    cy.contains("a", "Cypress Fixture Plaza").should("be.visible");
  });

  it("clicking a property opens its details page", () => {
    cy.intercept({ method: "GET", pathname: "/api/properties/101" }, { fixture: "property.json" }).as(
      "propertyDetails"
    );

    cy.visit("/project");
    cy.wait("@propertyList");
    cy.contains("a", "Cypress Test Tower").click();

    cy.location("pathname").should("eq", "/project/101");
    cy.wait("@propertyDetails");
    cy.contains("h1", "Cypress Test Tower").should("be.visible");
    cy.contains("#101").should("exist");
  });

  it("an unknown property id shows the not-found state", () => {
    cy.intercept(
      { method: "GET", pathname: "/api/properties/999999" },
      {
        statusCode: 404,
        body: {
          status: 404,
          error: "Not Found",
          message: "Property not found",
          path: "/api/properties/999999",
          fieldErrors: [],
        },
      }
    ).as("missingProperty");

    cy.visit("/project/999999");

    cy.wait("@missingProperty");
    cy.contains("h1", "Property not found.").should("be.visible");
    cy.contains("a", "Back to Properties").should("have.attr", "href", "/project");
  });

  it("the language toggle switches the page to Arabic", () => {
    cy.visit("/project");
    cy.wait("@propertyList");
    cy.get("html").should("have.attr", "dir", "ltr");

    cy.get('button[aria-label="Switch to Arabic"]').click();

    cy.get("html").should("have.attr", "dir", "rtl").and("have.attr", "lang", "ar");
    // From the t() dictionary in src/i18n/LanguageContext.jsx.
    cy.contains("h2", "اكتشف مشروعاتنا").should("be.visible");
    // The fixture's titleAr, picked by localize().
    cy.contains("a", "برج اختبار سايبرس").should("be.visible");
  });
});
