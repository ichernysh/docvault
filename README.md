# DocVault

A learning project: a mini document SaaS built around Keycloak (OIDC / OAuth2) and a Spring Boot resource server.
Later stages add AWS (ECS, Terraform, Lambda, SNS/SQS, DynamoDB) and local Kubernetes.

The full plan and architecture live in [docs/PLAN.md](docs/PLAN.md).

## What works now (stages 1 to 3)

- CI on GitHub Actions: every pull request builds `docs-api` and runs all tests on Java 21 and 25 (see [CI](#ci)).

- `web/`: a plain JavaScript page (no libraries) that signs in through the Keycloak login page
  (Authorization Code + PKCE), shows the token claims, calls `docs-api`, refreshes the token and signs out.

- `docs-api`: Spring Boot 3 resource server with CRUD for documents and per-user sharing.
- Keycloak realm `docvault`, imported from `keycloak/realm-docvault.json`.
- Authorization: realm roles (`viewer`, `editor`, `admin`), tenant isolation by the `tenant_id` claim,
  sharing (`READ` / `WRITE`) checked in `@PreAuthorize`.
- Tests with a real Keycloak (Testcontainers).

## Local run

Requirements: Docker (running), JDK 21+ (only for running tests outside Docker).

```bash
docker compose up -d --build
docker compose ps          # wait until keycloak and docs-api are Up
```

| Service | URL |
| --- | --- |
| Keycloak admin console | http://localhost:8080 (`admin` / `admin`) |
| docs-api | http://localhost:8081 |
| Postgres | localhost:5432 (`postgres` / `postgres`, databases `keycloak` and `docvault`) |

Values can be overridden with a local `.env` file (see `.env.example`). These are throwaway dev credentials, not secrets.

### Test users

All have the password `password`.

| User | Realm role | Tenant |
| --- | --- | --- |
| alice | editor | acme |
| bob | viewer | acme |
| carol | admin | globex |

### Try it

```bash
# decoded claims of alice's access token (iss, aud, realm_access.roles, tenant_id)
scripts/get-token.sh alice

# call the API with a real token
TOKEN=$(RAW=1 scripts/get-token.sh alice)
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/documents

# 14 end-to-end checks of the 401 / 403 / 404 contract
scripts/smoke-test.sh
```

Expected statuses: no token gives `401`, a user without the needed right gives `403`,
a document from another tenant gives `404` (its existence is not revealed).

### Web page (browser login)

Open http://localhost:3000 (use `localhost`, not `127.0.0.1`: Keycloak accepts exactly this address).

1. **Sign in** sends the browser to Keycloak. The password is typed only there; the page gets a one-time `code`
   and exchanges it for tokens, proving it started the login with the PKCE secret.
2. The page shows the claims of the access token and a countdown of its lifetime (5 minutes).
3. **GET /api/documents** and **Create** call `docs-api` with `Authorization: Bearer <token>`.
   Try `bob` (viewer, gets 403 on create) and `carol` (other tenant) in a private window.
4. **Refresh token now** trades the refresh token for a new token pair without a password.
   The page also does it automatically when less than 30 seconds are left.
5. **Sign out** ends the Keycloak session too, so the next sign-in asks for the password again.

Tokens are kept only in the page memory. After a reload, click **Sign in** again: Keycloak remembers the session
and returns immediately.

### Tests

```bash
cd docs-api
./mvnw test      # fast tests (need Docker for Postgres)
./mvnw verify    # + integration tests with a real Keycloak (*IT)
```

### Stop and reset

```bash
docker compose down        # stop, keep data
docker compose down -v     # stop and delete the Postgres volume; the realm is re-imported from JSON
```

## CI

Workflow `.github/workflows/ci.yml` runs on every pull request and on every push to `main`.

- Job `build` runs `./mvnw -B verify` in `docs-api` (unit tests, then integration tests with a real Keycloak
  through Testcontainers) on a matrix of Java 21 and 25. The checks are named `build (21)` and `build (25)`.
- The shared setup (Temurin JDK and Maven cache) lives in the composite action
  `.github/actions/setup-java-maven`; the workflow only has to check out the code first.
- The workflow token has `contents: read` only.
- Test reports are uploaded as artifacts `test-reports-java-21` and `test-reports-java-25`, also when tests fail.
  Open the run on the Actions tab and scroll down to **Artifacts**, or:
  `gh run download <run-id> -n test-reports-java-21`. Reports are kept for 7 days.
- Dependabot (`.github/dependabot.yml`) opens weekly pull requests for Maven dependencies, GitHub Actions and Docker
  base images. Major updates of Spring Boot and of the Java images are ignored on purpose.
- The ruleset `protect-main` allows changes to `main` only through a pull request with both checks green,
  and forbids force pushes and deleting `main`. If you rename the job or change the matrix, update the required
  checks in the ruleset, otherwise every pull request stays blocked.

## Troubleshooting

**`HTTPS required` when calling Keycloak from the host.** The realm setting `sslRequired=external` allows plain HTTP
only from local addresses, and Docker Desktop port forwarding can make requests look external.
This is a local-dev issue only.

- Realm `docvault` (tokens, API calls): already set to `"sslRequired": "none"` in `keycloak/realm-docvault.json`,
  so nothing to do.
- Realm `master` (the admin console at http://localhost:8080): Keycloak creates it itself and it is not part of
  our JSON, so switch the requirement off once after each fresh start:

```bash
KCADM="docker compose exec keycloak /opt/keycloak/bin/kcadm.sh"
$KCADM config credentials --server http://localhost:8080 --realm master --user admin --password admin
$KCADM update realms/master -s sslRequired=NONE
```

This lives in the Keycloak database, so it is lost after `docker compose down -v`.
Never do this outside local development: in the cloud, Keycloak sits behind HTTPS.

In the admin console, the realm switcher is in the top-left corner: pick `docvault` there (you start in `master`).

## Repository layout

```
docs/PLAN.md            plan and architecture
docs-api/               Spring Boot service
web/                    browser page (login, API calls), served by nginx on port 3000
keycloak/               realm export (realm-docvault.json)
docker/                 helper files for compose (Postgres init)
scripts/                get-token.sh, smoke-test.sh
.github/                workflows/ci.yml, actions/setup-java-maven, dependabot.yml
docker-compose.yml      local environment
```
