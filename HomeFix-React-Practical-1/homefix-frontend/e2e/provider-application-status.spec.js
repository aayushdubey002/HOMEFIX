import { expect, test } from "@playwright/test";

const providerUser = {
  id: 74,
  email: "provider@example.test",
  fullName: "Sample Provider",
  role: "PROVIDER",
  phone: "9876543210",
  serviceCategory: "PLUMBING",
  experience: "1-3_YEARS",
  serviceArea: "North District",
};

async function mockProviderApi(page, verificationStatus, documents = []) {
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "test-provider-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, providerUser);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: providerUser } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...providerUser, verificationStatus, online: false } });
    }
    if (pathname.endsWith("/provider/verification-documents")) {
      return route.fulfill({ json: documents });
    }
    if (pathname.endsWith("/provider/dashboard")) {
      return route.fulfill({ json: { completedJobs: 0, acceptedJobs: 0, earnings: 0, reviewCount: 0 } });
    }
    return route.fulfill({ json: [] });
  });
}

test("shows pending approval after required provider documents are submitted", async ({ page }) => {
  await mockProviderApi(page, "PENDING", [
    { id: 1, documentType: "IDENTITY_PROOF", status: "APPROVED", originalFilename: "identity.pdf" },
    { id: 2, documentType: "SERVICE_ADDRESS_PROOF", status: "APPROVED", originalFilename: "address.pdf" },
  ]);

  await page.goto("/provider/application-status");

  await expect(page.getByRole("heading", { name: "Please wait for admin approval" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Check status now" })).toBeVisible();
});

test("shows the approved next step and opens the provider workspace", async ({ page }) => {
  await mockProviderApi(page, "APPROVED");

  await page.goto("/provider/application-status");
  await expect(page.getByRole("heading", { name: "Congratulations, you’re approved!" })).toBeVisible();
  await page.getByRole("button", { name: "Start working" }).click();

  await expect(page).toHaveURL(/\/provider$/);
  await expect(page.getByRole("heading", { name: "Provider overview", level: 1 })).toBeVisible();
  const workspaceNav = page.getByRole("navigation", { name: "Provider workspace pages" });
  await workspaceNav.getByRole("link", { name: /Availability/ }).click();
  await expect(page).toHaveURL(/\/provider\/availability$/);
  await expect(page.getByRole("heading", { name: "Working hours" })).toBeVisible();
  await workspaceNav.getByRole("link", { name: /Profile/ }).click();
  await expect(page).toHaveURL(/\/provider\/profile$/);
  await expect(page.getByRole("heading", { name: "Provider details" })).toBeVisible();
  await workspaceNav.getByRole("link", { name: /Verification/ }).click();
  await expect(page).toHaveURL(/\/provider\/verification$/);
  await expect(page.getByText("Your provider account is verified")).toBeVisible();
  await expect(page.getByRole("button", { name: "Upload document" })).toHaveCount(0);
});

test("does not offer another upload when required documents are already verified", async ({ page }) => {
  await mockProviderApi(page, "PENDING", [
    { id: 11, documentType: "IDENTITY_PROOF", status: "APPROVED", originalFilename: "identity.pdf" },
    { id: 12, documentType: "SERVICE_ADDRESS_PROOF", status: "APPROVED", originalFilename: "address.pdf" },
  ]);

  await page.goto("/provider/verification");

  await expect(page.getByText("Documents verified — review is in progress")).toBeVisible();
  await expect(page.getByRole("button", { name: "Upload document" })).toHaveCount(0);
  await expect(page.getByLabel("Choose file")).toHaveCount(0);
});

test("shows the existing document returned with an upload conflict", async ({ page }) => {
  let uploadRequestBody = "";
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "test-provider-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, providerUser);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: providerUser } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...providerUser, verificationStatus: "PENDING", online: false } });
    }
    if (pathname.endsWith("/provider/dashboard")) {
      return route.fulfill({ json: { completedJobs: 0, acceptedJobs: 0, earnings: 0, reviewCount: 0 } });
    }
    if (pathname.endsWith("/provider/availability")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/provider/verification-documents")
      && route.request().method() === "GET") {
      return route.fulfill({ json: [
        {
          id: 21,
          documentType: "IDENTITY_PROOF",
          status: "PENDING",
          originalFilename: "identity.pdf",
        },
      ] });
    }
    if (pathname.endsWith("/provider/verification-documents")
      && route.request().method() === "POST") {
      uploadRequestBody = route.request().postData() || "";
      return route.fulfill({
        status: 409,
        json: {
          message: "This document is already submitted or approved. Upload a replacement only if it was rejected.",
          document: {
            id: 22,
            documentType: "SERVICE_ADDRESS_PROOF",
            status: "PENDING",
            originalFilename: "address-proof.pdf",
          },
        },
      });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/verification");
  await expect(page.getByLabel("Document type")).toHaveValue("SERVICE_ADDRESS_PROOF");
  await page.getByLabel("Choose file").setInputFiles({
    name: "address-proof.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\nHomeFix test document"),
  });
  await page.getByRole("button", { name: "Upload document" }).click();

  expect(uploadRequestBody).toContain("SERVICE_ADDRESS_PROOF");
  const uploadedAddressDocument = page.locator(".provider-document-item")
    .filter({ hasText: "address-proof.pdf" });
  await expect(uploadedAddressDocument).toContainText("PENDING");
  await expect(uploadedAddressDocument).toContainText("address-proof.pdf");
  await expect(page.getByText(/Proof of service address is already pending/i)).toBeVisible();
  await expect(page.getByLabel("Document type")).not.toHaveValue("SERVICE_ADDRESS_PROOF");
});

test("does not claim a document was refreshed when the server returns no matching record", async ({ page }) => {
  await page.addInitScript((user) => {
    sessionStorage.setItem("homefixAccessToken", "test-provider-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(user));
  }, providerUser);

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: providerUser } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...providerUser, verificationStatus: "PENDING", online: false } });
    }
    if (pathname.endsWith("/provider/dashboard") || pathname.endsWith("/provider/availability")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/provider/verification-documents")
      && route.request().method() === "GET") {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/provider/verification-documents")
      && route.request().method() === "POST") {
      return route.fulfill({
        status: 409,
        json: { message: "This document is already submitted or approved." },
      });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/verification");
  await page.getByLabel("Document type").selectOption("SERVICE_ADDRESS_PROOF");
  await page.getByLabel("Choose file").setInputFiles({
    name: "address-proof.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\nHomeFix test document"),
  });
  await page.getByRole("button", { name: "Upload document" }).click();

  await expect(page.getByText(/server rejected this upload, but did not return a matching document record/i)).toBeVisible();
  const serviceAddressDocument = page.locator(".provider-document-item")
    .filter({ hasText: "Proof of service address" });
  await expect(serviceAddressDocument).toContainText("NOT UPLOADED");
  await expect(serviceAddressDocument).not.toContainText("address-proof.pdf");
});

