import { expect, test } from "@playwright/test";

const customer = {
  id: 31,
  fullName: "Payment Customer",
  email: "payment-customer@example.test",
  role: "CUSTOMER",
  phone: "9876543210",
};

const provider = {
  id: 41,
  fullName: "Payment Provider",
  email: "payment-provider@example.test",
  role: "PROVIDER",
  phone: "9876543211",
  serviceCategory: "PLUMBING",
  experience: "1-3_YEARS",
  serviceArea: "North District",
};

const admin = {
  id: 51,
  fullName: "Payment Admin",
  email: "payment-admin@example.test",
  role: "ADMIN",
};

const plumber = {
  slug: "plumber",
  name: "Plumber",
  service: "PLUMBING",
  description: "Pipes, taps and leaks",
  startingPrice: 300,
};

async function setSession(page, user) {
  await page.addInitScript((storedUser) => {
    sessionStorage.setItem("homefixAccessToken", "payment-flow-token");
    sessionStorage.setItem("homefixUser", JSON.stringify(storedUser));
  }, user);
}

test("customer can select gateway-enabled UPI and pays only after service completion", async ({ page }) => {
  await setSession(page, customer);
  let createdBooking;
  let savedBooking;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/services/catalog")) {
      return route.fulfill({ json: [plumber] });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: true, upiEnabled: true, keyId: "rzp_test_public" } });
    }
    if (pathname.endsWith("/location/config")) {
      return route.fulfill({ json: { simulationEnabled: false } });
    }
    if (pathname.endsWith("/service-requests") && route.request().method() === "POST") {
      createdBooking = route.request().postDataJSON();
      savedBooking = {
        id: 91,
        bookingId: "HF-UPI-91",
        service: "PLUMBING",
        preferredDate: createdBooking.preferredDate,
        price: 300,
        status: "PENDING",
        completionPin: "123456",
        paymentMethod: "UPI",
        paymentStatus: "UNPAID",
      };
      return route.fulfill({
        status: 201,
        json: savedBooking,
      });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: savedBooking ? [savedBooking] : [] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer");
  await page.getByRole("link", { name: "Book Plumber" }).click();
  await expect(page.getByText("Step 1 of 6")).toBeVisible();
  await page.getByRole("button", { name: /Continue/ }).click();
  await expect(page.getByText("Step 2 of 6")).toBeVisible();
  await page.getByLabel("Describe the problem").fill("Water is leaking under the kitchen sink");
  const problemPhoto = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lqAAAAAASUVORK5CYII=",
    "base64",
  );
  await page.getByLabel("Add a photo").setInputFiles({
    name: "sink-leak.png",
    mimeType: "image/png",
    buffer: problemPhoto,
  });
  await expect(page.getByAltText("Preview of the problem photo")).toBeVisible();
  await page.getByRole("button", { name: /Continue/ }).click();
  await expect(page.getByText("Step 3 of 6")).toBeVisible();
  await page.getByLabel("Service address").fill("12 Main Street");
  await page.getByRole("button", { name: /Continue/ }).click();
  await expect(page.getByText("Step 4 of 6")).toBeVisible();
  await page.getByLabel("Preferred date").fill("2026-12-02");
  await page.getByRole("button", { name: /Continue/ }).click();
  await expect(page.getByText("Step 5 of 6")).toBeVisible();
  await page.getByRole("radio", { name: /UPI/ }).check();
  await page.getByRole("button", { name: /Continue/ }).click();
  await expect(page.getByText("Step 6 of 6")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Review your booking" })).toBeVisible();
  await expect(page.getByText("12 Main Street")).toBeVisible();
  await expect(page.getByText("Water is leaking under the kitchen sink", { exact: true }))
    .toBeVisible();
  await expect(page.getByText("sink-leak.png")).toBeVisible();
  await page.getByRole("button", { name: "Confirm Booking" }).click();

  await expect(page.getByRole("heading", { name: "Booking request submitted" })).toBeVisible();
  await expect(page.getByText("123456")).toBeVisible();
  await expect(page.getByText("Share this code with your provider when they arrive.")).toBeVisible();
  await expect(page.getByText("UPI checkout becomes available after your provider completes the service."))
    .toBeVisible();
  await expect(page.locator(".upi-payment-qr")).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Track booking status" }))
    .toHaveAttribute("href", "/customer/bookings/91");
  await page.getByRole("link", { name: "Track booking status" }).click();
  await expect(page).toHaveURL(/\/customer\/bookings\/91$/);
  await expect(page.getByText("Your request is waiting for an eligible provider to accept it."))
    .toBeVisible();
  expect(createdBooking.paymentMethod).toBe("UPI");
  expect(createdBooking.notes).toBe("Water is leaking under the kitchen sink");
  expect(createdBooking.problemImageBase64).toBe(problemPhoto.toString("base64"));
});

