import React, { useCallback, useEffect, useState } from "react";
import {
  BrowserRouter,
  Link,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";

import BookingSuccessPage from "./pages/BookingSuccessPage";
import CustomerHome from "./pages/CustomerHome";
import MyBookingsPage from "./pages/MyBookingsPage";
import ProviderDashboard from "./pages/ProviderDashboard";
import ServiceBookingPage from "./pages/ServiceBookingPage";
import LoginRegister from "./LoginRegister";
import {
  acceptServiceRequest,
  fetchBookings,
  fetchCurrentUser,
  fetchServiceCatalog,
  postServiceRequest,
  rejectServiceRequest,
} from "./services/apiService";
import { normalizeServiceCatalog } from "./services/serviceCatalog";
import "./App.css";

const USER_SESSION_KEY = "homefixUser";
const AUTH_TOKEN_KEY = "homefixAccessToken";

function readStoredUser() {
  try {
    if (!sessionStorage.getItem(AUTH_TOKEN_KEY)) {
      sessionStorage.removeItem(USER_SESSION_KEY);
      return null;
    }
    const savedUser = sessionStorage.getItem(USER_SESSION_KEY);
    return savedUser ? JSON.parse(savedUser) : null;
  } catch {
    sessionStorage.removeItem(USER_SESSION_KEY);
    sessionStorage.removeItem(AUTH_TOKEN_KEY);
    return null;
  }
}

function userHomePath(user) {
  return user?.role?.toUpperCase() === "PROVIDER" ? "/provider" : "/customer";
}

function ProtectedRoute({ user, allowedRole, children }) {
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (user.role?.toUpperCase() !== allowedRole) {
    return <Navigate to={userHomePath(user)} replace />;
  }

  return children;
}

function AppHeader({ user, onLogout }) {
  const isCustomer = user.role?.toUpperCase() === "CUSTOMER";

  return (
    <header className="app-header">
      <div className="header-inner">
        <Link className="brand-lockup" to={userHomePath(user)} aria-label="HomeFix home">
          <span className="brand-mark" aria-hidden="true">
            <i className="bi bi-house-heart-fill"></i>
          </span>
          <span>
            <strong>HomeFix</strong>
            <small>Home services, made simple</small>
          </span>
        </Link>

        <div className="header-actions">
          {isCustomer && (
            <nav className="customer-nav" aria-label="Customer navigation">
              <Link to="/customer">Home</Link>
              <Link to="/customer/bookings">My Bookings</Link>
            </nav>
          )}

          <details className="profile-menu">
            <summary className="profile-summary">
              <span className="user-avatar" aria-hidden="true">
                {(user.fullName || user.email || "H").charAt(0).toUpperCase()}
              </span>
              <span className="user-greeting">
                <small>Signed in as</small>
                <strong>{user.fullName || user.email}</strong>
              </span>
              <i className="bi bi-chevron-down" aria-hidden="true"></i>
            </summary>
            <div className="profile-details">
              <strong>{user.fullName || "HomeFix user"}</strong>
              <span>{user.email}</span>
              <span>{user.role?.toLowerCase() === "provider" ? "Service provider" : "Customer"}</span>
              {user.phone && <span>{user.phone}</span>}
              {user.serviceArea && <span>{user.serviceArea}</span>}
            </div>
          </details>

          <button type="button" onClick={onLogout} className="logout-button">
            <i className="bi bi-box-arrow-right" aria-hidden="true"></i>
            <span>Logout</span>
          </button>
        </div>
      </div>
    </header>
  );
}

function RoutedApp() {
  const [user, setUser] = useState(readStoredUser);
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(false);
  const [bookingsError, setBookingsError] = useState("");
  const [message, setMessage] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [serviceCatalog, setServiceCatalog] = useState([]);
  const [servicesLoading, setServicesLoading] = useState(false);
  const [servicesError, setServicesError] = useState("");
  const navigate = useNavigate();
  const location = useLocation();
  const isProvider = user?.role?.toUpperCase() === "PROVIDER";
  const isCustomer = user?.role?.toUpperCase() === "CUSTOMER";

  useEffect(() => {
    if (user) {
      sessionStorage.setItem(USER_SESSION_KEY, JSON.stringify(user));
    } else {
      sessionStorage.removeItem(USER_SESSION_KEY);
    }
  }, [user]);

  useEffect(() => {
    if (!user) return undefined;

    let active = true;
    fetchCurrentUser()
      .then((response) => {
        const currentUser = response.data?.user;
        if (active && currentUser?.id) {
          setUser(currentUser);
        }
      })
      .catch((error) => {
        if (!active) return;
        if (error.response?.status === 401) {
          sessionStorage.removeItem(AUTH_TOKEN_KEY);
          sessionStorage.removeItem(USER_SESSION_KEY);
          setUser(null);
          navigate("/login", { replace: true });
          return;
        }
        setMessage(
          error.response?.data?.message
            || "Unable to verify your session with HomeFix. Please retry."
        );
      });

    return () => {
      active = false;
    };
  }, [user?.id, navigate]);

  const loadServiceCatalog = useCallback(async () => {
    if (!isCustomer) return;
    setServicesLoading(true);
    setServicesError("");
    try {
      const response = await fetchServiceCatalog();
      setServiceCatalog(normalizeServiceCatalog(response.data));
    } catch (error) {
      console.error("Failed to load service catalog:", error);
      setServiceCatalog([]);
      setServicesError(
        error.response?.data?.message
          || error.message
          || "Unable to load services and prices. Please try again."
      );
    } finally {
      setServicesLoading(false);
    }
  }, [isCustomer]);

  useEffect(() => {
    if (isCustomer) {
      loadServiceCatalog();
    } else {
      setServiceCatalog([]);
      setServicesError("");
    }
  }, [isCustomer, loadServiceCatalog]);

  const loadBookings = useCallback(async (showLoading = false) => {
    if (!user?.id || !user?.role) return;
    if (showLoading) setLoading(true);

    try {
      const response = await fetchBookings();
      if (!Array.isArray(response.data)) {
        throw new Error("The bookings response was not a list.");
      }
      setBookings(response.data);
      setBookingsError("");
    } catch (error) {
      console.error("Failed to load bookings:", error);
      setBookingsError(
        error.response?.data?.message || "Unable to load bookings. Please try again."
      );
    } finally {
      if (showLoading) setLoading(false);
    }
  }, [user?.id, user?.role]);

  useEffect(() => {
    if (!user?.id) {
      setBookings([]);
      return undefined;
    }

    loadBookings(true);
    const intervalId = window.setInterval(() => loadBookings(false), 5000);
    return () => window.clearInterval(intervalId);
  }, [user?.id, loadBookings]);

  const handleLogin = (loggedInUser, accessToken) => {
    if (
      !loggedInUser?.id
      || !["CUSTOMER", "PROVIDER"].includes(loggedInUser.role?.toUpperCase())
      || !accessToken
    ) {
      setMessage("Login response did not contain a valid user and role.");
      return;
    }

    sessionStorage.setItem(AUTH_TOKEN_KEY, accessToken);
    sessionStorage.setItem(USER_SESSION_KEY, JSON.stringify(loggedInUser));
    setUser(loggedInUser);
    setBookings([]);
    setBookingsError("");
    setMessage("");
    navigate(userHomePath(loggedInUser), { replace: true });
  };

  const handleLogout = () => {
    setUser(null);
    setBookings([]);
    setBookingsError("");
    setMessage("");
    sessionStorage.removeItem(USER_SESSION_KEY);
    sessionStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem("homefixUser");
    navigate("/login", { replace: true });
  };

  const handleCreateRequest = async (formData) => {
    if (!isCustomer || !user?.id) {
      throw new Error("Please sign in with a customer account to book a service.");
    }

    const response = await postServiceRequest(formData);
    const savedBooking = response.data;
    const bookingIdentifier = savedBooking?.bookingId ?? savedBooking?.id;

    if (
      response.status !== 201
      || !savedBooking
      || bookingIdentifier == null
      || !savedBooking.service
      || !savedBooking.preferredDate
      || savedBooking.price == null
      || !savedBooking.status
    ) {
      throw new Error("The backend did not confirm a saved booking with its details.");
    }

    setBookings((current) => [savedBooking, ...current.filter(
      (booking) => booking.id !== savedBooking.id
    )]);
    navigate(`/customer/booking-success/${encodeURIComponent(bookingIdentifier)}`, {
      state: { booking: savedBooking },
    });
    return savedBooking;
  };

  const handleAccept = async (bookingId) => {
    if (!isProvider || !user?.id) return;
    try {
      setActionLoading(true);
      setMessage("");
      const response = await acceptServiceRequest(bookingId);
      if (
        response.data?.status?.toUpperCase() !== "ACCEPTED"
        || Number(response.data?.provider?.id) !== Number(user.id)
      ) {
        throw new Error("The backend did not confirm this provider assignment.");
      }
      setMessage("Request accepted successfully.");
      await loadBookings(false);
    } catch (error) {
      console.error("Accept request failed:", error);
      setMessage(error.response?.data?.message || "Unable to accept request.");
      await loadBookings(false);
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async (bookingId) => {
    if (!isProvider || !user?.id) return;
    try {
      setActionLoading(true);
      setMessage("");
      const response = await rejectServiceRequest(bookingId);
      if (response.data?.message !== "Request dismissed") {
        throw new Error("The backend did not confirm request dismissal.");
      }
      setMessage("Request dismissed.");
      await loadBookings(false);
    } catch (error) {
      console.error("Dismiss request failed:", error);
      setMessage(error.response?.data?.message || "Unable to dismiss request.");
    } finally {
      setActionLoading(false);
    }
  };

  const customerRoutes = (
    <>
      <Route
        path="/customer"
        element={
          <ProtectedRoute user={user} allowedRole="CUSTOMER">
            <CustomerHome
              user={user}
              services={serviceCatalog}
              servicesLoading={servicesLoading}
              servicesError={servicesError}
              onRetryServices={loadServiceCatalog}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/book/:serviceSlug"
        element={
          <ProtectedRoute user={user} allowedRole="CUSTOMER">
            <ServiceBookingPage
              user={user}
              services={serviceCatalog}
              servicesLoading={servicesLoading}
              servicesError={servicesError}
              onRetryServices={loadServiceCatalog}
              onSubmit={handleCreateRequest}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/booking-success/:bookingId"
        element={
          <ProtectedRoute user={user} allowedRole="CUSTOMER">
            <BookingSuccessPage user={user} />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/bookings"
        element={
          <ProtectedRoute user={user} allowedRole="CUSTOMER">
            <MyBookingsPage
              bookings={bookings}
              loading={loading}
              error={bookingsError}
              onRefresh={() => loadBookings(true)}
            />
          </ProtectedRoute>
        }
      />
    </>
  );

  if (!user && location.pathname !== "/login") {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return (
    <div className="app">
      {user && <AppHeader user={user} onLogout={handleLogout} />}
      {message && (
        <div className="dashboard app-message" role="status">
          {message}
        </div>
      )}
      <Routes>
        <Route
          path="/login"
          element={
            user
              ? <Navigate to={userHomePath(user)} replace />
              : <LoginRegister onLogin={handleLogin} />
          }
        />
        {customerRoutes}
        <Route
          path="/provider"
          element={
            <ProtectedRoute user={user} allowedRole="PROVIDER">
              <ProviderDashboard
                user={user}
                bookings={bookings}
                loading={loading}
                actionLoading={actionLoading}
                onAccept={handleAccept}
                onReject={handleReject}
                error={bookingsError}
                onRefresh={() => loadBookings(true)}
              />
            </ProtectedRoute>
          }
        />
        <Route
          path="/"
          element={<Navigate to={user ? userHomePath(user) : "/login"} replace />}
        />
        <Route
          path="*"
          element={<Navigate to={user ? userHomePath(user) : "/login"} replace />}
        />
      </Routes>
      {location.pathname === "/login" && message && (
        <p className="app-message dashboard" role="status">{message}</p>
      )}
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <RoutedApp />
    </BrowserRouter>
  );
}
