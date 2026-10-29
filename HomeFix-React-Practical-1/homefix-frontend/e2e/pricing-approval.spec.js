import { expect, test } from "@playwright/test";

const customer = {
  id: 21,
  fullName: "Price Review Customer",
  email: "price-review@example.test",
  role: "CUSTOMER",
};

test("customer explicitly approves a persisted provider extra charge", async ({ page }) => {
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "pricing-review-test-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, customer);

  let approved = false;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/notifications")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/service-requests/91/price-proposals/7")) {
      expect(route.request().method()).toBe("PUT");
      expect(route.request().postDataJSON()).toEqual({ decision: "APPROVED" });
      approved = true;
      return route.fulfill({ json: { id: 7, status: "APPROVED", proposedTotal: 450 } });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({
        json: [{
          id: 91,
          bookingId: "HF-PRICE-91",
          service: "PLUMBING",
          customerName: customer.fullName,
          preferredDate: "2026-12-02",
          preferredAt: "2026-12-02T09:00:00",
          address: "12 Main Street",
          price: approved ? 450 : 300,
          status: "ACCEPTED",
          providerName: "Saved Provider",
          provider: { id: 52, fullName: "Saved Provider" },
          ...(approved ? {} : {
            pendingPriceProposalId: 7,
            proposedPrice: 450,
            priceProposalExplanation: "Provider base rate for requested work.",
          }),
        }],
      });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/91");
  await expect(page.getByText("Provider requests an extra charge")).toBeVisible();
  await expect(page.getByText("New total if approved: ₹450")).toBeVisible();
  await page.getByRole("button", { name: "Approve extra charge" }).click();
  await expect(page.getByText("Extra charge approved. The booking total has been updated."))
    .toBeVisible();
  await expect(page.getByText("Provider requests an extra charge")).toHaveCount(0);
  await expect(page.getByText("₹450.00", { exact: true })).toBeVisible();
});