test("assigned provider can view the customer's problem description and photo", async ({ page }) => {
  await setSession(page, provider);
  const problemPhoto = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lqAAAAAASUVORK5CYII=",
    "base64",
  );
  const assignedBooking = {
    id: 95,
    bookingId: "HF-PROBLEM-95",
    service: "PLUMBING",
    customerName: "Payment Customer",
    phone: "9876543210",
    address: "12 Main Street",
    preferredDate: "2026-12-02",
    price: 300,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "IN_PROGRESS",
    paymentMethod: "CASH",
    paymentStatus: "UNPAID",
    notes: "Water is leaking under the kitchen sink",
    problemImageAvailable: true,
  };
  const bookingMessages = [];

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...provider, verificationStatus: "APPROVED", online: true } });
    }
    if (pathname.endsWith("/provider/dashboard")) {
      return route.fulfill({ json: { completedJobs: 0, acceptedJobs: 1, earnings: 0, reviewCount: 0 } });
    }
    if (pathname.endsWith("/provider/availability")
        || pathname.endsWith("/provider/verification-documents")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: false, upiEnabled: false } });
    }
    if (pathname.endsWith("/service-requests/95/problem-image")) {
      return route.fulfill({
        status: 200,
        contentType: "image/png",
        body: problemPhoto,
      });
    }
    if (pathname.endsWith("/service-requests/95/messages")) {
      if (route.request().method() === "POST") {
        const request = route.request().postDataJSON();
        bookingMessages.push({
          id: 501,
          senderId: provider.id,
          senderName: provider.fullName,
          senderRole: provider.role,
          message: request.message,
          sentAt: new Date().toISOString(),
        });
        return route.fulfill({ status: 201, json: bookingMessages.at(-1) });
      }
      return route.fulfill({ json: bookingMessages });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [assignedBooking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/requests");
  await expect(page.getByText("Customer-described problem")).toBeVisible();
  await expect(page.getByText("Water is leaking under the kitchen sink", { exact: true }))
    .toBeVisible();
  await expect(page.getByRole("img", { name: "Customer photo of the PLUMBING problem" }))
    .toBeVisible();
  await expect(page.getByRole("link", { name: "View full photo" })).toBeVisible();
  await page.getByRole("link", { name: "Open job workspace" }).click();
  await expect(page.getByRole("heading", { name: "PLUMBING" })).toBeVisible();
  await expect(page.getByRole("img", { name: "Customer’s service problem" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Open customer photo" })).toBeVisible();
  await page.getByRole("link", { name: "Open customer chat" }).click();
  await expect(page.getByRole("heading", { name: "Payment Customer" })).toBeVisible();
  await page.getByLabel("Message Payment Customer").fill("I am on my way.");
  await page.getByRole("button", { name: "Send message" }).click();
  await expect(page.getByText("I am on my way.", { exact: true })).toBeVisible();
  expect(bookingMessages).toHaveLength(1);
});

test("customer opens a dedicated detail page from the booking summary", async ({ page }) => {
  await setSession(page, customer);
  const booking = {
    id: 114,
    bookingId: "HF-CUSTOMER-114",
    service: "PLUMBING",
    customerName: customer.fullName,
    preferredDate: "2026-12-05",
    preferredAt: "2026-12-05T10:30:00",
    address: "12 Main Street",
    price: 300,
    status: "ACCEPTED",
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
  };
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings");
  await expect(page.getByText("HF-CUSTOMER-114", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "PLUMBING" })).toBeVisible();
  await page.getByRole("link", { name: /View booking details/ }).click();
  await expect(page).toHaveURL(/\/customer\/bookings\/114$/);
  await expect(page.getByText("12 Main Street")).toBeVisible();
  await expect(page.getByText(provider.fullName, { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: /Back to my bookings/ })).toBeVisible();
});

test("customer can call and chat with the provider assigned to their booking", async ({ page }) => {
  await setSession(page, customer);
  const assignedBooking = {
    id: 110,
    bookingId: "HF-CHAT-110",
    service: "PLUMBING",
    customerName: customer.fullName,
    preferredDate: "2026-12-05",
    price: 300,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    providerPhone: "9876543211",
    providerExperience: "1-3_YEARS",
    status: "ACCEPTED",
    paymentMethod: "CASH",
    paymentStatus: "UNPAID",
  };
  const bookingMessages = [];

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/service-requests/110/messages")) {
      if (route.request().method() === "POST") {
        const request = route.request().postDataJSON();
        bookingMessages.push({
          id: 601,
          senderId: customer.id,
          senderName: customer.fullName,
          senderRole: customer.role,
          message: request.message,
          sentAt: new Date().toISOString(),
        });
        return route.fulfill({ status: 201, json: bookingMessages.at(-1) });
      }
      return route.fulfill({ json: bookingMessages });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [assignedBooking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/110");
  await expect(page.getByRole("link", { name: "Call provider · 9876543211" }))
    .toHaveAttribute("href", "tel:9876543211");
  await page.getByRole("link", { name: "Open chat with Payment Provider" }).click();
  await expect(page.getByRole("heading", { name: "Payment Provider" })).toBeVisible();
  await page.getByLabel("Message Payment Provider").fill("Could you come after 10 AM?");
  await page.getByRole("button", { name: "Send message" }).click();
  await expect(page.getByText("Could you come after 10 AM?", { exact: true })).toBeVisible();
  await expect(page.getByRole("status")).toHaveText("Message sent.");
  expect(bookingMessages).toHaveLength(1);
});

test("customer chat keeps an unsent message and explains when delivery fails", async ({ page }) => {
  await setSession(page, customer);
  const assignedBooking = {
    id: 112,
    bookingId: "HF-CHAT-112",
    service: "PLUMBING",
    customerName: customer.fullName,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "ACCEPTED",
  };
  let messagePolls = 0;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/service-requests/112/messages")) {
      if (route.request().method() === "POST") {
        return route.fulfill({
          status: 503,
          json: { message: "Chat messages are temporarily unavailable." },
        });
      }
      messagePolls += 1;
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [assignedBooking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/112");
  await page.getByRole("link", { name: "Open chat with Payment Provider" }).click();
  await page.getByLabel("Message Payment Provider").fill("Please call before arriving.");
  await page.getByRole("button", { name: "Send message" }).click();

  await expect(page.getByRole("alert")).toContainText("Sending message failed (HTTP 503)");
  await expect(page.getByLabel("Message Payment Provider"))
    .toHaveValue("Please call before arriving.");
  await expect.poll(() => messagePolls, { timeout: 5000 }).toBeGreaterThan(1);
  await expect(page.getByRole("alert")).toContainText("Sending message failed (HTTP 503)");
});

test("completed booking hides chat and a stale chat link reports the conversation is closed", async ({ page }) => {
  await setSession(page, customer);
  const completedBooking = {
    id: 111,
    bookingId: "HF-CLOSED-111",
    service: "PLUMBING",
    customerName: customer.fullName,
    preferredDate: "2026-12-05",
    price: 300,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    providerPhone: "9876543211",
    status: "COMPLETED",
    paymentMethod: "CASH",
    paymentStatus: "PAID",
  };
  let messageEndpointCalled = false;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/service-requests/111/messages")) {
      messageEndpointCalled = true;
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [completedBooking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/111");
  await expect(page.locator(".customer-booking-action > strong"))
    .toContainText("HF-CLOSED-111 · PLUMBING");
  await expect(page.getByRole("link", { name: /Open chat with/ })).toHaveCount(0);

  await page.goto("/customer/bookings/111/chat");
  await expect(page.getByRole("heading", { name: "This booking chat is closed" })).toBeVisible();
  expect(messageEndpointCalled).toBe(false);
});

test("customer can cancel an arrived booking before the provider verifies the PIN", async ({ page }) => {
  await setSession(page, customer);
  const booking = {
    id: 113,
    bookingId: "HF-CUSTOMER-CANCEL-113",
    service: "PLUMBING",
    customerName: customer.fullName,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "ARRIVED",
    completionPin: "123456",
    price: 300,
  };
  let cancelRequests = 0;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/service-requests/113/cancel")) {
      cancelRequests += 1;
      booking.status = "CANCELLED";
      return route.fulfill({ json: booking });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/113");
  await page.getByRole("button", { name: "Cancel before job starts" }).click();
  await expect(page.getByRole("group", { name: "Confirm booking cancellation" })).toContainText(
    "before the provider starts work",
  );
  await page.getByRole("button", { name: "Yes, cancel booking" }).click();
  await expect.poll(() => cancelRequests).toBe(1);
});

test("customer sees the backend reason when a booking cancellation is rejected", async ({ page }) => {
  await setSession(page, customer);
  const booking = {
    id: 115,
    bookingId: "HF-CUSTOMER-CANCEL-115",
    service: "PLUMBING",
    customerName: customer.fullName,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "ARRIVED",
    price: 300,
  };
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/service-requests/115/cancel")) {
      return route.fulfill({
        status: 409,
        json: { message: "The job-start PIN has already been verified." },
      });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/115");
  await page.getByRole("button", { name: "Cancel before job starts" }).click();
  await page.getByRole("button", { name: "Yes, cancel booking" }).click();
  await expect(page.getByRole("alert"))
    .toContainText("The job-start PIN has already been verified.");
});

test("assigned provider can cancel an arrived job before PIN verification", async ({ page }) => {
  await setSession(page, provider);
  const booking = {
    id: 114,
    bookingId: "HF-PROVIDER-CANCEL-114",
    service: "PLUMBING",
    customerName: customer.fullName,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "ARRIVED",
    problemImageAvailable: false,
    price: 300,
  };
  let cancelRequests = 0;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...provider, verificationStatus: "APPROVED", online: true } });
    }
    if (pathname.endsWith("/provider/dashboard")) {
      return route.fulfill({ json: { completedJobs: 0, acceptedJobs: 1, earnings: 0, reviewCount: 0 } });
    }
    if (pathname.endsWith("/provider/availability")
        || pathname.endsWith("/provider/verification-documents")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: false, upiEnabled: false } });
    }
    if (pathname.endsWith("/service-requests/114/cancel")) {
      cancelRequests += 1;
      booking.status = "CANCELLED";
      return route.fulfill({ json: booking });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/bookings/114");
  await page.getByRole("button", { name: "Cancel before job starts" }).click();
  await page.getByRole("button", { name: "Yes, cancel booking" }).click();
  await expect.poll(() => cancelRequests).toBe(1);
  await expect(page).toHaveURL(/\/provider\/requests$/);
});

