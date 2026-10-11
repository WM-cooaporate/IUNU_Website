// Admin sign-in smoke tests against the production build, with the API stubbed.

const fillLoginForm = () => {
  cy.get('input[type="email"]').type("admin@cypress.test");
  cy.get('input[type="password"]').type("not-a-real-password");
  cy.contains("button", "Sign in").click();
};

describe("Admin dashboard", () => {
  it("shows the sign-in form when there is no session", () => {
    cy.visit("/admin");

    cy.contains("h1", "Admin Portal").should("be.visible");
    cy.get('input[type="email"]').should("be.visible");
    cy.get('input[type="password"]').should("be.visible");
    cy.contains("h2", "All projects").should("not.exist");
  });

  it("a failed login shows the error and stays on the sign-in form", () => {
    cy.intercept(
      { method: "POST", pathname: "/api/auth/login" },
      {
        statusCode: 401,
        body: {
          type: "about:blank",
          title: "Unauthorized",
          status: 401,
          detail: "Invalid email or password",
          instance: "/api/auth/login",
          timestamp: "2026-01-01T00:00:00Z",
          error: "Unauthorized",
          message: "Invalid email or password",
          path: "/api/auth/login",
          fieldErrors: [],
        },
      }
    ).as("login");

    cy.visit("/admin");
    fillLoginForm();

    cy.wait("@login");
    cy.contains(".admin-alert-error", "Invalid email or password").should("be.visible");
    cy.contains("h1", "Admin Portal").should("be.visible");
    cy.window().its("localStorage").invoke("getItem", "accessToken").should("be.null");
  });

  it("a successful login reaches the dashboard", () => {
    cy.intercept({ method: "POST", pathname: "/api/auth/login" }, { fixture: "login-success.json" }).as(
      "login"
    );
    cy.intercept({ method: "GET", pathname: "/api/properties/admin" }, { fixture: "empty-page.json" }).as(
      "adminProperties"
    );

    cy.visit("/admin");
    fillLoginForm();

    cy.wait("@login");
    cy.wait("@adminProperties")
      .its("request.headers.authorization")
      .should("eq", "Bearer cypress.fake.access-token");
    cy.contains("h2", "All projects").should("be.visible");
    cy.contains("h3", "No projects found").should("be.visible");
    cy.contains("h1", "Admin Portal").should("not.exist");
  });
});
