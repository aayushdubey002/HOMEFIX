import { expect, test } from "@playwright/test";
import { routeUpdatedApi } from "./api-routing.js";

test("loads provider registration services from the live catalog", async ({ page }) => {
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await routeUpdatedApi(page);

  await page.goto("/login");
  await page.getByRole("button", { name: "Sign Up", exact: true }).click();
  await page.getByRole("button", { name: /Service Provider/ }).click();

  await expect(page.getByRole("option", { name: "Appliance Repair" })).toBeAttached();
  await expect(page.getByRole("option", { name: "Painting" })).toBeAttached();
  expect(pageErrors).toEqual([]);
});