test("provider gets actionable guidance when job progress is denied", async ({ page }) => {
  await setSession(page, provider);
  const booking = {
    id: 117,
    bookingId: "HF-PROVIDER-STATUS-117",
    service: "PLUMBING",
    customerName: customer.fullName,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "ACCEPTED",
    problemImageAvailable: false,
    price: 300,
  };
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: false, upiEnabled: false } });
    }
    if (pathname.endsWith("/service-requests/117/status")) {
      return route.fulfill({ status: 403, json: {} });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/bookings/117");
  await page.getByRole("button", { name: "Confirm this service request" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "You are not authorized to update this booking.",
  );
  await expect(page.getByRole("alert")).not.toContainText("Request failed with status code 403");
});

test("provider job page loads the customer photo when image availability metadata is missing", async ({ page }) => {
  await setSession(page, provider);
  const problemPhoto = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lqAAAAAASUVORK5CYII=",
    "base64",
  );
  const booking = {
    id: 121,
    bookingId: "HF-PHOTO-METADATA-121",
    service: "PLUMBING",
    customerName: "Photo Customer",
    provider: { id: provider.id, fullName: provider.fullName },
    providerName: provider.fullName,
    status: "ACCEPTED",
    price: 300,
  };
  let photoRequestCount = 0;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: false, upiEnabled: false } });
    }
    if (pathname.endsWith("/service-requests/121/problem-image")) {
      photoRequestCount += 1;
      return route.fulfill({ status: 200, contentType: "image/png", body: problemPhoto });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/bookings/121");
  await expect(page.getByRole("img", { name: "Customer’s service problem" })).toBeVisible();
});

