import { expect, test } from "@playwright/test";
import { routeUpdatedApi } from "./api-routing.js";

test("shows the backend error for invalid login credentials", async ({ page }) => {
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await routeUpdatedApi(page);

  await page.goto("/login");
  await page.getByLabel("Email Address").fill("missing-account@example.test");
  await page.getByLabel("Password", { exact: true }).fill("incorrect-password");

  const loginResponse = page.waitForResponse((response) =>
    response.url().endsWith("/api/auth/login")
    && response.request().method() === "POST"
  );
  await page.locator("form").getByRole("button", { name: "Sign In" }).click();

  expect((await loginResponse).status()).toBe(401);
  await expect(page.locator(".homefix-auth-status"))
    .toHaveText("Invalid email or password.");
  expect(pageErrors).toEqual([]);
});
