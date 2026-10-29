import { expect, test } from "@playwright/test";

const admin = {
  id: 7,
  fullName: "HomeFix Administrator",
  email: "admin@example.test",
  role: "ADMIN",
};

test("admin can view booking status without status editing controls", async ({ page }) => {
  const booking = {
    id: 81,
    bookingId: "HF-ADMIN-81",
    customerName: "Asha Customer",
    providerName: "Assigned Provider",
    service: "PLUMBING",
    preferredDate: "2026-12-05",
    status: "PENDING",
    price: 300,
  };
  let statusUpdateRequested = false;

  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "admin-booking-status-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, admin);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    const method = route.request().method();
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: admin } });
    }
    if (pathname.endsWith("/admin/bookings/81/status") && method === "PUT") {
      statusUpdateRequested = true;
      return route.fulfill({ json: booking });
    }
    if (pathname.endsWith("/admin/bookings")) {
      return route.fulfill({ json: [booking] });
    }
    if (pathname.endsWith("/payments/config")
      || pathname.endsWith("/admin/pricing-config")
      || pathname.endsWith("/admin/dashboard")) {
      return route.fulfill({ json: {} });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/admin");
  await expect(page.getByRole("heading", { name: "Administrator dashboard" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Refresh data" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Export bookings CSV" })).toHaveCount(0);
  await page.getByRole("button", { name: "Bookings", exact: true }).click();
  await expect(page.getByRole("cell", { name: "PENDING", exact: true })).toBeVisible();
  await expect(page.getByRole("columnheader", { name: "Status" })).toBeVisible();
  await expect(page.getByRole("columnheader", { name: "Change status" })).toHaveCount(0);
  await expect(page.getByRole("combobox", { name: "Set status for HF-ADMIN-81" })).toHaveCount(0);
  expect(statusUpdateRequested).toBe(false);
});