test("provider must load and review a pending customer's photo before accepting", async ({ page }) => {
  await setSession(page, provider);
  const problemPhoto = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lqAAAAAASUVORK5CYII=",
    "base64",
  );
  const pendingBooking = {
    id: 96,
    bookingId: "HF-PENDING-PHOTO-96",
    service: "PLUMBING",
    customerName: "Photo Customer",
    phone: "9876543210",
    address: "12 Main Street",
    preferredDate: "2026-12-02",
    price: 300,
    providerName: "Provider will be assigned",
    status: "PENDING",
    notes: "A pipe is leaking.",
  };
  let releasePhoto;
  const photoResponse = new Promise((resolve) => {
    releasePhoto = resolve;
  });
  let acceptRequests = 0;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...provider, verificationStatus: "APPROVED", online: true } });
    }
    if (pathname.endsWith("/provider/dashboard")) {
      return route.fulfill({ json: { completedJobs: 0, acceptedJobs: 0, earnings: 0, reviewCount: 0 } });
    }
    if (pathname.endsWith("/provider/availability")
        || pathname.endsWith("/provider/verification-documents")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: false, upiEnabled: false } });
    }
    if (pathname.endsWith("/service-requests/96/problem-image")) {
      await photoResponse;
      return route.fulfill({ status: 200, contentType: "image/png", body: problemPhoto });
    }
    if (pathname.endsWith("/service-requests/96/accept")) {
      acceptRequests += 1;
      return route.fulfill({
        status: 200,
        json: {
          ...pendingBooking,
          status: "ACCEPTED",
          provider: { id: provider.id, fullName: provider.fullName },
        },
      });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [pendingBooking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/requests");
  await expect(page.getByText("HF-PENDING-PHOTO-96")).toBeVisible();
  await page.getByRole("link", { name: "Open job workspace" }).click();
  const acceptButton = page.getByRole("button", { name: "Accept this request" });
  await expect(page.getByText("Loading customer photo…")).toBeVisible();
  await expect(acceptButton).toBeDisabled();
  releasePhoto();
  await expect(page.getByRole("img", { name: "Customer’s service problem" }))
    .toBeVisible();
  await page.getByRole("checkbox", { name: /I have reviewed the customer’s problem photo/ }).check();
  await expect(acceptButton).toBeEnabled();
  await acceptButton.click();
  await expect.poll(() => acceptRequests).toBe(1);
});

