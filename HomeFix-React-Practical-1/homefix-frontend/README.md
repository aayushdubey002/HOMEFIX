# HomeFix frontend

The frontend uses `https://homefix-api-humf.onrender.com/api` for local API requests. This is
configured in `.env.local`; `.env.example` is a safe template containing no
credentials or secrets.

## Run locally

Start the Spring Boot backend first. Create `homefix-lab/.env` once from the
example, add your local database credentials, and keep the same JWT secret
across backend restarts. Rotating `JWT_SECRET` invalidates existing login
tokens and causes protected requests to return HTTP 401. The `.env` file is
ignored by Git and must never be committed.

```powershell
Set-Location "C:\HOMEFIX PROJECT\homefix-lab"
if (-not (Test-Path ".env")) { Copy-Item ".env.example" ".env" }
notepad ".env"
Get-Content ".env" | ForEach-Object {
  if ($_ -match '^\s*([^#\s=]+)\s*=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
  }
}
Set-Location "C:\HOMEFIX PROJECT\homefix-lab\api"
.\mvnw.cmd spring-boot:run
```

Ensure the local MySQL server is running and the `homefix` database exists. The
backend listens on port `8083` and allows the frontend origin
`http://localhost:5173`. The backend uses `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` from its
process environment; never put them in frontend environment files. Do not
activate the machine-specific `local` profile for the environment-based setup.

In a second PowerShell terminal:

```powershell
Set-Location "C:\HOMEFIX PROJECT\HomeFix-React-Practical-1\homefix-frontend"
npm ci
npm run dev
```

Vite serves the frontend at `http://localhost:5173`. The test-only H2 database
configuration is in the backend's `src/test/resources/application.properties`;
local application runs use MySQL.

Required backend environment variables are `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` (at least 32 UTF-8 bytes).
`FRONTEND_ORIGIN` defaults to `http://localhost:5173`; `PORT`, service-radius,
location-simulation, and document-storage settings can also be configured
through the supplied `.env.example`. Payment, email, and SMS delivery are not
configured integrations unless their documented settings are supplied.

## Razorpay UPI checkout

The backend keeps UPI checkout disabled until all three Razorpay values are
configured in the ignored `homefix-lab/.env` file or the backend process
environment:

```text
RAZORPAY_KEY_ID=
RAZORPAY_KEY_SECRET=
RAZORPAY_WEBHOOK_SECRET=
```

Use the key ID and key secret from the same Razorpay test-mode account. Create
a webhook for `payment.captured`, `payment.failed`, `refund.created`,
`refund.processed`, and `refund.failed`; use the webhook secret
generated for that endpoint, and point it to
`https://<your-public-backend-host>/api/payments/razorpay/webhook`. Keep all
three values private and do not commit them. Razorpay Checkout is offered only
after a UPI booking is marked complete. A browser return does not confirm
payment: HomeFix verifies the gateway response and signed webhooks before
updating the persistent payment status. Testing this against Razorpay requires
an account with test credentials and a publicly reachable webhook URL; no
gateway payment is simulated by the local test suite.

Checkout and administrator refund requests remain disabled unless all three
test credentials are present and the key ID begins with `rzp_test_`. Live
keys are deliberately rejected by this integration. To test webhooks locally,
expose the backend only through a trusted development tunnel and configure
that public HTTPS URL in the Razorpay test dashboard; never use production
keys for local testing.

## Pricing, invoice, and refunds

Administrators can configure fixed or percentage discounts, eligible service
codes, minimum subtotals, and tax percentage in the pricing controls. All
values default to zero/no discount, are validated and calculated by the
backend, and are saved as part of the booking's price history and invoice
breakdown. Customer and admin screens display the saved subtotal, discount,
tax, final amount, transaction status, and refund history.

An administrator refund request requires a captured Razorpay test payment,
an amount, and a reason. The backend reserves the remaining refundable
balance, records the admin and request in its audit history, and submits the
request to Razorpay. Only a Razorpay response or correctly signed refund
webhook changes a refund to processed; pending and failed states remain
visible and do not fabricate successful refunds. Retries reuse an
idempotency key, while refunds exceeding the available balance are rejected.
No live payment or refund flow has been verified by the automated tests.

For deployment, set the three Razorpay variables only in the backend host's
encrypted environment/secret manager, keep the configured key in test mode,
and register the deployed HTTPS webhook endpoint in the Razorpay test
dashboard. Do not copy these settings into Vite or other frontend environment
variables. The backend's Hibernate schema update creates the refund ledger
and updates the saved price/invoice breakdown columns; back up the MySQL
database and review schema changes before deploying an application update.

## Validate

Run the backend test suite with the in-memory H2 test database:

```powershell
Set-Location "C:\HOMEFIX PROJECT\homefix-lab\api"
.\mvnw.cmd clean test
```

Build the frontend and run the deterministic customer experience browser tests:

```powershell
Set-Location "C:\HOMEFIX PROJECT\HomeFix-React-Practical-1\homefix-frontend"
npx playwright install chromium
npm run build
npx playwright test e2e/customer-experience.spec.js
```

With MySQL, the API, and Vite running, `npm run test:e2e` also exercises the
existing live-API login, protected-route, and provider-catalog browser checks.
The customer experience tests use isolated API fixtures; backend workflow and
authorization behavior is covered by the H2 integration tests.