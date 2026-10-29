import { expect, test } from "@playwright/test";

const customer = {
  id: 42,
  fullName: "Asha Customer",
  email: "asha@example.test",
  role: "CUSTOMER",
  phone: "9876543210",
};

const services = [
  {
    slug: "plumber",
    name: "Plumber",
    service: "PLUMBING",
    description: "Pipes, taps and leaks",
    startingPrice: 300,
  },
  {
    slug: "electrician",
    name: "Electrician",
    service: "ELECTRICAL",
    description: "Wiring and lighting repairs",
    startingPrice: 500,
  },
  {
    slug: "cleaning",
    name: "Cleaning",
    service: "CLEANING",
    description: "Home cleaning services",
    startingPrice: 999,
  },
];

async function prepareCustomerPage(page, { addresses = [], notifications = [] } = {}) {
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "browser-test-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, customer);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    let body = {};

    if (pathname.endsWith("/auth/me")) {
      body = { user: customer };
    } else if (pathname.endsWith("/services/catalog")) {
      body = services;
    } else if (pathname.endsWith("/service-requests")) {
      body = [];
    } else if (pathname.endsWith("/notifications")) {
      body = notifications;
    } else if (pathname.endsWith("/customer/profile")) {
      body = customer;
    } else if (pathname.endsWith("/customer/addresses")) {
      body = addresses;
    }

    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(body),
    });
  });
}

test("searches and filters live catalog categories with a clear empty state", async ({ page }) => {
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await prepareCustomerPage(page);

  await page.goto("/customer");
  await expect(page.getByRole("heading", { name: "What do you need help with?" }))
    .toBeVisible();
  await expect(page.locator(".home-service-card")).toHaveCount(3);

  await page.getByRole("searchbox", { name: "Search services" }).fill("pipes");
  await expect(page.locator(".home-service-card")).toHaveCount(1);
  await expect(page.getByRole("heading", { name: "Plumber" })).toBeVisible();

  await page.getByRole("searchbox", { name: "Search services" }).fill("");
  await page.getByRole("combobox", { name: "Filter by service category" })
    .selectOption("ELECTRICAL");
  await expect(page.locator(".home-service-card")).toHaveCount(1);
  await expect(page.getByRole("heading", { name: "Electrician" })).toBeVisible();

  await page.getByRole("searchbox", { name: "Search services" }).fill("pipes");
  await page.getByRole("combobox", { name: "Filter by service category" })
    .selectOption("CLEANING");
  await expect(page.getByText("No services match those filters.")).toBeVisible();
  await page.getByRole("button", { name: "Clear search and filters" }).click();
  await expect(page.locator(".home-service-card")).toHaveCount(3);
  expect(pageErrors).toEqual([]);
});

test("opens the protected customer profile and saved-address page", async ({ page }) => {
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await prepareCustomerPage(page);

  await page.goto("/customer");
  await page.getByRole("main").getByRole("link", { name: "Profile & addresses" }).click();

  await expect(page).toHaveURL(/\/customer\/profile$/);
  await expect(page.getByRole("heading", { name: "Profile and saved addresses" }))
    .toBeVisible();
  await expect(page.getByLabel("Full name")).toHaveValue(customer.fullName);
  await expect(page.getByText("No saved addresses yet.")).toBeVisible();
  expect(pageErrors).toEqual([]);
});

test("shows saved location and unread notifications and stays within mobile width", async ({ page }) => {
  await prepareCustomerPage(page, {
    addresses: [{
      id: 8,
      label: "Home",
      streetAddress: "12 Main Street",
      city: "Pune",
      postalCode: "411001",
      primaryAddress: true,
    }],
    notifications: [{
      id: 19,
      title: "Booking update",
      message: "Your appointment is confirmed.",
      read: false,
    }],
  });

  await page.goto("/customer");
  await expect(page.getByRole("combobox", { name: "Choose a saved service address" }))
    .toHaveValue("8");
  await expect(page.locator(".customer-sidebar-count")).toHaveText("1");
  await expect.poll(async () => page.locator(
    ".customer-service-card .customer-service-image"
  ).first().evaluate((image) => image.complete && image.naturalWidth > 0)).toBe(true);

  await page.getByRole("button", { name: /Notifications/ }).click();
  await expect(page.getByText("Your appointment is confirmed.")).toBeVisible();

  for (const width of [1024, 768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth))
      .toBeLessThanOrEqual(width);
  }

  await page.getByRole("link", { name: "Book Plumber" }).click();
  await expect(page).toHaveURL(/\/customer\/book\/plumber$/);
  await expect(page.getByRole("heading", { name: "Book Plumber" })).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByLabel("Describe the problem").fill("Kitchen sink is leaking.");
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByLabel("Service address"))
    .toHaveValue("12 Main Street, 411001");
  await expect(page.getByLabel("City")).toHaveValue("Pune");
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByRole("heading", { name: "When would you like the service?" }))
    .toBeVisible();
  await expect(page.getByRole("button", { name: "Find nearby providers" }))
    .toHaveCount(0);
});

test("uses a category icon fallback when a service photo fails to load", async ({ page }) => {
  await prepareCustomerPage(page);
  await page.route("**/images/services/plumbing.jpg", (route) =>
    route.fulfill({ status: 404, body: "" })
  );

  await page.goto("/customer");
  await expect(page.locator(".customer-service-card .service-artwork-fallback").first())
    .toBeVisible();
});
