import axios from "axios";

const API_BASE_URL = "https://homefix-r9ca.onrender.com/api";

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json"
  }
});

// REGISTER
export const registerUser = (data) => {
  return api.post("/auth/register", data);
};

// LOGIN
export const loginUser = (data) => {
  return api.post("/auth/login", data);
};

// DASHBOARD STATS
export const fetchStats = (userId) => {
  return api.get("/dashboard/stats", {
    params: {
      userId
    }
  });
};

// RECENT BOOKINGS - LOGGED IN USER ONLY
export const fetchBookings = (userId) => {
  return api.get("/service-requests", {
    params: {
      userId: userId
    }
  });
};

// CREATE SERVICE REQUEST
export const postServiceRequest = (data, userId) => {
  return api.post("/service-requests", data, {
    params: {
      userId: userId
    }
  });
};

// ADD NEW SERVICE
export const postNewService = (data) => {
  return api.post("/services", data);
};

export default api;