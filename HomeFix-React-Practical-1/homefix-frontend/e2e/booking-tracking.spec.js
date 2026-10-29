import { expect, test } from "@playwright/test";

const customer = {
  id: 42,
  fullName: "Asha Customer",
  email: "asha@example.test",
  role: "CUSTOMER",
  phone: "9876543210",
};

test("customer booking progress reflects provider acceptance from the API", async ({ page }) => {
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "booking-tracking-test-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, customer);

  let bookingReadCount = 0;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/notifications")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/services/catalog")) {
      return route.fulfill({
        json: [{
          slug: "plumber",
          name: "Plumber",
          service: "PLUMBING",
          startingPrice: 300,
          imageKey: "plumbing",
        }],
      });
    }
    if (pathname.endsWith("/service-requests")) {
      bookingReadCount += 1;
      const accepted = bookingReadCount > 2;
      return route.fulfill({
        json: [{
          id: 91,
          bookingId: "HF-TRACK-91",
          service: "PLUMBING",
          customerName: customer.fullName,
          preferredDate: "2026-12-02",
          preferredAt: "2026-12-02T09:00:00",
          address: "12 Main Street",
          price: 300,
          status: accepted ? "ACCEPTED" : "PENDING",
          providerName: accepted ? "Sam Provider" : "Provider will be assigned",
          provider: accepted ? { id: 52, fullName: "Sam Provider" } : null,
        }],
      });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/91");
  await expect(page.getByRole("heading", { name: "Plumber" })).toBeVisible();
  await expect(page.locator(".customer-booking-hero-artwork img"))
    .toHaveAttribute("src", "/images/services/plumbing.jpg");
  await expect(page.locator(".booking-status-step")).toHaveCount(7);
  await expect.poll(() => page.locator(".booking-status-steps").evaluate(
    (element) => getComputedStyle(element).display,
  )).toBe("flex");
  await expect(page.getByText("Your request is waiting for an eligible provider to accept it."))
    .toBeVisible();
  await expect(page.getByText("Provider accepted", { exact: true })).toBeVisible();

  await expect(page.getByText("A provider accepted your request. The appointment is being confirmed."))
    .toBeVisible({ timeout: 8000 });
  await expect(page.getByText("Sam Provider", { exact: true })).toBeVisible();
  await expect(page.locator(".booking-status-step.is-current")).toContainText("Provider accepted");

  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.locator(".booking-status-steps").evaluate(
    (element) => getComputedStyle(element).display,
  )).toBe("grid");
  await expect.poll(() => page.evaluate(
    () => document.documentElement.scrollWidth <= window.innerWidth,
  )).toBe(true);
});
