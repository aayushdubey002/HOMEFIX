import React from "react";

function BookingTable({
  bookings = [],
  user,
  onAccept,
  onReject,
  loading = false,
  actionLoading = false,
}) {
  const isProvider =
    user?.role?.toUpperCase() === "PROVIDER";

  const isCustomer =
    user?.role?.toUpperCase() === "CUSTOMER";

  // Format booking status for display
  const getStatus = (status) => {
    return (status || "PENDING")
      .toUpperCase()
      .replace(/[_-]+/g, " ");
  };

  // CSS class according to status
  const getStatusClass = (status) => {
    switch (getStatus(status)) {
      case "ACCEPTED":
        return "status-accepted";

      case "COMPLETED":
        return "status-completed";

      case "CANCELLED":
        return "status-cancelled";

      case "REJECTED":
        return "status-rejected";

      case "IN PROGRESS":
      case "IN_PROGRESS":
        return "status-in-progress";

      default:
        return "status-pending";
    }
  };

  // Check whether provider can accept the request
  const canAccept = (booking) => {
    const pending =
      getStatus(booking.status) === "PENDING";

    const unassigned =
      !booking.provider &&
      (
        !booking.providerName ||
        booking.providerName === "Provider will be assigned" ||
        booking.providerName === "Not assigned"
      );

    return pending && unassigned;
  };

  if (loading) {
    return (
      <section className="booking-section custom-card">
        <h2>
          {isProvider ? "Service Requests" : "My Bookings"}
        </h2>

        <p>Loading bookings...</p>
      </section>
    );
  }

  return (
    <section className="booking-section custom-card">
      <h2>
        {isProvider ? "Service Requests" : "My Bookings"}
      </h2>

      {bookings.length === 0 ? (
        <div className="no-bookings">
          <p>
            {isProvider
              ? "No matching service requests available."
              : "You have not created any service requests yet."}
          </p>
        </div>
      ) : (
        <div className="booking-table-wrapper">
          <table className="booking-table">
            <thead>
              <tr>
                <th>Booking ID</th>
                <th>Service</th>
                <th>Customer</th>

                {isProvider && <th>Phone</th>}

                <th>Address</th>
                <th>Preferred Date</th>
                <th>Price</th>
                <th>Provider</th>
                <th>Status</th>

                {isProvider && <th>Action</th>}
              </tr>
            </thead>

            <tbody>
              {bookings.map((booking) => (
                <tr key={booking.id}>
                  {/* Booking ID */}
                  <td>
                    {booking.bookingCode || booking.bookingId || booking.id || "N/A"}
                  </td>

                  {/* Service */}
                  <td>
                    {booking.service || "N/A"}
                  </td>

                  {/* Customer name */}
                  <td>
                    {booking.customerName || "N/A"}
                  </td>

                  {/* Phone: provider only */}
                  {isProvider && (
                    <td>
                      {booking.phone || "N/A"}
                    </td>
                  )}

                  {/* Address */}
                  <td>
                    {booking.address || "N/A"}
                  </td>

                  {/* Preferred date */}
                  <td>
                    {booking.preferredDate || "N/A"}
                  </td>

                  {/* Price */}
                  <td>
                    {booking.price != null
                      ? `₹${booking.price}`
                      : "N/A"}
                  </td>

                  {/* Assigned provider */}
                  <td>
                    {booking.providerName &&
                    booking.providerName !==
                      "Provider will be assigned"
                      ? booking.providerName
                      : "Not assigned"}
                  </td>

                  {/* Current status */}
                  <td>
                    <span
                      className={`booking-status ${getStatusClass(
                        booking.status
                      )}`}
                    >
                      {getStatus(booking.status)}
                    </span>
                  </td>

                  {/* Provider actions */}
                  {isProvider && (
                    <td>
                      {getStatus(booking.status) === "ACCEPTED" ? (
                        <button
                          type="button"
                          className="accept-button"
                          disabled
                          aria-label={`Request ${booking.bookingCode || booking.id} already accepted`}
                        >
                          <i className="bi bi-check2" aria-hidden="true"></i>
                          Accepted
                        </button>
                      ) : canAccept(booking) ? (
                        <div className="booking-actions">
                          <button
                            type="button"
                            className="accept-button"
                            disabled={actionLoading}
                            onClick={() => {
                              if (onAccept) {
                                onAccept(booking.id);
                              }
                            }}
                          >
                            Accept
                          </button>

                          <button
                            type="button"
                            className="reject-button"
                            disabled={actionLoading}
                            onClick={() => {
                              if (onReject) {
                                onReject(booking.id);
                              }
                            }}
                          >
                            Dismiss
                          </button>
                        </div>
                      ) : (
                        <span className="assigned-label">
                          {getStatus(booking.status) === "ACCEPTED"
                            ? "Accepted"
                            : "Unavailable"}
                        </span>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Notes displayed separately when available */}
      {bookings.some((booking) => booking.notes) && (
        <div className="booking-notes">
          <h3>Booking Notes</h3>

          {bookings
            .filter((booking) => booking.notes)
            .map((booking) => (
              <div
                key={`note-${booking.id}`}
                className="booking-note"
              >
                <strong>
                  {booking.bookingCode || `Booking ${booking.id}`}:
                </strong>{" "}
                {booking.notes}
              </div>
            ))}
        </div>
      )}
    </section>
  );
}

export default BookingTable;