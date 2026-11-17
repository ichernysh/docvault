# DocVault

A learning project: a mini document SaaS built around Keycloak (OIDC / OAuth2) and a Spring Boot resource server.
Later stages add AWS (ECS, Terraform, Lambda, SNS/SQS, DynamoDB) and local Kubernetes.

The full plan and architecture live in [docs/PLAN.md](docs/PLAN.md).

## What works now (stages 1 and 2)

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

## AWS

The cloud part lives in `infra/` and is described with Terraform. The account is on the AWS Free plan.

### Account constraints

- **Region: `eu-north-1` only.** In other regions the account's service control policies (SCP) deny basic EC2 calls.
- **No OIDC for GitHub Actions.** The same SCPs deny `iam:*Provider*`, so CI will use an access key of a dedicated
  IAM user (`ci-deploy`) stored in GitHub Secrets. Not creating it until the workflows need it.
- No MFA for console sign-in (this account type has no such setting). Do not click "Activate advanced features":
  it is irreversible, creates AWS Organizations and removes the spend limit.

### Before any AWS command

Use a named profile. Never rely on the default one (it may point to a different account).

```bash
export AWS_PROFILE=docvault
aws sts get-caller-identity     # Account must be the DocVault account
```

The profile is created once with `aws configure --profile docvault` (region `eu-north-1`, output `json`)
using an access key of the IAM user `docvault-terraform`. Keys never go into the repository.

### Bootstrap (`infra/bootstrap`)

Applied once by hand with a **local state** (the state bucket does not exist before it is created).
It creates the S3 bucket for the state of later modules (versioning, public access blocked),
a monthly cost budget with email alerts, and the IAM user `ci-deploy` (no access key yet).

```bash
cd infra/bootstrap
cp terraform.tfvars.example terraform.tfvars   # fill in account id, bucket name, email (git-ignored)
terraform init
terraform plan
terraform apply
```

`terraform.tfvars` and `terraform.tfstate*` are git-ignored: the state is local and must be backed up
outside the repository until later modules keep theirs in the S3 bucket.

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
infra/bootstrap/        Terraform: state bucket, budget, CI user (applied once, local state)
scripts/                get-token.sh, smoke-test.sh
docker-compose.yml      local environment
```
