import { expect, test } from "@playwright/test";

const admin = {
  id: 7,
  fullName: "HomeFix Administrator",
  email: "admin@example.test",
  role: "ADMIN",
};

const customer = {
  id: 42,
  fullName: "Asha Customer",
  email: "asha@example.test",
  role: "CUSTOMER",
  phone: "9876543210",
};

test("admin-added services appear in the customer catalog with the selected local image and booking flow", async ({ page }) => {
  let currentUser = admin;
  const categories = [];
  let createPayload;

  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "browser-test-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, admin);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    const method = route.request().method();

    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: currentUser } });
    }

    if (pathname.endsWith("/admin/categories")) {
      if (method === "POST") {
        createPayload = route.request().postDataJSON();
        const category = { id: 88, ...createPayload };
        categories.push(category);
        return route.fulfill({ status: 201, json: category });
      }
      return route.fulfill({ json: categories });
    }

    if (pathname.endsWith("/services/catalog")) {
      return route.fulfill({
        json: categories.filter((category) => category.active).map((category) => ({
          slug: category.slug,
          name: category.name,
          service: category.serviceCode,
          description: category.description,
          startingPrice: category.startingPrice,
          imageKey: category.imageKey,
        })),
      });
    }

    if (pathname.endsWith("/service-requests")) return route.fulfill({ json: [] });
    if (pathname.endsWith("/notifications")) return route.fulfill({ json: [] });
    if (pathname.endsWith("/customer/profile")) return route.fulfill({ json: customer });
    if (pathname.endsWith("/customer/addresses")) return route.fulfill({ json: [] });
    if (pathname.endsWith("/payments/config")) return route.fulfill({ json: {} });
    if (pathname.endsWith("/admin/pricing-config")) return route.fulfill({ json: {} });
    if (pathname.endsWith("/admin/dashboard")) return route.fulfill({ json: {} });
    if (pathname.includes("/admin/")) return route.fulfill({ json: [] });
    return route.fulfill({ json: {} });
  });

  await page.goto("/admin");
  await page.getByRole("button", { name: "Services", exact: true }).click();
  await page.getByPlaceholder("Name", { exact: true }).fill("Garden Care");
  await page.getByPlaceholder("Slug (e.g. ac-repair)").fill("garden-care");
  await page.getByPlaceholder("Service code").fill("GARDEN CARE");
  await page.getByPlaceholder("Starting price (₹)").fill("850");
  await page.getByPlaceholder("Description").fill("Garden maintenance and care.");
  await page.getByRole("combobox", { name: "Service card image" }).selectOption("cleaning");
  await expect(page.locator(".admin-category-image-preview img").first())
    .toHaveAttribute("src", "/images/services/cleaning.jpg");
  await page.getByRole("button", { name: "Add category" }).click();

  await expect(page.getByRole("status")).toContainText("Service category added.");
  expect(createPayload).toMatchObject({
    name: "Garden Care",
    slug: "garden-care",
    serviceCode: "GARDEN CARE",
    imageKey: "cleaning",
  });

  currentUser = customer;
  await page.evaluate((user) => {
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, customer);
  await page.reload();

  await expect(page).toHaveURL(/\/customer$/);
  await expect(page.getByRole("heading", { name: "Garden Care" })).toBeVisible();
  await expect(page.locator(".customer-service-card .customer-service-image").last())
    .toHaveAttribute("src", "/images/services/cleaning.jpg");
  await expect(page.getByText("₹850")).toBeVisible();
  await page.getByRole("link", { name: "Book Garden Care" }).click();
  await expect(page).toHaveURL(/\/customer\/book\/garden-care$/);
  await expect(page.getByRole("heading", { name: "Book Garden Care" })).toBeVisible();
});

