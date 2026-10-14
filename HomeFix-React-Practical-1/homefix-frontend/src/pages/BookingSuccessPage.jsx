import React, { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { fetchBookings } from "../services/apiService";

function sameBookingIdentifier(booking, identifier) {
  return String(booking.bookingId ?? "") === identifier
    || String(booking.id ?? "") === identifier;
}

export default function BookingSuccessPage({ user }) {
  const { bookingId } = useParams();
  const location = useLocation();
  const routedBooking = location.state?.booking;
  const [booking, setBooking] = useState(
    routedBooking && sameBookingIdentifier(routedBooking, bookingId) ? routedBooking : null
  );
  const [loading, setLoading] = useState(!booking);
  const [error, setError] = useState("");

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
        <span className="eyebrow">YOUR SERVICE IS SCHEDULED</span>
        <h1>Booking Successful!</h1>
        <p className="booking-success-intro">
          Your booking has been saved. You can review its details below.
        </p>
        {error && <p className="bookings-error" role="alert">{error}</p>}

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
            <small>Current status</small>
            <span className={`booking-status status-${statusClass}`}>{status}</span>
          </div>
        </div>

        <div className="result-actions">
          <Link className="primary-link-button" to="/customer/bookings">View My Bookings</Link>
          <Link className="secondary-link-button" to="/customer">Back to Home</Link>
        </div>
      </section>
    </main>
  );
}
