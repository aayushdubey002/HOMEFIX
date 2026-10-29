import axios from "axios";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8083/api";

const AUTH_TOKEN_KEY = "homefixAccessToken";
const multipartRequestConfig = { headers: { "Content-Type": undefined } };

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(AUTH_TOKEN_KEY);

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

export const registerUser = (data) =>
  api.post("/auth/register", data, { timeout: 60000 });

export const loginUser = (data) =>
  api.post("/auth/login", data);

export const fetchCurrentUser = () =>
  api.get("/auth/me");

export const fetchStats = () =>
  api.get("/dashboard/stats");

export const fetchServiceCatalog = () =>
  api.get("/services/catalog");

export const fetchPaymentConfiguration = () =>
  api.get("/payments/config");

export const createRazorpayOrder = (bookingId, idempotencyKey) =>
  api.post(`/payments/razorpay/orders/${bookingId}`, null, {
    headers: { "Idempotency-Key": idempotencyKey },
    timeout: 20000,
  });

export const verifyRazorpayPayment = (transactionId, paymentDetails) =>
  api.post(`/payments/razorpay/transactions/${transactionId}/verify`, paymentDetails, {
    timeout: 20000,
  });

export const cancelRazorpayCheckout = (transactionId) =>
  api.post(`/payments/razorpay/transactions/${transactionId}/cancel`);

export const fetchBookingPaymentHistory = (bookingId) =>
  api.get(`/payments/razorpay/bookings/${bookingId}/history`);

export const requestAdminRefund = (transactionId, data, idempotencyKey) =>
  api.post(`/admin/payments/transactions/${transactionId}/refunds`, data, {
    headers: { "Idempotency-Key": idempotencyKey },
  });

export const fetchLocationConfig = () =>
  api.get("/location/config");

export const fetchBookings = () =>
  api.get("/service-requests", { timeout: 15000 });

export const postServiceRequest = (data) =>
  api.post("/service-requests", data, { timeout: 20000 });

export const fetchBookingProblemImage = (bookingId) =>
  api.get(`/service-requests/${bookingId}/problem-image`, { responseType: "blob" });

export const fetchBookingMessages = (bookingId) =>
  api.get(`/service-requests/${bookingId}/messages`, { timeout: 15000 });

export const sendBookingMessage = (bookingId, message) =>
  api.post(`/service-requests/${bookingId}/messages`, { message }, { timeout: 15000 });

export const acceptServiceRequest = (bookingId) =>
  api.put(`/service-requests/${bookingId}/accept`, null);

export const rejectServiceRequest = (bookingId) =>
  api.put(`/service-requests/${bookingId}/reject`, null);

export const updateServiceRequestStatus = (
  bookingId, status, note = "", paymentReference = "", completionPin = "",
  paymentMethod = "", cashReceivedAmount = null,
) => api.put(`/service-requests/${bookingId}/status`, {
  status,
  note,
  paymentReference,
  completionPin,
  paymentMethod: paymentMethod || null,
  cashReceivedAmount,
});

export const cancelServiceRequest = (bookingId) =>
  api.put(`/service-requests/${bookingId}/cancel`);

export const resetServiceRequestCompletionPin = (bookingId) =>
  api.post(`/service-requests/${bookingId}/completion-pin`);

export const submitBookingReview = (bookingId, rating, comment) =>
  api.post(`/service-requests/${bookingId}/review`, { rating, comment });

export const fetchBookingInvoice = (bookingId) =>
  api.get(`/service-requests/${bookingId}/invoice`);

export const downloadBookingInvoice = (bookingId) =>
  api.get(`/service-requests/${bookingId}/invoice/pdf`, { responseType: "blob" });

export const fetchBookingPriceHistory = (bookingId) =>
  api.get(`/service-requests/${bookingId}/price-history`);

export const proposeBookingPrice = (bookingId, additionalAmount, explanation) =>
  api.post(`/service-requests/${bookingId}/price-proposals`, {
    additionalAmount: Number(additionalAmount),
    explanation,
  });

export const decideBookingPriceProposal = (bookingId, proposalId, decision) =>
  api.put(`/service-requests/${bookingId}/price-proposals/${proposalId}`, { decision });

