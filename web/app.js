// Step 2: "Sign in" starts the Authorization Code flow with PKCE.
// (Exchanging the returned code for a token comes in the next step.)

const $ = (id) => document.getElementById(id);
const show = (text) => { $("response").textContent = text; };

// --- small helpers --------------------------------------------------------

// PKCE requires base64url: like base64, but "+" -> "-", "/" -> "_" and no "=" padding
function base64url(bytes) {
  return btoa(String.fromCharCode(...bytes))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

function randomString(byteLength = 32) {
  return base64url(crypto.getRandomValues(new Uint8Array(byteLength)));
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return new Uint8Array(digest);
}

// --- login ----------------------------------------------------------------

async function login() {
  const codeVerifier = randomString(); // the secret: stays in this browser, sent only when exchanging the code
  const state = randomString();        // returned by Keycloak unchanged; lets us reject answers to someone else's request
  const codeChallenge = base64url(await sha256(codeVerifier)); // what we send now: a hash, useless without the secret

  // the page will be reloaded after the trip to Keycloak, so plain variables would be lost
  sessionStorage.setItem("pkce_verifier", codeVerifier);
  sessionStorage.setItem("oauth_state", state);

  const params = new URLSearchParams({
    client_id: CONFIG.clientId,
    redirect_uri: CONFIG.redirectUri,
    response_type: "code",
    scope: "openid",
    state,
    code_challenge: codeChallenge,
    code_challenge_method: "S256",
  });
  window.location = `${CONFIG.issuer}/protocol/openid-connect/auth?${params}`;
}

$("login").addEventListener("click", login);

// --- exchanging the code for tokens -----------------------------------------

// Tokens live only in this variable: gone after a page reload, not readable via localStorage
let tokens = null;

// A JWT is three base64url parts: header.payload.signature. The payload is just JSON.
function decodeJwt(token) {
  const base64 = token.split(".")[1].replaceAll("-", "+").replaceAll("_", "/");
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes));
}

async function exchangeCode(code, codeVerifier) {
  const response = await fetch(`${CONFIG.issuer}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      client_id: CONFIG.clientId,
      code,
      redirect_uri: CONFIG.redirectUri, // must be exactly what we sent when starting the login
      code_verifier: codeVerifier,      // the secret: Keycloak hashes it and compares with our earlier code_challenge
    }),
  });
  const body = await response.json();
  if (!response.ok) {
    throw new Error(`${body.error}: ${body.error_description}`);
  }
  return body;
}

const secondsLeft = () => decodeJwt(tokens.access_token).exp - Math.floor(Date.now() / 1000);

// Same /token endpoint, other grant: trade the long-lived refresh token for a new pair, no password needed
async function refreshTokens() {
  const response = await fetch(`${CONFIG.issuer}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      client_id: CONFIG.clientId,
      refresh_token: tokens.refresh_token,
    }),
  });
  const body = await response.json();
  if (!response.ok) {
    // the Keycloak session is over (idle too long or signed out elsewhere): nothing left to refresh with
    tokens = null;
    $("who").textContent = "not signed in";
    $("expires").textContent = "";
    $("login").hidden = false;
    $("logout").hidden = true;
    throw new Error(`Session expired, sign in again (${body.error}: ${body.error_description})`);
  }
  tokens = body;
  render();
}

// Called before every API request: refresh early so we never send a token that is about to expire
async function freshAccessToken() {
  if (secondsLeft() < 30) await refreshTokens();
}

function render() {
  const claims = decodeJwt(tokens.access_token);
  $("who").textContent = claims.preferred_username ?? claims.sub;
  $("login").hidden = true;
  $("claims").textContent = JSON.stringify(claims, null, 2);
  $("list").disabled = false;
  $("create").querySelector("button").disabled = false;
  $("refresh").disabled = false;
  $("logout").hidden = false;
}

// --- calling docs-api -----------------------------------------------------------

// The page only attaches the token and shows what came back. Who may do what is decided by docs-api.
async function callApi(method, path, body) {
  await freshAccessToken();
  const response = await fetch(`${CONFIG.apiBase}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${tokens.access_token}`,
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  const pretty = text ? JSON.stringify(JSON.parse(text), null, 2) : "(empty body)";
  show(`${method} ${path}\nHTTP ${response.status} ${response.statusText}\n${pretty}`);
}

async function safeCall(...args) {
  try {
    await callApi(...args);
  } catch (error) {
    // typical cause: the browser blocked the response (CORS) or the API is down
    show(`Request failed before an answer arrived:\n${error.message}`);
  }
}

$("list").addEventListener("click", () => safeCall("GET", "/api/documents"));

$("create").addEventListener("submit", (event) => {
  event.preventDefault(); // do not reload the page
  const title = $("title").value.trim();
  if (title) safeCall("POST", "/api/documents", { title });
});

// --- what we see after coming back from Keycloak ---------------------------

async function handleReturn() {
  const query = new URLSearchParams(window.location.search);

  if (query.has("error")) {
    show(`Keycloak refused the login:\n${query.get("error")}: ${query.get("error_description")}`);
    return;
  }
  if (!query.has("code")) {
    show("Not signed in. Click \"Sign in\".");
    return;
  }

  const savedState = sessionStorage.getItem("oauth_state");
  const codeVerifier = sessionStorage.getItem("pkce_verifier");
  sessionStorage.removeItem("oauth_state");
  sessionStorage.removeItem("pkce_verifier"); // one-time secrets

  // the code is one-time too: take it out of the address bar so a reload does not try to reuse it
  const code = query.get("code");
  const returnedState = query.get("state");
  window.history.replaceState({}, "", CONFIG.redirectUri);

  if (!savedState || returnedState !== savedState) {
    show("The answer does not belong to our login request (state mismatch). Ignored.");
    return;
  }
  if (!codeVerifier) {
    show("No saved PKCE secret in this tab. Click \"Sign in\" again.");
    return;
  }

  try {
    tokens = await exchangeCode(code, codeVerifier);
    render();
    show("Signed in. The token is in the page memory; claims are shown above.");
  } catch (error) {
    show(`Token exchange failed:\n${error.message}`);
  }
}

// --- refresh button, countdown, logout ---------------------------------------------

$("refresh").addEventListener("click", async () => {
  const before = decodeJwt(tokens.access_token);
  try {
    await refreshTokens();
    const after = decodeJwt(tokens.access_token);
    show(`Refreshed without a password.\nold token: exp ${before.exp}, iat ${before.iat}\nnew token: exp ${after.exp}, iat ${after.iat}`);
  } catch (error) {
    show(error.message);
  }
});

// make the short token lifetime visible
setInterval(() => {
  if (tokens) $("expires").textContent = `token expires in ${Math.max(secondsLeft(), 0)} s`;
}, 1000);

// Ending only our page's memory is not enough: Keycloak keeps its own session cookie and would sign us in again.
// So we send the browser to Keycloak's logout endpoint; it comes back to post_logout_redirect_uri.
$("logout").addEventListener("click", () => {
  const params = new URLSearchParams({
    id_token_hint: tokens.id_token,                   // tells Keycloak whose session to end
    post_logout_redirect_uri: CONFIG.redirectUri,     // must be in the client's "post logout redirect URIs"
  });
  tokens = null;
  window.location = `${CONFIG.issuer}/protocol/openid-connect/logout?${params}`;
});

handleReturn();
