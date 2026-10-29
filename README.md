# HomeFix

HomeFix is a home-services booking platform with a React/Vite client and a
Spring Boot REST API. The backend is the authority for identity, pricing,
provider approval, booking ownership, status changes, and persistent records.

## Technology

- Java 21 and Spring Boot 3.5.x
- Maven Wrapper
- Spring MVC, Spring Security, JWT bearer authentication, BCrypt
- Spring Data JPA and Hibernate
- MySQL 8 for runtime; H2 is test-scoped
- React 18, Vite, React Router, Axios, Bootstrap 5
- JUnit 5 and Spring Boot Test

## Project layout

- `homefix-lab/api` — Spring Boot API, Thymeleaf home page, persistence models,
  tests, and Dockerfile
- `HomeFix-React-Practical-1/homefix-frontend` — React customer, provider, and
  administrator application
- `IMPLEMENTATION_PLAN.md` — requirements mapping, implementation decisions,
  and verification checklist

## Prerequisites

- JDK 21
- MySQL 8
- Node.js 20 or newer and npm

The Maven Wrapper is included in the API directory. The API uses `DB_USERNAME`,
`DB_PASSWORD`, and `JWT_SECRET`; set them in the process environment rather
than committing secrets to properties files. `JWT_SECRET` must contain at
least 32 UTF-8 bytes. For local development, create a random value with
`openssl rand -base64 48`.

## MySQL setup

Create a dedicated database and application user in MySQL (adjust the local
account/host policy for your environment):

```sql
CREATE DATABASE homefix CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'homefix_app'@'localhost' IDENTIFIED BY 'replace-with-a-local-secret';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON homefix.* TO 'homefix_app'@'localhost';
```

The application uses Hibernate `ddl-auto=update` for additive schema updates.
Back up an existing database before deployment. Application startup does not
drop or replace existing tables.

## Run locally on Windows

The VS Code API launch configuration reads `homefix-lab/.env`. Create your
private local file from the example and replace all placeholders:

```powershell
Set-Location "C:\path\to\HOMEFIX\homefix-lab"
if (-not (Test-Path ".env")) { Copy-Item .env.example .env }
notepad .env
```

`.env` is ignored by Git. Never commit it. Keep the same `JWT_SECRET` between
backend restarts; rotating it invalidates active tokens and returns HTTP 401
for protected API requests. In PowerShell, load the file into the current
terminal without printing its values:

```powershell
Get-Content ".env" | ForEach-Object {
  if ($_ -match '^\s*([^#\s=]+)\s*=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
  }
}
```

Optionally configure service radius and development location simulation:

```powershell
$env:SERVICE_RADIUS_KM = "25"
$env:LOCATION_SIMULATION_ENABLED = "false"
```

Set the administrator bootstrap credentials only for an explicitly controlled
initial setup. The bootstrap initializer creates an admin only when both
`ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` are configured. The
bootstrap password must be at least 12 characters. Use a unique password and
remove the bootstrap password from the runtime environment after the account
is created.

Start the API:

```powershell
Set-Location "C:\path\to\HOMEFIX\homefix-lab\api"
.\mvnw.cmd spring-boot:run
```

In a second terminal, start the React application:

```powershell
Set-Location "C:\path\to\HOMEFIX\HomeFix-React-Practical-1\homefix-frontend"
npm ci
$env:VITE_API_BASE_URL = "http://localhost:8083/api"
npm run dev
```

Open the Vite URL shown in the terminal. The API health endpoint is
`http://localhost:8083/actuator/health`. Admin accounts are not self-registering;
sign in using the configured bootstrap account.

## Tests and production build

Run backend tests using the API's isolated test database:

```powershell
Set-Location "C:\path\to\HOMEFIX\homefix-lab\api"
.\mvnw.cmd clean test
```

Build the backend artifact:

```powershell
.\mvnw.cmd clean package
```

Build the frontend:

```powershell
Set-Location "C:\path\to\HOMEFIX\HomeFix-React-Practical-1\homefix-frontend"
npm ci
npm run build
```

Run the browser smoke tests with the backend and Vite server already running:

```powershell
npx playwright install chromium
npm run test:e2e
```

The browser tests use the live API for invalid-login handling and the provider
service catalog; they do not create or modify user or booking records.

## Implemented workflows and operational boundaries

- Customer and provider registration/login, JWT authentication, account
  profiles, saved customer addresses, and role-protected APIs
- Database-backed service catalog, booking creation/history/cancellation,
  provider acceptance and status progression, booking history, and reviews
- Provider verification documents with extension, content signature, and size
  checks; documents are stored outside static web resources and protected by
  owner/admin authorization
- Provider skills, location, service radius, work schedules, online state, and
  nearby matching using distance, approval, availability, and appointment
  conflict checks
- Administrator dashboard for database metrics, user management, provider and
  document verification, service categories, bookings, support, reviews,
  manual payment ledger, and CSV booking export
- Booking status history, notifications, invoices, audit records, and support
  ticket persistence

Browser location is permission-based; a manual address remains available.
Provider matching requires valid coordinates and an active category. The
Mumbai/Pune location simulation is opt-in through
`LOCATION_SIMULATION_ENABLED=true`.

Payments and refunds are recorded as manual ledger changes only. No payment
gateway or external charge/refund is implemented. Notifications are persisted
in the API; email/SMS delivery is not configured. Private documents use local
filesystem storage by default; configure durable protected storage for a
multi-instance deployment. Production deployment should use TLS, a dedicated
database identity, rotated secrets, backups, restricted document storage, and
an explicit CORS origin.

## Docker

The API Dockerfile builds with Java 21 and launches the packaged application:

```powershell
Set-Location "C:\path\to\HOMEFIX\homefix-lab\api"
docker build -t homefix-api .
docker run --rm -p 8083:8083 `
  -e DB_HOST=<mysql-host> `
  -e DB_PORT=3306 `
  -e DB_NAME=homefix `
  -e DB_USERNAME=<database-user> `
  -e DB_PASSWORD=<database-password> `
  -e JWT_SECRET=<random-secret> `
  -e FRONTEND_ORIGIN=<https-frontend-origin> `
  homefix-api
```

Use a production secret manager and persistent protected volume for uploaded
verification documents in deployed environments.
