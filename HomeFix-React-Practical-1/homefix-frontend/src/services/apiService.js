import axios from "axios";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "https://homefix-r9ca.onrender.com/api";
const AUTH_TOKEN_KEY = "homefixAccessToken";

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

export const fetchBookings = () =>
  api.get("/service-requests");

export const postServiceRequest = (data) =>
  api.post("/service-requests", data);

export const acceptServiceRequest = (bookingId) =>
  api.put(
    `/service-requests/${bookingId}/accept`,
    null
  );

export const rejectServiceRequest = (bookingId) =>
  api.put(
    `/service-requests/${bookingId}/reject`,
    null
  );

export const postNewService = (data) =>
  api.post("/services", data);

export default api;