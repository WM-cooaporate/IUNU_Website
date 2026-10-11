// Admin sign-in smoke tests against the production build, with the API stubbed.

const EXPIRED_ACCESS_TOKEN = "cypress.expired.access-token";
const OLD_REFRESH_TOKEN = "cypress-old-refresh-token";

/** Opens /admin with an admin session already stored, as if signed in earlier. */
const visitWithStoredSession = () =>
  cy.visit("/admin", {
    onBeforeLoad(win) {
      win.localStorage.setItem("accessToken", EXPIRED_ACCESS_TOKEN);
      win.localStorage.setItem("refreshToken", OLD_REFRESH_TOKEN);
      win.localStorage.setItem(
        "user",
        JSON.stringify({ id: 1, fullName: "Cypress Admin", email: "admin@cypress.test", role: "ADMIN" })
      );
    },
  });

/** The admin listing rejects the expired token and accepts anything else. */
const stubAdminListingRejectingExpiredToken = () =>
  cy
    .intercept({ method: "GET", pathname: "/api/properties/admin" }, (req) => {
      if (req.headers.authorization === `Bearer ${EXPIRED_ACCESS_TOKEN}`) {
        req.reply({ statusCode: 401, body: { status: 401, message: "Unauthorized", fieldErrors: [] } });
      } else {
        req.reply({ fixture: "empty-page.json" });
      }
    })
    .as("adminProperties");

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

  it("an expired access token is refreshed and the request replayed", () => {
    stubAdminListingRejectingExpiredToken();
    cy.intercept({ method: "POST", pathname: "/api/auth/refresh" }, { fixture: "login-success.json" }).as(
      "refresh"
    );

    visitWithStoredSession();

    cy.wait("@refresh").its("request.body").should("deep.equal", { refreshToken: OLD_REFRESH_TOKEN });
    cy.contains("h3", "No projects found").should("be.visible");
    cy.contains("h1", "Admin Portal").should("not.exist");
    cy.window().its("localStorage").invoke("getItem", "accessToken").should("eq", "cypress.fake.access-token");
    cy.window().its("localStorage").invoke("getItem", "refreshToken").should("eq", "cypress-fake-refresh-token");
  });

  it("a refused refresh signs the admin out", () => {
    stubAdminListingRejectingExpiredToken();
    cy.intercept(
      { method: "POST", pathname: "/api/auth/refresh" },
      { statusCode: 401, body: { status: 401, message: "Invalid or expired refresh token", fieldErrors: [] } }
    ).as("refresh");

    visitWithStoredSession();

    cy.wait("@refresh");
    cy.contains("h1", "Admin Portal").should("be.visible");
    cy.contains("h2", "All projects").should("not.exist");
    cy.window().its("localStorage").invoke("getItem", "accessToken").should("be.null");
  });
});
