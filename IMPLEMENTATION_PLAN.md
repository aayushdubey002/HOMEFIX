# HomeFix Full-Stack Implementation Plan

## Architecture decision

Keep the existing React/Vite customer and provider application and its Spring
REST API. Extend them in place instead of replacing a working client. The API
will remain the source of truth for identity, prices, booking ownership and
state. Add Thymeleaf as a small server-rendered public entry/operations surface;
do not duplicate authenticated business workflows between two clients. REST
authentication remains stateless JWT bearer authentication, so browsers do not
automatically attach credentials cross-site; Thymeleaf form requests use
Spring Security CSRF protection.

The supported runtime target is Java 21 with the latest stable Spring Boot 3.x
available when this plan was prepared: Spring Boot 3.5.16 (verified in Maven
Central metadata on 2026-10-21). MySQL 8 is the persistent runtime database;
H2 remains isolated to tests. Existing databases are upgraded additively and
are never dropped by application startup.

## Existing state to preserve

- React/Vite booking, catalog, login, customer booking history, and provider
  request acceptance/dismissal screens.
- Spring REST endpoints, BCrypt password hashing, JWT authentication, MySQL
  bookings, service categories/prices, and the H2 test profile.
- Existing uncommitted worktree edits are pre-existing and must be preserved.

## Work items and requirement mapping

| ID | Work item | Requirements | Verification |
| --- | --- | --- | --- |
| T001 | Standardize the backend on Java 21 and Spring Boot 3.x; retain Maven and existing compatible API behavior. | Platform, existing-work preservation | Maven test-compile/test on Java 21 |
| T002 | Externalize database/JWT secrets, configure MySQL 8 safely, and add additive relational entities, keys, indexes, timestamps, and test-only H2 settings. | Database, security | H2 integration tests; configuration inspection |
| T003 | Add customer profiles/addresses, provider profiles/skills/availability/documents, categories, booking history, and database repositories. | Customer, provider, database | Persistence and validation tests |
| T004 | Implement role-aware auth, admin bootstrap via supplied environment variables, provider review/approval, profile, and privacy-safe document access. | Customer, provider, admin, security | Auth and authorization integration tests |
| T005 | Implement category/availability/radius matching, Mumbai/Pune development coordinates, transactional booking/status history, cancellation, and overlap prevention. | GPS, booking workflow | Matching/state-transition/overlap tests |
| T006 | Implement reviews, notifications, complaints, invoice records, payment/refund records without claiming a gateway is integrated, and audit events. | Customer, provider, admin | Database-backed service/API tests |
| T007 | Add customer/provider/admin UI routes and working forms/actions while preserving current booking UX and responsive design. | User interface, all roles | Frontend production build; API workflow tests |
| T008 | Add Thymeleaf public entry view, operational setup documentation, feature checklist, and safe deployment instructions. | Thymeleaf, README, checklist | Documentation and MVC view tests |
| T009 | Run the backend suite/build and frontend production build; address failures and report any external services that cannot be verified locally. | Testing, deliverable | `mvn clean test`, `npm run build` |

## Phases and completion checklist

- [x] Phase 1 — inspect current architecture, features, data, configuration,
  existing errors and worktree changes; no files modified during inspection.
- [x] Phase 2 — record this plan and identify compatibility/dependency changes.
- [x] Phase 3 — Java 21 / Spring Boot 3.x, configuration, relational model,
  repositories, additive schema initialization.
- [x] Phase 4 — role-based authentication, customer/provider/admin dashboards
  and provider verification controls.
- [x] Phase 5 — service search, location matching, availability, transactional
  booking workflow, status restrictions and appointment conflict checks.
- [x] Phase 6 — reviews, notifications, complaints, invoices, payment/refund
  records and admin management.
- [x] Phase 7 — automated H2 test suite and frontend production build pass.
  The Java 21 runtime itself was unavailable; verification ran on Java 26 with
  Java 21 compilation target.
- [x] Phase 8 — root README setup/deployment instructions and final checklist.

## Missing or constrained integrations

- A Java 21 JDK was not detected after the installer attempt; the installer
  process exited unsuccessfully and the machine still resolves Java 26. The
  project targets Java 21 (`--release 21`) and the backend suite passed on
  Java 26, but execution on a Java 21 runtime remains unverified.
- No payment-gateway credentials/provider are present. Implement persistent
  payment/refund records and administrative controls only; do not create or
  imply successful real transactions.
- GPS permission and geolocation are browser capabilities, not an external
  map/routing integration. Coordinates are optional; manual address booking
  remains available, and distance matching is calculated server-side.
- Email/SMS delivery, cloud document storage, and hosted MySQL availability are
  not configured. Notifications and document metadata are persisted locally;
  real third-party delivery is not claimed.
