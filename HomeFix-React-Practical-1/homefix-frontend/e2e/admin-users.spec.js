import { expect, test } from "@playwright/test";

const admin = {
  id: 99,
  email: "admin@example.test",
  fullName: "HomeFix Admin",
  role: "ADMIN",
};

test("admin can switch between customer and service provider information", async ({ page }) => {
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "test-admin-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, admin);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: admin } });
    }
    if (pathname.endsWith("/admin/users")) {
      return route.fulfill({ json: [
        {
          id: 11,
          name: "Customer One",
          email: "customer@example.test",
          phone: "9876543210",
          role: "CUSTOMER",
          active: true,
          createdAt: "2026-10-01T10:00:00Z",
        },
        {
          id: 12,
          name: "Provider One",
          email: "provider@example.test",
          phone: "9876501234",
          role: "PROVIDER",
          active: true,
          providerApproved: true,
          createdAt: "2026-10-02T10:00:00Z",
        },
        {
          id: 13,
          name: "Administrator",
          email: "other-admin@example.test",
          phone: "",
          role: "ADMIN",
          active: true,
        },
      ] });
    }
    if (pathname.endsWith("/admin/providers")) {
      return route.fulfill({ json: [{
        id: 12,
        name: "Provider One",
        email: "provider@example.test",
        phone: "9876501234",
        serviceCategory: "ELECTRICAL",
        experience: "3-5_YEARS",
        serviceArea: "Central District",
        status: "APPROVED",
        documentsReady: true,
        online: true,
      }] });
    }
    if (pathname.endsWith("/payments/config")
      || pathname.endsWith("/admin/pricing-config")
      || pathname.endsWith("/admin/dashboard")) {
      return route.fulfill({ json: {} });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/admin");
  await page.getByRole("button", { name: "Users", exact: true }).click();

  await expect(page.getByRole("button", { name: /Customers 1/ })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("columnheader", { name: "Customer" })).toBeVisible();
  await expect(page.getByText("Customer One")).toBeVisible();
  await expect(page.getByText("customer@example.test")).toBeVisible();
  await expect(page.locator(".admin-table tbody tr")).toHaveCount(1);

  await page.getByRole("button", { name: /Service Providers 1/ }).click();

  await expect(page.getByRole("columnheader", { name: "Service profile" })).toBeVisible();
  await expect(page.getByText("Provider One")).toBeVisible();
  await expect(page.getByText("ELECTRICAL")).toBeVisible();
  await expect(page.getByText("3-5_YEARS")).toBeVisible();
  await expect(page.getByText("Central District")).toBeVisible();
  await expect(page.getByText("APPROVED")).toBeVisible();
  await expect(page.locator(".admin-table tbody tr")).toHaveCount(1);
});
