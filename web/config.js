// Where things live. Kept apart from the logic so the cloud setup only changes this file.
const CONFIG = {
  // Keycloak realm; the browser talks to it directly (login page, token endpoint)
  issuer: "http://localhost:8080/realms/docvault",
  // Client created in Keycloak (public client, Authorization Code + PKCE)
  clientId: "web",
  // Where Keycloak sends the browser back after login. Must match "Valid redirect URIs" of the client
  redirectUri: "http://localhost:3000/",
  // Our resource server
  apiBase: "http://localhost:8081",
};
