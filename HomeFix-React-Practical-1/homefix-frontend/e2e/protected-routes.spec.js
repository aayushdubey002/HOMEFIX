import { expect, test } from "@playwright/test";

test("redirects an unauthenticated customer to sign in", async ({ page }) => {
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));

  await page.goto("/customer/bookings");

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Welcome Back!" })).toBeVisible();
  expect(pageErrors).toEqual([]);
});