test("booking progress wraps into readable rows without overlapping labels", async ({ page }) => {
  await setSession(page, customer);
  const completedBooking = {
    id: 122,
    bookingId: "HF-TIMELINE-122",
    service: "PLUMBING",
    customerName: customer.fullName,
    preferredDate: "2026-12-05",
    price: 300,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "COMPLETED",
    paymentMethod: "CASH",
    paymentStatus: "PAID",
  };
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [completedBooking] });
    }
    return route.fulfill({ json: [] });
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/customer/bookings/122");
  const timeline = page.getByRole("region", { name: "Booking progress" }).first();
  await expect(timeline).toBeVisible();
  await expect(timeline.getByText("Your service is complete.")).toBeVisible();
  const layout = await timeline.locator(".booking-status-steps").evaluate((element) => {
    const nodes = [...element.querySelectorAll(".booking-status-step")];
    return nodes.map((node) => {
      const rect = node.getBoundingClientRect();
      const label = node.querySelector(":scope > span:last-child").getBoundingClientRect();
      return {
        row: Math.round(rect.top),
        labelLeft: label.left,
        labelRight: label.right,
      };
    });
  });
  expect(layout).toHaveLength(7);
  expect(new Set(layout.map((step) => step.row)).size).toBeGreaterThan(1);
  for (const step of layout) {
    expect(step.labelRight - step.labelLeft).toBeGreaterThan(0);
  }
});

test("provider gets separate PIN and payment screens before completing assigned work", async ({ page }) => {
  await setSession(page, provider);
  const booking = {
    id: 120,
    bookingId: "HF-WORKSPACE-120",
    service: "PLUMBING",
    customerName: customer.fullName,
    phone: customer.phone,
    address: "12 Main Street",
    preferredDate: "2026-12-05",
    price: 300,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "ARRIVED",
    paymentMethod: "CASH",
    paymentStatus: "UNPAID",
    completionPin: "654321",
  };
  const updates = [];

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/service-requests/120/status") && route.request().method() === "PUT") {
      const request = route.request().postDataJSON();
      updates.push(request);
      booking.status = request.status;
      booking.paymentStatus = request.status === "COMPLETED" && request.paymentMethod === "CASH"
        ? "PAID"
        : "UNPAID";
      booking.paymentMethod = request.paymentMethod || booking.paymentMethod;
      return route.fulfill({ json: booking });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: false, upiEnabled: false } });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/bookings/120");
  await page.getByRole("button", { name: "Open PIN verification" }).click();
  await expect(page.getByRole("heading", { name: "Verify job-start PIN" })).toBeVisible();
  await page.getByLabel("Customer job-start PIN").fill("654321");
  await page.getByRole("button", { name: "Verify PIN & start work" }).click();
  await expect(page.getByRole("button", { name: "Open payment recording" })).toBeVisible();
  expect(updates[0].status).toBe("IN_PROGRESS");
  expect(updates[0].completionPin).toBe("654321");

  await page.getByRole("button", { name: "Open payment recording" }).click();
  await expect(page.getByRole("heading", { name: "Confirm charges and payment" })).toBeVisible();
  await page.getByLabel("Cash received (₹)").fill("350");
  await page.getByRole("button", { name: "Complete service" }).click();
  await expect(page.getByText("Service completed", { exact: true })).toBeVisible();
  expect(updates[1].status).toBe("COMPLETED");
  expect(updates[1].paymentMethod).toBe("CASH");
  expect(updates[1].cashReceivedAmount).toBe(350);
});

