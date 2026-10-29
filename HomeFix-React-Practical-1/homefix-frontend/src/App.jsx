import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  BrowserRouter,
  Link,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";

import BookingSuccessPage from "./pages/BookingSuccessPage";
import BookingChatPage from "./pages/BookingChatPage";
import CustomerHome from "./pages/CustomerHome";
import MyBookingsPage from "./pages/MyBookingsPage";
import ProviderBookingPage from "./pages/ProviderBookingPage";
import ProviderDashboard from "./pages/ProviderDashboard";
import ProviderApplicationStatusPage from "./pages/ProviderApplicationStatusPage";
import ServiceBookingPage from "./pages/ServiceBookingPage";
import AdminDashboard from "./pages/AdminDashboard";
import CustomerProfilePage from "./pages/CustomerProfilePage";
import NotificationCenter from "./components/NotificationCenter";
import HomeFixLogoMark from "./components/HomeFixLogoMark";
import LoginRegister from "./LoginRegister";
import {
  acceptServiceRequest,
  cancelServiceRequest,
  decideBookingPriceProposal,
  fetchBookings,
  fetchCurrentUser,
  fetchServiceCatalog,
  postServiceRequest,
  proposeBookingPrice,
  rejectServiceRequest,
  resetServiceRequestCompletionPin,
  submitBookingReview,
  updateServiceRequestStatus,
} from "./services/apiService";
import {
  normalizeServiceCatalog,
  subscribeToServiceCatalogUpdates,
} from "./services/serviceCatalog";
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
  const role = user?.role?.toUpperCase();
  if (role === "PROVIDER") {
    return user.providerApproved ? "/provider" : "/provider/application-status";
  }
  if (role === "ADMIN") return "/admin";
  return "/customer";
}