export const fetchCustomerProfile = () =>
  api.get("/customer/profile");

export const updateCustomerProfile = (data) =>
  api.put("/customer/profile", data);

export const fetchCustomerAddresses = () =>
  api.get("/customer/addresses");

export const addCustomerAddress = (data) =>
  api.post("/customer/addresses", data);

export const fetchNotifications = () =>
  api.get("/notifications");

export const markNotificationRead = (id) =>
  api.post(`/notifications/${id}/read`);

export const submitSupportTicket = (data) =>
  api.post("/support/tickets", data);

export const fetchSupportTickets = () =>
  api.get("/support/tickets");

export const postNewService = (data) =>
  api.post("/services", data);

export const fetchAdminDashboard = () =>
  api.get("/admin/dashboard");

export const fetchAdminUsers = () =>
  api.get("/admin/users");

export const updateAdminUserStatus = (id, status) =>
  api.put(`/admin/users/${id}/status`, { status });

export const fetchAdminProviders = () =>
  api.get("/admin/providers");

export const decideProviderVerification = (id, status, note = "") =>
  api.post(`/admin/providers/${id}/approval`, { status, note });

export const fetchAdminProviderDocuments = () =>
  api.get("/admin/provider-documents");

export const fetchAdminProviderDocumentFile = (id) =>
  api.get(`/admin/provider-documents/${id}/file`, { responseType: "blob" });

export const reviewAdminProviderDocument = (id, status, note = "") =>
  api.put(`/admin/provider-documents/${id}/review`, { status, note });

export const fetchAdminCategories = () =>
  api.get("/admin/categories");

function categoryRequestBody(data, image) {
  if (!image) return data;
  const form = new FormData();
  form.append("category", new Blob([JSON.stringify(data)], { type: "application/json" }));
  form.append("image", image, image.name);
  return form;
}

export const createAdminCategory = (data, image) =>
  api.post(
    "/admin/categories",
    categoryRequestBody(data, image),
    image ? multipartRequestConfig : undefined,
  );

export const updateAdminCategory = (id, data, image) =>
  api.put(
    `/admin/categories/${id}`,
    categoryRequestBody(data, image),
    image ? multipartRequestConfig : undefined,
  );

export const fetchAdminBookings = () =>
  api.get("/admin/bookings");

export const updateAdminBookingStatus = (id, status, note = "") =>
  api.put(`/admin/bookings/${id}/status`, { status, note });

export const fetchAdminTickets = () =>
  api.get("/admin/tickets");

export const updateAdminTicket = (id, status, resolution) =>
  api.put(`/admin/tickets/${id}`, { status, resolution });

export const fetchAdminReviews = () =>
  api.get("/admin/reviews");

export const updateAdminReviewVisibility = (id, status) =>
  api.put(`/admin/reviews/${id}/visibility`, { status });

export const fetchAdminPayments = () =>
  api.get("/admin/payments");

export const fetchAdminPricingConfiguration = () =>
  api.get("/admin/pricing-config");

export const updateAdminPricingConfiguration = (configuration) =>
  api.put("/admin/pricing-config", configuration);

export const updateAdminPaymentConfiguration = (upiId) =>
  api.put("/admin/payment-config", { upiId });

export const exportAdminBookings = () =>
  api.get("/admin/reports/bookings.csv", { responseType: "blob" });

export const fetchProviderProfile = () =>
  api.get("/provider/profile");

export const fetchProviderVerificationDocuments = () =>
  api.get("/provider/verification-documents");

export const uploadProviderVerificationDocument = (documentType, file) => {
  const formData = new FormData();
  formData.append("documentType", documentType);
  formData.append("file", file);
  return api.post("/provider/verification-documents", formData, multipartRequestConfig);
};

export const updateProviderProfile = (data) =>
  api.put("/provider/profile", data);

export const updateProviderLocation = (data) =>
  api.put("/provider/location", data);

export const fetchProviderAvailability = () =>
  api.get("/provider/availability");

export const updateProviderAvailability = (data) =>
  api.put("/provider/availability", data);

export const fetchProviderDashboard = () =>
  api.get("/provider/dashboard");

export default api;