test("provider sends an itemized extra charge for approval before service completion", async ({ page }) => {
  await setSession(page, provider);
  const booking = {
    id: 122,
    bookingId: "HF-EXTRA-122",
    service: "PLUMBING",
    customerName: customer.fullName,
    phone: customer.phone,
    address: "12 Main Street",
    preferredDate: "2026-12-05",
    price: 300,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "IN_PROGRESS",
    paymentMethod: "CASH",
    paymentStatus: "UNPAID",
  };
  let proposalBody;
  const statusUpdates = [];

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/service-requests/122/price-proposals")
      && route.request().method() === "POST") {
      proposalBody = route.request().postDataJSON();
      booking.pendingPriceProposalId = 23;
      booking.proposedPrice = 360;
      return route.fulfill({ status: 201, json: { id: 23, ...proposalBody, status: "PENDING_APPROVAL" } });
    }
    if (pathname.endsWith("/service-requests/122/status")
      && route.request().method() === "PUT") {
      statusUpdates.push(route.request().postDataJSON());
      return route.fulfill({ json: booking });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({
        json: {
          gatewayEnabled: false,
          upiEnabled: false,
          maximumProviderAdditionalCharge: 1000,
        },
      });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/bookings/122/payment");
  await page.getByLabel("Extra charge").selectOption("ADD");
  await expect(page.getByText("Maximum extra charge: ₹1000.00")).toBeVisible();
  await expect(page.getByLabel("Payment method")).toHaveCount(0);
  await expect(page.getByLabel("Cash received (₹)")).toHaveCount(0);
  await page.getByLabel("Charge type").fill("Tools");
  await page.getByLabel("Additional amount (₹)").fill("1001");
  await expect(page.getByRole("alert")).toContainText("above the allowed maximum");
  await expect(page.getByRole("button", { name: "Send charge for customer approval" })).toBeDisabled();
  await page.getByLabel("Additional amount (₹)").fill("200");
  await page.getByLabel("Additional amount (₹)").dispatchEvent("wheel");
  await expect(page.getByLabel("Additional amount (₹)")).toHaveValue("200");
  await page.getByLabel("Why is this charge needed?").fill("Replacement wrench needed for the repair.");
  await page.getByRole("button", { name: "Send charge for customer approval" }).click();

  await expect(page.getByText(/extra charge is awaiting the customer’s decision/i)).toBeVisible();
  expect(proposalBody).toEqual({
    additionalAmount: 200,
    explanation: "Tools: Replacement wrench needed for the repair.",
  });
  expect(statusUpdates).toHaveLength(0);
  await expect(page.getByRole("button", { name: "Waiting for customer decision" })).toBeDisabled();
});

test("provider cannot mark a UPI payment paid by entering a transaction reference", async ({ page }) => {
  await setSession(page, provider);
  let confirmedBody;
  let completed = false;
  const booking = {
    id: 92,
    bookingId: "HF-UPI-92",
    service: "PLUMBING",
    customerName: "Payment Customer",
    phone: "9876543210",
    address: "12 Main Street",
    preferredDate: "2026-12-02",
    price: 300,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: completed ? "COMPLETED" : "IN_PROGRESS",
    paymentMethod: "UPI",
    paymentStatus: completed ? "PAID" : "UNPAID",
    merchantUpiId: "homefix@bank",
  };

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...provider, verificationStatus: "APPROVED", online: true } });
    }
    if (pathname.endsWith("/provider/dashboard")) {
      return route.fulfill({ json: { completedJobs: 0, acceptedJobs: 1, earnings: 0, reviewCount: 0 } });
    }
    if (pathname.endsWith("/provider/availability")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/provider/verification-documents")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: true, upiEnabled: true } });
    }
    if (pathname.endsWith("/service-requests/92/status") && route.request().method() === "PUT") {
      confirmedBody = route.request().postDataJSON();
      completed = true;
      return route.fulfill({ json: { ...booking, status: "COMPLETED", paymentStatus: "UNPAID" } });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [{ ...booking, status: completed ? "COMPLETED" : "IN_PROGRESS", paymentStatus: completed ? "PAID" : "UNPAID" }] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/bookings/92");
  await page.getByRole("button", { name: "Open payment recording" }).click();
  await expect(page.getByText(/customer completes UPI through HomeFix checkout/)).toBeVisible();
  await page.getByRole("button", { name: "Complete service" }).click();

  await expect.poll(() => confirmedBody?.status).toBe("COMPLETED");
  expect(confirmedBody.paymentReference).toBe("");
  expect(confirmedBody.paymentMethod).toBe("UPI");
  expect(confirmedBody.cashReceivedAmount).toBeNull();
  expect(confirmedBody.status).toBe("COMPLETED");
});