test("admin-uploaded service image is saved and shown in the customer catalog", async ({ page }) => {
  let currentUser = admin;
  const uploadedUrl = "/api/services/catalog/images/550e8400-e29b-41d4-a716-446655440000.png";
  const categories = [];
  let savedUpload;

  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "browser-test-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, admin);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    const method = route.request().method();
    if (pathname.endsWith("/auth/me")) return route.fulfill({ json: { user: currentUser } });
    if (pathname === "/api/admin/categories" && method === "POST") {
      const request = route.request();
      const contentType = request.headers()["content-type"] || "";
      const body = request.postData() || "";
      const categoryPart = body.match(/name="category"[^]*?\r\n\r\n([\s\S]*?)\r\n--/);
      savedUpload = {
        contentType,
        category: categoryPart ? JSON.parse(categoryPart[1]) : null,
        includesFilename: body.includes('filename="custom-appliance.png"'),
      };
      const category = { id: 89, ...savedUpload.category, imageUrl: uploadedUrl };
      categories.push(category);
      return route.fulfill({ status: 201, json: category });
    }
    if (pathname.endsWith("/admin/categories")) return route.fulfill({ json: categories });
    if (pathname.endsWith("/services/catalog")) {
      return route.fulfill({
        json: categories.filter((category) => category.active).map((category) => ({
          slug: category.slug,
          name: category.name,
          service: category.serviceCode,
          description: category.description,
          startingPrice: category.startingPrice,
          imageKey: category.imageKey,
          imageUrl: category.imageUrl,
        })),
      });
    }
    if (pathname.endsWith("/service-requests")) return route.fulfill({ json: [] });
    if (pathname.endsWith("/notifications")) return route.fulfill({ json: [] });
    if (pathname.endsWith("/customer/profile")) return route.fulfill({ json: customer });
    if (pathname.endsWith("/customer/addresses")) return route.fulfill({ json: [] });
    if (pathname.endsWith("/payments/config")) return route.fulfill({ json: {} });
    if (pathname.endsWith("/admin/pricing-config")) return route.fulfill({ json: {} });
    if (pathname.endsWith("/admin/dashboard")) return route.fulfill({ json: {} });
    if (pathname.includes("/admin/")) return route.fulfill({ json: [] });
    return route.fulfill({ json: {} });
  });

  await page.goto("/admin");
  await page.getByRole("button", { name: "Services", exact: true }).click();
  await page.getByPlaceholder("Name", { exact: true }).fill("Custom Appliance Repair");
  await page.getByPlaceholder("Slug (e.g. ac-repair)").fill("custom-appliance-repair");
  await page.getByPlaceholder("Service code").fill("CUSTOM_APPLIANCE_REPAIR");
  await page.getByPlaceholder("Starting price (₹)").fill("920");
  await page.getByPlaceholder("Description").fill("Repairs for household appliances.");
  await page.getByLabel("Upload service image").setInputFiles({
    name: "custom-appliance.png",
    mimeType: "image/png",
    buffer: Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l2cAAAAASUVORK5CYII=",
      "base64",
    ),
  });
  await expect(page.locator(".admin-category-form .admin-category-image-preview"))
    .toContainText("custom-appliance.png");
  await page.getByRole("button", { name: "Add category" }).click();
  await expect(page.getByRole("status")).toContainText("Service category added.");
  expect(savedUpload).toMatchObject({
    category: {
      name: "Custom Appliance Repair",
      slug: "custom-appliance-repair",
      serviceCode: "CUSTOM_APPLIANCE_REPAIR",
    },
    includesFilename: true,
  });
  expect(savedUpload.contentType).toMatch(/^multipart\/form-data;\s*boundary=/i);

  currentUser = customer;
  await page.evaluate((user) => sessionStorage.setItem("homefixUser", JSON.stringify(user)), customer);
  await page.reload();
  await expect(page).toHaveURL(/\/customer$/);
  await expect(page.locator(".customer-service-card .customer-service-image").first())
    .toHaveAttribute("src", `https://homefix-api-humf.onrender.com/api${uploadedUrl}`);
});
