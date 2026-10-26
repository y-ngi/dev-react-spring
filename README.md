# dev-react-spring

A React and Spring Boot monorepo designed for a same-origin API behind Google Cloud
HTTPS Load Balancing.

## Architecture

- `frontend/` is a React 19, TypeScript, and Vite SPA. API calls use `/api/*`;
  Vite proxies those requests to the local backend.
- `backend/` is a Java 25 / Spring Boot 3 application using `JdbcClient` and Flyway.
  MySQL 8.4 is the authorization source of truth.
- Login verifies a BCrypt password and emails a five-minute, one-use OTP. The
  stored OTP is a SHA-256 hash. A successful verification issues a 15-minute JWT
  in an `HttpOnly`, `SameSite=Lax` cookie.
- JWTs contain only `sub`, `iat`, and `exp`. The security filter queries the
  user's current status and role for every authenticated API request.
- `terraform/` provisions Cloud Run behind a serverless NEG and HTTPS load
  balancer, Cloud Armor IP/rate-limit rules, a CDN-enabled static bucket, and
  private-IP Cloud SQL.

## Local development

Frontend development runs independently on Node.js 24. Start Vite with MSW
enabled by default; API calls are mocked in the browser and do not require Docker
or a running backend:

```sh
cd frontend
npm ci
npm run dev
```

For frontend/backend integration checks, run the backend with its database and
mail catcher as ordinary background containers, then start the frontend in API
mode:

```sh
docker compose \
  -f .devcontainer/docker-compose.yml \
  -f .devcontainer/docker-compose.backend.yml \
  up -d --build backend
cd frontend && npm run dev:api
```

Stop the integration services with the same Compose file arguments and `down`.
The frontend is at `http://localhost:5173`, the API at `http://localhost:8080`,
Mailpit at `http://localhost:8025`, and MySQL at port `3306`. Flyway initializes
the database. Provision user records with BCrypt password hashes before testing
login; the application intentionally has no public registration endpoint.

For backend development with Java tooling, open the repository's
`.devcontainer/devcontainer.json` in VS Code and reopen in the Dev Container. It
provides a Java 25/Gradle backend workspace, while MySQL and Mailpit remain
separate services. The frontend is not installed in or required by this
container.

## Checks

```sh
cd frontend && npm ci && npm test && npm run build
cd ../backend && ./gradlew test
```

Backend authorization integration tests use Testcontainers and MySQL 8.4.

## Google Cloud environments

`terraform/environments/stg` and `terraform/environments/prd` use a GCS state
backend. Create the state bucket first, then initialize with a restricted state
bucket, for example:

```sh
terraform -chdir=terraform/environments/stg init \
  -backend-config="bucket=YOUR_TERRAFORM_STATE_BUCKET" \
  -backend-config="prefix=dev-react-spring/stg"
```

Supply environment-specific project, region, domain, globally unique frontend
bucket, Cloud Run image, admin CIDRs, Cloud SQL password, and Secret Manager
secret names through Terraform variables. Configure `mail_host`, `mail_username`,
`mail_from`, and a Secret Manager SMTP-password secret for production mail. The
referenced secrets must contain the matching database password, SMTP password,
and a Base64-encoded JWT key of at least 32 bytes. Configure the `stg` and `prd` GitHub environments with
`GCP_PROJECT_ID`, `GCP_REGION`, `FRONTEND_BUCKET`, `URL_MAP`,
`WORKLOAD_IDENTITY_PROVIDER`, and `DEPLOY_SERVICE_ACCOUNT` variables for the
manual Deploy workflow. Select the branch in the workflow dispatch UI.
