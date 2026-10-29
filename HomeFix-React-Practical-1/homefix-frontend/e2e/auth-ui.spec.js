import { expect, test } from "@playwright/test";

const roleDestinations = [
  { role: "CUSTOMER", path: "/customer" },
  { role: "PROVIDER", path: "/provider" },
  { role: "ADMIN", path: "/admin" },
];

test("sends the selected role to the existing login API for all account types", async ({ page }) => {
  const submittedRoles = [];
  await page.route("**/api/auth/login", async (route) => {
    submittedRoles.push(JSON.parse(route.request().postData()));
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({ message: "Invalid email or password." }),
    });
  });

  await page.goto("/login");
  await expect(page.locator(".homefix-role-option")).toHaveCount(3);

  for (const [index, role] of ["CUSTOMER", "PROVIDER", "ADMIN"].entries()) {
    await page.locator(".homefix-role-option").nth(index).click();
    await page.getByLabel("Email Address").fill("login-check@example.test");
    await page.getByLabel("Password", { exact: true }).fill("incorrect-password");

    const loginResponse = page.waitForResponse((response) =>
      response.url().endsWith("/api/auth/login")
      && response.request().method() === "POST"
    );
    await page.locator("form").getByRole("button", { name: "Sign In" }).click();
    expect((await loginResponse).status()).toBe(401);
    await expect(page.locator(".homefix-auth-status"))
      .toHaveText("Invalid email or password.");
    expect(submittedRoles.at(-1).role).toBe(role);
  }

  expect(submittedRoles).toHaveLength(3);
});

for (const { role, path } of roleDestinations) {
  test(`routes a verified ${role.toLowerCase()} session to ${path}`, async ({ page }) => {
    const user = {
      id: 700,
      email: "signed-in@example.test",
      fullName: "HomeFix User",
      role,
      providerApproved: true,
    };
    const pageErrors = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));

    await page.route("**/api/**", async (route) => {
      const { pathname } = new URL(route.request().url());
      if (pathname.endsWith("/auth/login")) {
        return route.fulfill({
          json: { accessToken: "verified-test-token", user },
        });
      }
      if (pathname.endsWith("/auth/me")) {
        return route.fulfill({ json: { user } });
      }
      if (pathname.endsWith("/provider/profile")) {
        return route.fulfill({ json: user });
      }
      if (pathname.endsWith("/payments/config")) {
        return route.fulfill({ json: { gatewayEnabled: false } });
      }
      return route.fulfill({ json: [] });
    });

    await page.goto("/login");
    const roleIndex = roleDestinations.findIndex((destination) => destination.role === role);
    await page.locator(".homefix-role-option").nth(roleIndex).click();
    await page.getByLabel("Email Address").fill(user.email);
    await page.getByLabel("Password", { exact: true }).fill("password123");
    await page.locator("form").getByRole("button", { name: "Sign In" }).click();

    await expect(page).toHaveURL(new RegExp(`${path.replaceAll("/", "\\/")}$`));
    expect(pageErrors).toEqual([]);
  });
}

test("offers customer registration but never exposes administrator signup", async ({ page }) => {
  const registrationRequests = [];
  await page.route("**/api/auth/register", async (route) => {
    registrationRequests.push(JSON.parse(route.request().postData()));
    await route.fulfill({ status: 201, json: { message: "Account created." } });
  });

  await page.goto("/login");
  await expect(page.locator(".homefix-brand-icon")).toBeVisible();
  await expect(page.getByText("Home services, made simple", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Sign Up", exact: true }).click();
  await expect(page.getByLabel("Full Name")).toBeVisible();
  await expect(page.getByLabel("Phone Number")).toBeVisible();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("type", "password");
  await page.getByRole("button", { name: "Show password" }).click();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("type", "text");
  await page.getByRole("button", { name: "Hide password" }).click();

  await page.locator(".homefix-role-option").nth(2).click();
  await expect(page.getByRole("status"))
    .toContainText("Administrator accounts are provisioned by HomeFix");
  await expect(page.locator("form")).toHaveCount(0);
  expect(registrationRequests).toHaveLength(0);
});

test("submits customer registration through the existing backend contract", async ({ page }) => {
  const registrationRequests = [];
  await page.route("**/api/auth/register", async (route) => {
    registrationRequests.push(JSON.parse(route.request().postData()));
    await route.fulfill({ status: 201, json: { message: "Account created." } });
  });

  await page.goto("/login");
  await page.getByRole("button", { name: "Sign Up", exact: true }).click();
  await page.getByLabel("Full Name").fill("Jordan Customer");
  await page.getByLabel("Email Address").fill("jordan@example.test");
  await page.getByLabel("Password", { exact: true }).fill("password123");
  await page.getByLabel("Phone Number").fill("9876543210");
  await page.locator("form").getByRole("button", { name: "Create account" }).click();

  await expect(page.locator(".homefix-auth-status")).toHaveText("Account created.");
  expect(registrationRequests).toEqual([{
    fullName: "Jordan Customer",
    email: "jordan@example.test",
    password: "password123",
    role: "CUSTOMER",
    phone: "9876543210",
    serviceCategory: "",
    experience: "",
    serviceArea: "",
  }]);
});

test("keeps the authentication layout within tablet and mobile viewports", async ({ page }) => {
  await page.goto("/login");

  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    const dimensions = await page.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }));
    expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
    await expect(page.locator(".homefix-role-option")).toHaveCount(3);
  }
});
