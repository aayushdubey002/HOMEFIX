import { expect, test } from "@playwright/test";

const adminUser = {
  id: 99,
  email: "admin@example.test",
  fullName: "HomeFix Admin",
  role: "ADMIN",
};

test("keeps provider profiles visible and names a document endpoint failure", async ({ page }) => {
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "test-admin-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, adminUser);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: adminUser } });
    }
    if (pathname.endsWith("/admin/provider-documents")) {
      return route.fulfill({ status: 500, json: { message: "Document table unavailable." } });
    }
    if (pathname.endsWith("/admin/providers")) {
      return route.fulfill({
        json: [{
          id: 7,
          name: "Pending Provider",
          email: "provider@example.test",
          serviceCategory: "PLUMBING",
          status: "PENDING",
          documentsReady: false,
        }],
      });
    }
    if (pathname.endsWith("/admin/dashboard")) {
      return route.fulfill({ json: {} });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/admin");

  await expect(page.getByText("Some admin data could not be loaded.")).toContainText(
    "Provider documents: Document table unavailable."
  );
  await page.getByRole("button", { name: "Providers", exact: true }).click();
  await expect(page.getByRole("cell", { name: /Pending Provider/ })).toBeVisible();
});

test("explains approval requirements and removes a rejected provider from review queue", async ({ page }) => {
  const admin = { id: 99, email: "admin@example.test", fullName: "HomeFix Admin", role: "ADMIN" };
  let providerStatus = "PENDING";
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "test-admin-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, admin);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: admin } });
    }
    if (pathname.endsWith("/admin/providers")) {
      return route.fulfill({ json: [{
        id: 7,
        name: "Pending Provider",
        email: "provider@example.test",
        serviceCategory: "PLUMBING",
        status: providerStatus,
        documentsReady: false,
      }] });
    }
    if (pathname.endsWith("/admin/provider-documents")) {
      return route.fulfill({ json: [{
        id: 8,
        providerId: 7,
        providerName: "Pending Provider",
        providerEmail: "provider@example.test",
        documentType: "IDENTITY_PROOF",
        originalFilename: "identity.pdf",
        uploadedAt: "2026-10-24T00:00:00Z",
        sizeBytes: 500,
        status: "APPROVED",
        reviewNote: "",
      }] });
    }
    if (pathname.endsWith("/admin/providers/7/approval") && route.request().method() === "POST") {
      providerStatus = route.request().postDataJSON().status;
      return route.fulfill({ json: { status: providerStatus } });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { upiEnabled: false, upiId: "" } });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/admin");
  await page.getByRole("button", { name: "Providers", exact: true }).click();
  await expect(page.getByText("Identity: APPROVED")).toBeVisible();
  await expect(page.getByText("Service address: NOT UPLOADED")).toBeVisible();
  await expect(page.getByRole("button", { name: "Approve profile" })).toBeDisabled();
  await page.getByRole("button", { name: "Reject profile" }).click();

  await expect(page.getByText("No provider applications are waiting for review.")).toBeVisible();
  await expect(page.locator(".admin-table").first().getByRole("cell", { name: /Pending Provider/ })).toHaveCount(0);
  await expect(page.getByText("identity.pdf")).toBeVisible();
});

test("keeps every uploaded provider document visible after review", async ({ page }) => {
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "test-admin-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, adminUser);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: adminUser } });
    }
    if (pathname.endsWith("/admin/provider-documents")) {
      return route.fulfill({ json: [
        {
          id: 21,
          providerId: 7,
          providerName: "Verified Provider",
          providerEmail: "provider@example.test",
          documentType: "IDENTITY_PROOF",
          originalFilename: "identity-approved.pdf",
          contentType: "application/pdf",
          uploadedAt: "2026-10-24T00:00:00Z",
          sizeBytes: 500,
          status: "APPROVED",
          reviewNote: "",
        },
        {
          id: 22,
          providerId: 7,
          providerName: "Verified Provider",
          providerEmail: "provider@example.test",
          documentType: "SERVICE_ADDRESS_PROOF",
          originalFilename: "address-rejected.pdf",
          contentType: "application/pdf",
          uploadedAt: "2026-10-25T00:00:00Z",
          sizeBytes: 700,
          status: "REJECTED",
          reviewNote: "Please upload a clearer document.",
        },
      ] });
    }
    if (pathname.endsWith("/admin/provider-documents/21/file")) {
      return route.fulfill({
        status: 200,
        contentType: "application/pdf",
        body: "%PDF-1.4\nHomeFix preview test\n",
      });
    }
    if (pathname.endsWith("/admin/providers")) {
      return route.fulfill({ json: [{
        id: 7,
        name: "Verified Provider",
        email: "provider@example.test",
        serviceCategory: "PLUMBING",
        status: "APPROVED",
        documentsReady: true,
      }] });
    }
    if (pathname.endsWith("/admin/dashboard")) {
      return route.fulfill({ json: {} });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/admin");
  await page.getByRole("button", { name: "Providers", exact: true }).click();

  await expect(page.getByText("identity-approved.pdf")).toBeVisible();
  await expect(page.getByText("address-rejected.pdf")).toBeVisible();
  await expect(page.getByText("Please upload a clearer document.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Verification documents" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Approve", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Reject", exact: true })).toHaveCount(0);

  const identityRow = page.locator(".admin-table tbody tr").filter({ hasText: "identity-approved.pdf" });
  await identityRow.getByRole("button", { name: "View" }).click();
  await expect(page.getByRole("dialog", { name: "identity-approved.pdf" })).toBeVisible();
  await expect(page.getByTitle("Preview of identity-approved.pdf")).toBeVisible();
  await page.getByRole("button", { name: "Close document preview" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
});