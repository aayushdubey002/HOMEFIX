# HomeFix React Frontend

React customer and provider dashboards for HomeFix. The customer booking flow
uses React Router and the centralized Axios API service. The Spring Boot API
is the authority for user identity, service pricing, booking state, and provider
assignment.

## Run locally

```bash
npm install
npm run dev
```

Open the URL shown by Vite, normally `http://localhost:5173/`.

## Customer pages

- `/customer` — service categories and starting prices
- `/customer/book/:serviceSlug` — service-specific booking form
- `/customer/booking-success/:bookingId` — saved booking confirmation
- `/customer/bookings` — the signed-in customer's bookings

Customer routes require a signed-in customer account. Provider accounts continue
to use `/provider`; unauthenticated visitors are sent to `/login`.

The booking form submits to `POST /api/service-requests?userId={customerId}`.
The API stores the booking and returns its booking ID, price, and status. The
confirmation page can reload booking details through the customer's existing
`GET /api/service-requests?userId={customerId}` endpoint.

The frontend does not insert sample bookings or switch to a success page unless
the API confirms the booking was saved.

## API configuration

The default API base URL is the existing Render API:
`https://homefix-r9ca.onrender.com/api`. To point a local frontend at a different
backend, set `VITE_API_BASE_URL` to the full API base URL before running Vite or
building the frontend. Do not put database credentials or JWT signing secrets
in frontend environment variables.

The backend issues one-hour HS256 bearer tokens. Configure `JWT_SECRET` on the
backend with a stable random value of at least 32 UTF-8 bytes, and configure
`FRONTEND_ORIGIN` to the exact deployed frontend origin. Neither value belongs
in React source. Local backend runs also need the existing Aiven `DB_HOST`,
`DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` values; they have not
been changed by this work.

The backend creates the additive `booking_dismissals` table on startup using
the existing `spring.jpa.hibernate.ddl-auto=update` setting. Existing rows are
not deleted. Existing Maven tests use the project's H2 test configuration and
do not verify the Aiven MySQL instance.
