import React from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import BookingChat from "../components/BookingChat";

export default function BookingChatPage({
  bookings,
  user,
  onRefresh,
}) {
  const navigate = useNavigate();
  const { bookingId } = useParams();
  const isProvider = user?.role?.toUpperCase() === "PROVIDER";
  const booking = bookings.find((item) => String(item.id) === bookingId);
  if (!booking) {
    return (
      <main className="dashboard conversation-page">
        <section className="custom-card conversation-unavailable">
          <h1>Loading booking chat</h1>
          <p>Refreshing your bookings. If the conversation does not appear, retry.</p>
          <button type="button" className="accept-button" onClick={onRefresh}>Refresh bookings</button>
        </section>
      </main>
    );
  }
  const backPath = isProvider ? `/provider/bookings/${booking.id}` : "/customer/bookings";
  const otherPartyName = isProvider
    ? booking.customerName || "Customer"
    : booking.provider?.fullName || booking.providerName || "Assigned provider";
  const accessConfirmed = isProvider
    ? Number(booking.provider?.id) === Number(user?.id)
    : Boolean(booking.provider);
  const chatOpenStatuses = ["ACCEPTED", "CONFIRMED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS"];
  const chatAvailable = chatOpenStatuses.includes(
    (booking.status || "").toUpperCase().replace(/[\s-]+/g, "_"),
  );

  if (!accessConfirmed || !chatAvailable) {
    return (
      <main className="dashboard conversation-page">
        <Link className="secondary-link-button" to={backPath}>Back to booking</Link>
        <section className="custom-card conversation-unavailable">
          <i className={`bi ${chatAvailable ? "bi-shield-lock" : "bi-check-circle"}`} aria-hidden="true"></i>
          <h1>{chatAvailable ? "Chat is not available" : "This booking chat is closed"}</h1>
          <p>{chatAvailable
            ? "This conversation is available only to the customer and the provider assigned to this booking."
            : "Chat is available while the service is accepted or in progress. This booking is no longer active."}</p>
          <button type="button" className="accept-button" onClick={onRefresh}>
            Refresh booking status
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard conversation-page">
      <div className="conversation-page-toolbar">
        <button type="button" className="secondary-link-button" onClick={() => navigate(backPath)}>
          <i className="bi bi-arrow-left" aria-hidden="true"></i>
          Back to {isProvider ? "job workspace" : "my bookings"}
        </button>
        <span>
          Booking {booking.bookingId || booking.id} · {booking.service}
        </span>
      </div>
      <BookingChat
        bookingId={booking.id}
        currentUserId={user?.id}
        otherPartyName={otherPartyName}
      />
    </main>
  );
}