test("provider can switch to cash and record the received amount", async ({ page }) => {
  await setSession(page, provider);
  let completionBody;
  const booking = {
    id: 94,
    bookingId: "HF-CASH-94",
    service: "PLUMBING",
    customerName: "Payment Customer",
    phone: "9876543210",
    address: "12 Main Street",
    preferredDate: "2026-12-02",
    price: 300,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "IN_PROGRESS",
    paymentMethod: "UPI",
    paymentStatus: "UNPAID",
  };

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...provider, verificationStatus: "APPROVED", online: true } });
    }
    if (pathname.endsWith("/provider/dashboard")) {
      return route.fulfill({ json: { completedJobs: 0, acceptedJobs: 1, earnings: 0, reviewCount: 0 } });
    }
    if (pathname.endsWith("/provider/availability")
        || pathname.endsWith("/provider/verification-documents")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: true, upiEnabled: true } });
    }
    if (pathname.endsWith("/service-requests/94/status") && route.request().method() === "PUT") {
      completionBody = route.request().postDataJSON();
      return route.fulfill({ json: { ...booking, status: "COMPLETED", paymentMethod: "CASH", paymentStatus: "PAID", paymentCollectedAmount: 350 } });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/bookings/94");
  await page.getByRole("button", { name: "Open payment recording" }).click();
  await page.getByLabel("Payment method").selectOption("CASH");
  await page.getByLabel(/Cash received/).fill("350.00");
  await page.getByRole("button", { name: "Complete service" }).click();

  await expect.poll(() => completionBody?.paymentMethod).toBe("CASH");
  expect(Number(completionBody.cashReceivedAmount)).toBe(350);
  expect(completionBody.status).toBe("COMPLETED");
});

test("provider must enter the customer PIN before starting an on-the-way job", async ({ page }) => {
  await setSession(page, provider);
  let startRequest;
  const booking = {
    id: 93,
    bookingId: "HF-PIN-93",
    service: "PLUMBING",
    customerName: "Payment Customer",
    phone: "9876543210",
    address: "12 Main Street",
    preferredDate: "2026-12-02",
    price: 300,
    providerName: provider.fullName,
    provider: { id: provider.id, fullName: provider.fullName },
    status: "ON_THE_WAY",
    paymentMethod: "CASH",
    paymentStatus: "UNPAID",
  };

  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: provider } });
    }
    if (pathname.endsWith("/provider/profile")) {
      return route.fulfill({ json: { ...provider, verificationStatus: "APPROVED", online: true } });
    }
    if (pathname.endsWith("/provider/dashboard")) {
      return route.fulfill({ json: { completedJobs: 0, acceptedJobs: 1, earnings: 0, reviewCount: 0 } });
    }
    if (pathname.endsWith("/provider/availability")
        || pathname.endsWith("/provider/verification-documents")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/service-requests/93/status") && route.request().method() === "PUT") {
      const request = route.request().postDataJSON();
      if (request.status === "IN_PROGRESS") startRequest = request;
      booking.status = request.status;
      return route.fulfill({ json: booking });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/provider/bookings/93");
  await page.getByRole("button", { name: "I have arrived" }).click();
  await page.getByRole("button", { name: "Open PIN verification" }).click();
  await expect(page.getByLabel("Customer job-start PIN")).toBeVisible();
  await expect(page.getByRole("button", { name: "Verify PIN & start work" })).toBeDisabled();
  await page.getByLabel("Customer job-start PIN").fill("385204");
  await page.getByRole("button", { name: "Verify PIN & start work" }).click();

  await expect.poll(() => startRequest?.completionPin).toBe("385204");
  expect(startRequest.status).toBe("IN_PROGRESS");
});

test("customer can replace the booking PIN before the provider starts work", async ({ page }) => {
  await setSession(page, customer);
  let currentPin = "123456";
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/service-requests/94/completion-pin")
        && route.request().method() === "POST") {
      currentPin = "654321";
      return route.fulfill({ json: { completionPin: currentPin, message: "A new job-start PIN was generated." } });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [{
        id: 94,
        bookingId: "HF-PIN-94",
        service: "PLUMBING",
        status: "ON_THE_WAY",
        price: 300,
        completionPin: currentPin,
        paymentMethod: "CASH",
        paymentStatus: "UNPAID",
      }] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/94");
  await expect(page.getByText(/Job start PIN: 123456/)).toBeVisible();
  await page.getByRole("button", { name: "Generate a new PIN" }).click();

  await expect(page.getByText(/Job start PIN: 654321/)).toBeVisible();
});

