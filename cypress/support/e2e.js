// No backend runs in E2E: every API call is stubbed. This catch-all answers
// anything a spec did not stub with a 404, so a missing stub fails loudly
// instead of hanging on a connection to a server that is not there.
// Cypress matches the most recently defined intercept first, so the stubs a
// spec defines take precedence over these.
beforeEach(() => {
  cy.intercept("**/api/**", { statusCode: 404, body: { message: "Not stubbed in Cypress" } });
  // The admin dashboard pokes the backend's liveness probe to wake it up.
  cy.intercept("**/actuator/**", { statusCode: 200, body: { status: "UP" } });
});