test("shows document feedback and a reapply action after rejection", async ({ page }) => {
  await mockProviderApi(page, "PENDING", [
    { id: 3, documentType: "IDENTITY_PROOF", status: "REJECTED", originalFilename: "identity.pdf", reviewNote: "Image is blurry." },
    { id: 4, documentType: "SERVICE_ADDRESS_PROOF", status: "APPROVED", originalFilename: "address.pdf" },
  ]);

  await page.goto("/provider/application-status");

  await expect(page.getByRole("heading", { name: "Sorry, your application wasn’t approved" })).toBeVisible();
  await expect(page.getByText("Image is blurry.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Update documents and reapply" })).toBeVisible();
});

test("provider registration signs in and opens the application status page", async ({ page }) => {
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/services/catalog")) {
      return route.fulfill({ json: [{ slug: "plumber", service: "PLUMBING", name: "Plumbing" }] });
    }
    if (pathname.endsWith("/auth/register")) {
      return route.fulfill({ status: 201, json: { message: "Provider registration submitted." } });
    }
    if (pathname.endsWith("/auth/login")) {
      return route.fulfill({
        json: { accessToken: "test-provider-token", user: providerUser },
      });
    }
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: providerUser } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...providerUser, verificationStatus: "PENDING", online: false } });
    }
    if (pathname.endsWith("/provider/verification-documents")) {
      return route.fulfill({ json: [] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/login");
  await page.getByRole("button", { name: "Sign Up", exact: true }).click();
  await page.getByRole("button", { name: /Service Provider/ }).click();
  await page.getByLabel("Full Name").fill(providerUser.fullName);
  await page.getByLabel("Email Address").fill(providerUser.email);
  await page.getByLabel("Password", { exact: true }).fill("password123");
  await page.getByLabel("Phone Number").fill(providerUser.phone);
  await page.getByLabel("Service Category").selectOption("PLUMBING");
  await page.getByLabel("Experience").selectOption("1-3_YEARS");
  await page.getByLabel("Service Area / Address").fill(providerUser.serviceArea);
  await page.getByRole("button", { name: "Submit provider application" }).click();

  await expect(page).toHaveURL(/\/provider\/application-status$/);
  await expect(page.getByRole("heading", { name: "Complete your verification documents" })).toBeVisible();
});