test("admin sees gateway configuration status without access to payment secrets", async ({ page }) => {
  await setSession(page, admin);
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: admin } });
    }
    if (pathname.endsWith("/payments/config")) {
      return route.fulfill({ json: { gatewayEnabled: false, upiEnabled: false, keyId: "" } });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/admin");
  await page.getByRole("button", { name: "Payments", exact: true }).click();
  await expect(page.getByText("Gateway not configured")).toBeVisible();
  await expect(page.getByText(/RAZORPAY_KEY_SECRET/)).toBeVisible();
  await expect(page.getByLabel("UPI ID")).toHaveCount(0);
  await expect(page.getByPlaceholder("Reconciliation reference")).toHaveCount(0);
  await expect(page.locator("select[id^='payment-status-']")).toHaveCount(0);
});

test("customer can download the completed booking invoice PDF", async ({ page }) => {
  await setSession(page, customer);
  let invoiceRequested = false;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/notifications")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/service-requests/105/invoice/pdf")) {
      invoiceRequested = true;
      return route.fulfill({
        contentType: "application/pdf",
        body: "%PDF-1.4\nHomeFix invoice fixture\n",
      });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({
        json: [{
          id: 105,
          bookingId: "HF-INVOICE-105",
          service: "PLUMBING",
          customerName: customer.fullName,
          preferredDate: "2026-12-02",
          address: "12 Main Street",
          price: 300,
          status: "COMPLETED",
          providerName: "Payment Provider",
          paymentMethod: "CASH",
          paymentStatus: "PAID",
        }],
      });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/105");
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download PDF invoice" }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe("homefix-105.pdf");
  expect(invoiceRequested).toBe(true);
});

test("customer invoice shows approved extra charge type and amount", async ({ page }) => {
  await setSession(page, customer);
  const booking = {
    id: 123,
    bookingId: "HF-INVOICE-123",
    service: "PLUMBING",
    customerName: customer.fullName,
    preferredDate: "2026-12-02",
    address: "12 Main Street",
    price: 360,
    status: "COMPLETED",
    providerName: "Payment Provider",
  };
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/notifications")) {
      return route.fulfill({ json: [] });
    }
    if (pathname.endsWith("/service-requests/123/invoice")) {
      return route.fulfill({
        json: {
          invoiceNumber: "HF-INVOICE-123",
          subtotal: 360,
          discount: 0,
          tax: 0,
          total: 360,
          paymentStatus: "PAID",
          approvedAdditionalCharges: 60,
          approvedChargeItems: [{
            id: 23,
            amount: 60,
            explanation: "Tools: Replacement wrench needed for the repair.",
          }],
        },
      });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/123");
  await page.getByRole("button", { name: "View invoice" }).click();
  await expect(page.getByText("Approved additional charges ₹60.00")).toBeVisible();
  await expect(page.getByText("Tools: Replacement wrench needed for the repair.: ₹60.00"))
    .toBeVisible();
});

test("customer sees the saved price breakdown and payment refund history", async ({ page }) => {
  await setSession(page, customer);
  const booking = {
    id: 93,
    bookingId: "HF-PAYMENT-HISTORY-93",
    service: "PLUMBING",
    status: "COMPLETED",
    price: "297.00",
    serviceSubtotal: "300.00",
    discountAmount: "30.00",
    taxAmount: "27.00",
    paymentMethod: "UPI",
    paymentStatus: "PARTIALLY_REFUNDED",
  };
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/auth/me")) {
      return route.fulfill({ json: { user: customer } });
    }
    if (pathname.endsWith("/service-requests")) {
      return route.fulfill({ json: [booking] });
    }
    if (pathname.endsWith("/payments/razorpay/bookings/93/history")) {
      return route.fulfill({
        json: [{
          transactionId: 7,
          amount: "297.00",
          status: "PARTIALLY_REFUNDED",
          gatewayPaymentId: "pay_test_history",
          createdAt: "2026-10-28T12:00:00Z",
          refunds: [{
            refundId: 8,
            amount: "25.00",
            status: "PROCESSED",
            gatewayRefundId: "rfnd_test_history",
            reason: "Partial service adjustment",
          }],
        }],
      });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/customer/bookings/93");
  await expect(page.getByText("₹300.00")).toBeVisible();
  await expect(page.getByText("₹27.00")).toBeVisible();
  await page.getByRole("button", { name: "View payment and refund history" }).click();
  await expect(page.getByText("Payment PARTIALLY_REFUNDED")).toBeVisible();
  await expect(page.getByText(/Refund ₹25\.00 · PROCESSED/)).toBeVisible();
  await expect(page.getByText(/Partial service adjustment/)).toBeVisible();
});