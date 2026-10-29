import React, { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { fetchBookings } from "../services/apiService";
import BookingStatusTimeline from "../components/BookingStatusTimeline";
import RazorpayCheckoutButton from "../components/RazorpayCheckoutButton";

function sameBookingIdentifier(booking, identifier) {
  return String(booking.bookingId ?? "") === identifier
    || String(booking.id ?? "") === identifier;
}

export default function BookingSuccessPage({ user, bookings = [], onRefresh }) {
  const { bookingId } = useParams();
  const location = useLocation();
  const routedBooking = location.state?.booking;
  const currentBooking = bookings.find((item) => sameBookingIdentifier(item, bookingId));
  const [booking, setBooking] = useState(
    currentBooking
      || (routedBooking && sameBookingIdentifier(routedBooking, bookingId) ? routedBooking : null)
  );
  const [loading, setLoading] = useState(!booking);
  const [error, setError] = useState("");

  useEffect(() => {
    if (currentBooking) {
      setBooking(currentBooking);
      setError("");
      setLoading(false);
    }
  }, [currentBooking]);

  useEffect(() => {
    if (!user?.id || !bookingId) return undefined;

    let active = true;
    if (!routedBooking) setLoading(true);
    setError("");

    fetchBookings()
      .then((response) => {
        const savedBooking = Array.isArray(response.data)
          ? response.data.find((item) => sameBookingIdentifier(item, bookingId))
          : null;
        if (active) {
          if (savedBooking) {
            setBooking(savedBooking);
          } else if (!routedBooking) {
            setBooking(null);
            setError("We couldn't find this booking in your account.");
          }
        }
      })
      .catch((requestError) => {
        if (active) {
          setError(
            requestError.response?.data?.message
              || "We couldn't load this booking. You can still find it in My Bookings."
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [bookingId, routedBooking, user?.id]);

  if (loading) {
    return (
      <main className="dashboard">
        <section className="booking-result-card" aria-live="polite">
          <p>Loading your saved booking...</p>
        </section>
      </main>
    );
  }

  if (!booking) {
    return (
      <main className="dashboard">
        <section className="booking-result-card booking-result-unavailable">
          <span className="result-icon result-icon-info" aria-hidden="true">
            <i className="bi bi-info-circle-fill"></i>
          </span>
          <h1>Booking details unavailable</h1>
          <p>{error || "This booking is not available in your account."}</p>
          <div className="result-actions">
            <Link className="primary-link-button" to="/customer/bookings">View My Bookings</Link>
            <Link className="secondary-link-button" to="/customer">Back to Home</Link>
          </div>
        </section>
      </main>
    );
  }

  const status = booking.status || "Status unavailable";
  const statusClass = String(booking.status || "unknown")
    .toLowerCase()
    .replace(/[\s_]+/g, "-");

  return (
    <main className="dashboard">
      <section className="booking-result-card">
        <span className="result-icon" aria-hidden="true">
          <i className="bi bi-check-lg"></i>
        </span>
        <span className="eyebrow">REQUEST RECEIVED</span>
        <h1>Booking request submitted</h1>
        <p className="booking-success-intro">
          Your request is saved. A provider must accept it before the service is confirmed.
        </p>
        <p className="booking-success-tracking-hint">
          You can check the latest progress here or return to this booking from My Bookings.
          Status updates refresh automatically.
        </p>
        {error && <p className="bookings-error" role="alert">{error}</p>}
        <BookingStatusTimeline status={booking.status} />

        <div className="booking-result-details">
          <div>
            <small>Booking ID</small>
            <strong>{booking.bookingId ?? booking.id}</strong>
          </div>
          <div>
            <small>Service</small>
            <strong>{booking.service || "Not available"}</strong>
          </div>
          <div>
            <small>Preferred date</small>
            <strong>{booking.preferredDate || "Not available"}</strong>
          </div>
          <div>
            <small>Price</small>
            <strong>{booking.price == null ? "Not available" : `₹${booking.price}`}</strong>
          </div>
          <div>
            <small>Payment method</small>
            <strong>{booking.paymentMethod || "Cash"}</strong>
          </div>
          <div>
            <small>Payment status</small>
            <strong>{booking.paymentStatus || "UNPAID"}</strong>
          </div>
          {booking.completionPin && (
            <div className="booking-start-pin">
              <small>Job start PIN</small>
              <strong>{booking.completionPin}</strong>
              <span>Share this code with your provider when they arrive.</span>
            </div>
          )}
          <div>
            <small>Current status</small>
            <span className={`booking-status status-${statusClass}`}>{status}</span>
          </div>
        </div>

        {booking.provider && (
          <section className="provider-profile-summary" aria-label="Assigned provider profile">
            <strong>Assigned provider: {booking.provider.fullName || booking.providerName}</strong>
            {booking.providerExperience && <span>Experience: {booking.providerExperience.replaceAll("_", " ").toLowerCase()}</span>}
            {booking.providerServiceArea && <span>Service area: {booking.providerServiceArea}</span>}
            {booking.providerProfessionalSummary && <p>{booking.providerProfessionalSummary}</p>}
          </section>
        )}

        {booking.paymentMethod === "UPI" && booking.paymentStatus === "PAID" && (
          <p className="payment-confirmed-note" role="status">UPI payment has been verified by Razorpay.</p>
        )}
        {booking.paymentMethod === "UPI"
          && booking.paymentStatus !== "PAID"
          && status.toUpperCase() === "COMPLETED" && (
          <section className="booking-upi-payment" aria-labelledby="booking-upi-title">
            <h2 id="booking-upi-title">Secure UPI payment</h2>
            <p>Razorpay verifies the payment before the booking ledger is updated.</p>
            <RazorpayCheckoutButton
              bookingId={booking.id}
              bookingCode={booking.bookingId ?? booking.id}
              amount={booking.price}
              onRefresh={onRefresh}
            />
          </section>
        )}
        {booking.paymentMethod === "UPI"
          && booking.paymentStatus !== "PAID"
          && status.toUpperCase() !== "COMPLETED" && (
            <p className="payment-disclaimer">
              UPI checkout becomes available after your provider completes the service.
            </p>
          )}

        <div className="result-actions">
          {booking.id != null && (
            <Link className="primary-link-button" to={`/customer/bookings/${booking.id}`}>
              Track booking status
            </Link>
          )}
          <Link className="primary-link-button" to="/customer/bookings">View My Bookings</Link>
          <Link className="secondary-link-button" to="/customer">Back to Home</Link>
        </div>
      </section>
    </main>
  );
}