function actionFailureMessage(error, action) {
  const responseMessage = error.response?.data?.message;
  if (typeof responseMessage === "string" && responseMessage.trim()) {
    return responseMessage;
  }
  if (error.response?.status === 403) {
    return `You are not authorized to ${action}. Sign in again and check your provider verification; contact HomeFix support if this continues.`;
  }
  if (error.response?.status === 401) {
    return "Your session has expired. Sign in again and retry.";
  }
  return error.message || `Unable to ${action}.`;
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
  const isProvider = user.role?.toUpperCase() === "PROVIDER";

  return (
    <header className={`app-header${isCustomer ? " customer-app-header" : ""}`}>
      <div className="header-inner">
        <Link className="brand-lockup" to={userHomePath(user)} aria-label="HomeFix home">
          <span className="brand-mark" aria-hidden="true">
            <HomeFixLogoMark className="homefix-logo-svg" />
          </span>
          <span>
            <strong>HomeFix</strong>
            <small>Home services, made simple</small>
          </span>
        </Link>

        <div className="header-actions">
          {isCustomer && (
            <nav className="customer-nav" aria-label="Customer navigation">
              <NavLink end to="/customer">Home</NavLink>
              <NavLink to="/customer/bookings">My Bookings</NavLink>
              <NavLink to="/customer/profile">Profile & addresses</NavLink>
            </nav>
          )}
          {isProvider && (
            <nav className="provider-header-nav" aria-label="Provider navigation">
              <NavLink end to="/provider">Provider portal</NavLink>
              {!user.providerApproved && (
                <NavLink to="/provider/application-status">Application</NavLink>
              )}
            </nav>
          )}
          <NotificationCenter />

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
              <span>{user.role?.toLowerCase() === "provider"
                ? "Service provider"
                : user.role?.toLowerCase() === "admin" ? "Administrator" : "Customer"}</span>
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
  const hasLoadedServiceCatalog = useRef(false);
  const navigate = useNavigate();
  const location = useLocation();
  const isProvider = user?.role?.toUpperCase() === "PROVIDER";
  const isCustomer = user?.role?.toUpperCase() === "CUSTOMER";
  const isAdmin = user?.role?.toUpperCase() === "ADMIN";

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
          setMessage("");
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
        const status = error.response?.status;
        const details = error.response?.data?.message || error.message;
        setMessage(status
          ? `Session check (/auth/me) failed with HTTP ${status}${details ? `: ${details}` : ""}`
          : `Unable to verify your session with HomeFix: ${details || "Please retry."}`);
      });

    return () => {
      active = false;
    };
  }, [user?.id, navigate]);

  const loadServiceCatalog = useCallback(async () => {
    if (!isCustomer) return;
    if (!hasLoadedServiceCatalog.current) setServicesLoading(true);
    setServicesError("");
    try {
      const response = await fetchServiceCatalog();
      setServiceCatalog(normalizeServiceCatalog(response.data));
      hasLoadedServiceCatalog.current = true;
    } catch (error) {
      console.error("Failed to load service catalog:", error);
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
      hasLoadedServiceCatalog.current = false;
    }
  }, [isCustomer, loadServiceCatalog]);

  useEffect(() => {
    if (!isCustomer || location.pathname !== "/customer") return undefined;
    const unsubscribe = subscribeToServiceCatalogUpdates(loadServiceCatalog);
    const refreshVisibleCatalog = () => {
      if (document.visibilityState === "visible") loadServiceCatalog();
    };
    const intervalId = window.setInterval(refreshVisibleCatalog, 30000);
    window.addEventListener("focus", refreshVisibleCatalog);
    return () => {
      unsubscribe();
      window.clearInterval(intervalId);
      window.removeEventListener("focus", refreshVisibleCatalog);
    };
  }, [isCustomer, location.pathname, loadServiceCatalog]);

  const loadBookings = useCallback(async (showLoading = false) => {
    if (!user?.id || !["CUSTOMER", "PROVIDER"].includes(user?.role?.toUpperCase())) return;
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
    if (isProvider && location.pathname === "/provider/application-status") {
      setBookings([]);
      setBookingsError("");
      setLoading(false);
      return undefined;
    }
    if (!user?.id || isAdmin) {
      setBookings([]);
      return undefined;
    }

    let active = true;
    let timeoutId;
    const refreshBookings = async (showLoading) => {
      await loadBookings(showLoading);
      if (active) {
        timeoutId = window.setTimeout(() => refreshBookings(false), 5000);
      }
    };
    refreshBookings(true);
    return () => {
      active = false;
      window.clearTimeout(timeoutId);
    };
  }, [user?.id, isAdmin, isProvider, location.pathname, loadBookings]);

  const handleLogin = (loggedInUser, accessToken) => {
    if (
      !loggedInUser?.id
      || !["CUSTOMER", "PROVIDER", "ADMIN"].includes(loggedInUser.role?.toUpperCase())
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
      navigate(`/provider/bookings/${bookingId}`);
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
      return true;
    } catch (error) {
      console.error("Dismiss request failed:", error);
      setMessage(error.response?.data?.message || "Unable to dismiss request.");
      return false;
    } finally {
      setActionLoading(false);
    }
  };

  const handleUpdateStatus = async (bookingId, status, paymentDetails = {}) => {
    try {
      setActionLoading(true);
      setMessage("");
      await updateServiceRequestStatus(
        bookingId,
        status,
        "",
        paymentDetails.paymentReference || "",
        paymentDetails.completionPin || "",
        paymentDetails.paymentMethod || "",
        paymentDetails.cashReceivedAmount ?? null,
      );
      setMessage(`Booking updated to ${status.replaceAll("_", " ").toLowerCase()}.`);
      await loadBookings(false);
      return { success: true };
    } catch (error) {
      const errorMessage = actionFailureMessage(error, "update this booking");
      setMessage(errorMessage);
      return { success: false, message: errorMessage };
    } finally {
      setActionLoading(false);
    }
  };

  const handleProposeBookingPrice = async (bookingId, additionalAmount, explanation) => {
    try {
      setActionLoading(true);
      setMessage("");
      await proposeBookingPrice(bookingId, additionalAmount, explanation);
      setMessage("Extra charge sent to the customer for approval.");
      await loadBookings(false);
      return { success: true };
    } catch (error) {
      const errorMessage = actionFailureMessage(error, "request a price change for this booking");
      setMessage(errorMessage);
      return { success: false, message: errorMessage };
    } finally {
      setActionLoading(false);
    }
  };

  const handlePriceProposalDecision = async (bookingId, proposalId, decision) => {
    try {
      setActionLoading(true);
      setMessage("");
      await decideBookingPriceProposal(bookingId, proposalId, decision);
      setMessage(decision === "APPROVED"
        ? "Extra charge approved. The booking total has been updated."
        : "Extra charge rejected. The existing booking total remains unchanged.");
      await loadBookings(false);
    } catch (error) {
      setMessage(error.response?.data?.message || "Unable to send the extra charge for approval.");
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancelBooking = async (bookingId) => {
    try {
      const response = await cancelServiceRequest(bookingId);
      if (response.data?.status?.toUpperCase().replaceAll(" ", "_") !== "CANCELLED") {
        throw new Error("The server did not confirm that this booking was cancelled.");
      }
      setMessage(isProvider
        ? "Booking cancelled. The customer has been notified."
        : "Booking cancelled. The provider has been notified.");
      await loadBookings(false);
      return { success: true };
    } catch (error) {
      const errorMessage = error.response?.data?.message
        || error.message
        || "Unable to cancel this booking. Please retry.";
      setMessage(errorMessage);
      return { success: false, message: errorMessage };
    }
  };

  const handleResetCompletionPin = async (bookingId) => {
    try {
      const response = await resetServiceRequestCompletionPin(bookingId);
      setBookings((current) => current.map((booking) => (
        booking.id === bookingId
          ? { ...booking, completionPin: response.data?.completionPin }
          : booking
      )));
      setMessage(response.data?.message || "A new job-start PIN was generated.");
      await loadBookings(false);
    } catch (error) {
      setMessage(error.response?.data?.message || "Unable to generate a new job-start PIN.");
    }
  };

  const handleSubmitReview = async (bookingId, rating, comment) => {
    try {
      await submitBookingReview(bookingId, rating, comment);
      setMessage("Thank you. Your review was submitted.");
      await loadBookings(false);
    } catch (error) {
      setMessage(error.response?.data?.message || "Unable to submit your review.");
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
              bookings={bookings}
              bookingsLoading={loading}
              bookingsError={bookingsError}
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
            <BookingSuccessPage
              user={user}
              bookings={bookings}
              onRefresh={() => loadBookings(false)}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/bookings"
        element={
          <ProtectedRoute user={user} allowedRole="CUSTOMER">
            <MyBookingsPage
              userId={user?.id}
              bookings={bookings}
              services={serviceCatalog}
              loading={loading}
              actionLoading={actionLoading}
              error={bookingsError}
              onRefresh={() => loadBookings(true)}
              onCancel={handleCancelBooking}
              onReview={handleSubmitReview}
              onResetCompletionPin={handleResetCompletionPin}
              onPriceProposalDecision={handlePriceProposalDecision}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/bookings/:bookingId"
        element={
          <ProtectedRoute user={user} allowedRole="CUSTOMER">
            <MyBookingsPage
              userId={user?.id}
              bookings={bookings}
              services={serviceCatalog}
              loading={loading}
              actionLoading={actionLoading}
              error={bookingsError}
              onRefresh={() => loadBookings(true)}
              onCancel={handleCancelBooking}
              onReview={handleSubmitReview}
              onResetCompletionPin={handleResetCompletionPin}
              onPriceProposalDecision={handlePriceProposalDecision}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/bookings/:bookingId/chat"
        element={
          <ProtectedRoute user={user} allowedRole="CUSTOMER">
            <BookingChatPage
              bookings={bookings}
              user={user}
              onRefresh={() => loadBookings(true)}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/provider/bookings/:bookingId"
        element={
          <ProtectedRoute user={user} allowedRole="PROVIDER">
            <ProviderBookingPage
              bookings={bookings}
              user={user}
              loading={loading}
              actionLoading={actionLoading}
              onAccept={handleAccept}
              onReject={handleReject}
              onUpdateStatus={handleUpdateStatus}
              onProposePrice={handleProposeBookingPrice}
              onCancel={handleCancelBooking}
              onRefresh={() => loadBookings(true)}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/provider/bookings/:bookingId/start"
        element={
          <ProtectedRoute user={user} allowedRole="PROVIDER">
            <ProviderBookingPage
              view="start"
              bookings={bookings}
              user={user}
              loading={loading}
              actionLoading={actionLoading}
              onAccept={handleAccept}
              onReject={handleReject}
              onUpdateStatus={handleUpdateStatus}
              onProposePrice={handleProposeBookingPrice}
              onCancel={handleCancelBooking}
              onRefresh={() => loadBookings(true)}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/provider/bookings/:bookingId/payment"
        element={
          <ProtectedRoute user={user} allowedRole="PROVIDER">
            <ProviderBookingPage
              view="payment"
              bookings={bookings}
              user={user}
              loading={loading}
              actionLoading={actionLoading}
              onAccept={handleAccept}
              onReject={handleReject}
              onUpdateStatus={handleUpdateStatus}
              onProposePrice={handleProposeBookingPrice}
              onCancel={handleCancelBooking}
              onRefresh={() => loadBookings(true)}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/provider/bookings/:bookingId/chat"
        element={
          <ProtectedRoute user={user} allowedRole="PROVIDER">
            <BookingChatPage
              bookings={bookings}
              user={user}
              onRefresh={() => loadBookings(true)}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/profile"
        element={
          <ProtectedRoute user={user} allowedRole="CUSTOMER">
            <CustomerProfilePage />
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
          path="/provider/application-status"
          element={
            <ProtectedRoute user={user} allowedRole="PROVIDER">
              <ProviderApplicationStatusPage onContinue={() => navigate("/provider")} />
            </ProtectedRoute>
          }
        />
        <Route
          path="/provider/*"
          element={
            <ProtectedRoute user={user} allowedRole="PROVIDER">
              <ProviderDashboard
                user={user}
                onApplicationStatus={() => navigate("/provider/application-status")}
                bookings={bookings}
                loading={loading}
                actionLoading={actionLoading}
                onAccept={handleAccept}
                onReject={handleReject}
                onUpdateStatus={handleUpdateStatus}
                onProposePrice={handleProposeBookingPrice}
                error={bookingsError}
                onRefresh={() => loadBookings(true)}
              />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <ProtectedRoute user={user} allowedRole="ADMIN">
              <AdminDashboard />
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
