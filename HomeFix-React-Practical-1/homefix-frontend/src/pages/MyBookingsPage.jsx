import React, { useState } from "react";
import { Link, useParams } from "react-router-dom";
import BookingStatusTimeline from "../components/BookingStatusTimeline";
import RazorpayCheckoutButton from "../components/RazorpayCheckoutButton";
import { getServiceImage, inferServiceImageKey } from "../services/serviceCatalog";
import {
  downloadBookingInvoice,
  fetchBookingInvoice,
  fetchBookingPaymentHistory,
  fetchBookingPriceHistory,
  submitSupportTicket,
} from "../services/apiService";

export default function MyBookingsPage({
  bookings,
  services = [],
  loading,
  error,
  onRefresh,
  onCancel,
  onReview,
  onResetCompletionPin,
  onPriceProposalDecision,
  actionLoading = false,
  userId,
}) {
  const { bookingId } = useParams();
  const [reviewDrafts, setReviewDrafts] = useState({});
  const [complaint, setComplaint] = useState({ bookingId: "", subject: "", description: "" });
  const [complaintMessage, setComplaintMessage] = useState("");
  const [complaintBusy, setComplaintBusy] = useState(false);
  const [invoices, setInvoices] = useState({});
  const [invoiceMessage, setInvoiceMessage] = useState("");
  const [invoiceDownloadBusy, setInvoiceDownloadBusy] = useState({});
  const [paymentHistory, setPaymentHistory] = useState({});
  const [paymentHistoryLoading, setPaymentHistoryLoading] = useState({});
  const [paymentHistoryError, setPaymentHistoryError] = useState("");
  const [priceHistory, setPriceHistory] = useState({});
  const [priceHistoryMessage, setPriceHistoryMessage] = useState("");
  const [confirmCancellationId, setConfirmCancellationId] = useState(null);
  const [cancellationMessage, setCancellationMessage] = useState("");
  const selectedBooking = bookings.find(
    (booking) => String(booking.id) === String(bookingId),
  );
  const bookingStatus = String(selectedBooking?.status || "UNKNOWN")
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, "_");
  const bookingService = selectedBooking && services.find((service) => {
    const bookingCode = String(selectedBooking.service || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
    return [
      service.requestService,
      service.service,
      service.slug,
    ].some((value) => String(value || "").toUpperCase().replace(/[^A-Z0-9]/g, "") === bookingCode);
  });
  const fallbackServiceImage = selectedBooking
    ? getServiceImage(inferServiceImageKey({
      name: selectedBooking.service,
      service: selectedBooking.service,
      slug: selectedBooking.service,
    }))
    : null;
  const bookingServiceImage = bookingService?.image || fallbackServiceImage?.src || "";
  const bookingServiceIcon = bookingService?.icon || fallbackServiceImage?.icon || "bi bi-house-gear";
  const bookingServiceName = bookingService?.name
    || String(selectedBooking?.service || "Home service").replaceAll("_", " ");
  const bookingGroups = [
    {
      title: "Active bookings",
      bookings: bookings.filter((booking) => (
        ["PENDING", "ACCEPTED", "CONFIRMED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS"]
          .includes((booking.status || "").toUpperCase().replaceAll(" ", "_"))
      )),
    },
    {
      title: "Completed bookings",
      bookings: bookings.filter(
        (booking) => (booking.status || "").toUpperCase() === "COMPLETED",
      ),
    },
    {
      title: "Closed bookings",
      bookings: bookings.filter((booking) => (
        ["CANCELLED", "REJECTED"].includes((booking.status || "").toUpperCase())
      )),
    },
  ];

  const viewInvoice = async (bookingId) => {
    setInvoiceMessage("");
    try {
      const response = await fetchBookingInvoice(bookingId);
      setInvoices((current) => ({ ...current, [bookingId]: response.data }));
    } catch (requestError) {
      setInvoiceMessage(requestError.response?.data?.message || "Unable to load invoice.");
    }
  };

  const downloadInvoice = async (bookingId) => {
    setInvoiceMessage("");
    setInvoiceDownloadBusy((current) => ({ ...current, [bookingId]: true }));
    try {
      const response = await downloadBookingInvoice(bookingId);
      const url = URL.createObjectURL(response.data);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${invoices[bookingId]?.invoiceNumber || `homefix-${bookingId}`}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
      setInvoiceMessage("Invoice PDF downloaded.");
    } catch (requestError) {
      setInvoiceMessage(requestError.response?.data?.message || "Unable to download invoice PDF.");
    } finally {
      setInvoiceDownloadBusy((current) => ({ ...current, [bookingId]: false }));
    }
  };

  const loadPaymentHistory = async (bookingId) => {
    setPaymentHistoryError("");
    setPaymentHistoryLoading((current) => ({ ...current, [bookingId]: true }));
    try {
      const response = await fetchBookingPaymentHistory(bookingId);
      setPaymentHistory((current) => ({ ...current, [bookingId]: response.data }));
    } catch (requestError) {
      setPaymentHistoryError(
        requestError.response?.data?.message || "Unable to load payment and refund history.",
      );
    } finally {
      setPaymentHistoryLoading((current) => ({ ...current, [bookingId]: false }));
    }
  };

  const submitComplaint = async (event) => {
    event.preventDefault();
    setComplaintBusy(true);
    setComplaintMessage("");
    try {
      await submitSupportTicket({
        bookingId: complaint.bookingId ? Number(complaint.bookingId) : null,
        subject: complaint.subject,
        description: complaint.description,
      });
      setComplaint({ bookingId: "", subject: "", description: "" });
      setComplaintMessage("Your support ticket was submitted.");
    } catch (requestError) {
      setComplaintMessage(requestError.response?.data?.message || "Unable to submit support ticket.");
    } finally {
      setComplaintBusy(false);
    }
  };

  const loadPriceHistory = async (bookingId) => {
    setPriceHistoryMessage("");
    try {
      const response = await fetchBookingPriceHistory(bookingId);
      setPriceHistory((current) => ({ ...current, [bookingId]: response.data }));
    } catch (requestError) {
      setPriceHistoryMessage(requestError.response?.data?.message || "Unable to load price history.");
    }
  };

  const cancelBooking = async (bookingId) => {
    setCancellationMessage("");
    const result = await onCancel(bookingId);
    if (result?.success) {
      setConfirmCancellationId(null);
    } else {
      setCancellationMessage(
        result?.message || "Cancellation failed. Refresh the booking and try again.",
      );
    }
  };

  return (
    <main className={`dashboard my-bookings-page${bookingId ? " booking-detail-page" : ""}`}>
      <div className="bookings-page-heading">
        <div>
          <span className="eyebrow">YOUR HOMEFIX ACTIVITY</span>
          <h1>{selectedBooking ? "Booking details" : "My Bookings"}</h1>
          <p>{selectedBooking
            ? "Review this service request, its progress, and available actions."
            : "Track each service request and open its dedicated details page."}</p>
        </div>
        <div className="bookings-page-actions">
          <button
            type="button"
            className="refresh-bookings-button"
            onClick={onRefresh}
            disabled={loading}
          >
            <i className={`bi ${loading ? "bi-arrow-repeat spin-icon" : "bi-arrow-clockwise"}`} aria-hidden="true"></i>
            {loading ? "Refreshing..." : "Refresh"}
          </button>
          <Link className="secondary-link-button" to="/customer">
            <i className="bi bi-grid" aria-hidden="true" /> Browse Services
          </Link>
        </div>
      </div>

      {error && (
        <div className="bookings-error" role="alert">
          <span>{error}</span>
          <button type="button" onClick={onRefresh} disabled={loading}>Try again</button>
        </div>
      )}
      {cancellationMessage && (
        <p className="bookings-error" role="alert">{cancellationMessage}</p>
      )}

      {!bookingId && (
        <section className="customer-booking-groups" aria-label="Your bookings">
          {loading && bookings.length === 0 ? (
            <section className="booking-list-skeleton" aria-label="Loading bookings">
              {[0, 1, 2].map((item) => (
                <div className="booking-skeleton-card" key={item} aria-hidden="true">
                  <span /><span /><span />
                </div>
              ))}
            </section>
          ) : bookings.length === 0 ? (
            <section className="custom-card no-bookings booking-empty-state">
              <span className="booking-empty-icon" aria-hidden="true">
                <i className="bi bi-calendar2-check" />
              </span>
              <h2>No bookings yet</h2>
              <p>Your service requests will appear here after you book a service.</p>
              <Link className="provider-primary-link" to="/customer">
                <i className="bi bi-search" aria-hidden="true" /> Browse services
              </Link>
            </section>
          ) : bookingGroups.map((group) => group.bookings.length > 0 && (
            <section className="custom-card customer-booking-group" key={group.title}>
              <div className="customer-booking-group-heading">
                <h2>{group.title}</h2>
                <span>{group.bookings.length}</span>
              </div>
              <div className="customer-booking-card-grid">
                {group.bookings.map((booking) => (
                  <article className="customer-booking-summary" key={booking.id}>
                    <div className="customer-booking-summary-top">
                      <span>{booking.bookingId || `Booking ${booking.id}`}</span>
                      <span className={`booking-status status-${(booking.status || "pending").toLowerCase().replaceAll("_", "-")}`}>
                        {(booking.status || "PENDING").replaceAll("_", " ")}
                      </span>
                    </div>
                    <h3>{booking.service || "Home service"}</h3>
                    <p>Booked by {booking.customerName || "you"}</p>
                    <dl>
                      <div><dt>Appointment</dt><dd>{booking.preferredDate || "Date not set"}{booking.preferredAt ? ` · ${String(booking.preferredAt).slice(11, 16)}` : ""}</dd></div>
                      <div><dt>Estimated price</dt><dd>₹{Number(booking.price || 0).toFixed(2)}</dd></div>
                    </dl>
                    <Link className="provider-primary-link" to={`/customer/bookings/${booking.id}`}>
                      View booking details <i className="bi bi-arrow-right" aria-hidden="true"></i>
                    </Link>
                  </article>
                ))}
              </div>
            </section>
          ))}
        </section>
      )}
      {bookingId && (
        selectedBooking ? (
          <div className="customer-booking-detail">
            <Link className="secondary-link-button booking-detail-back" to="/customer/bookings">
              <i className="bi bi-arrow-left" aria-hidden="true"></i> Back to my bookings
            </Link>
            <section className="custom-card customer-booking-hero">
              <div className="customer-booking-hero-artwork">
                <img
                  src={bookingServiceImage}
                  alt={`${bookingServiceName} service`}
                  onError={(event) => {
                    event.currentTarget.hidden = true;
                  }}
                />
                <i className={bookingServiceIcon} aria-hidden="true" />
              </div>
              <div className="customer-booking-hero-copy">
                <div className="customer-booking-detail-heading">
                  <span className="eyebrow">BOOKING DETAILS</span>
                  <span className={`booking-status status-${bookingStatus.toLowerCase().replaceAll("_", "-")}`}>
                    {bookingStatus.replaceAll("_", " ")}
                  </span>
                </div>
                <h2>{bookingServiceName}</h2>
                <span className="customer-booking-reference">
                  <i className="bi bi-hash" aria-hidden="true" />
                  {selectedBooking.bookingId || `Booking ${selectedBooking.id}`}
                </span>
              </div>
            </section>
            <BookingStatusTimeline status={bookingStatus} />
            <div className="customer-booking-detail-grid">
              <div className="customer-booking-detail-main">
                <section className="custom-card booking-information-card">
                  <div className="booking-information-heading">
                    <span className="booking-information-icon"><i className="bi bi-calendar2-week" aria-hidden="true" /></span>
                    <div><h2>Appointment</h2><p>Scheduled service visit</p></div>
                  </div>
                  <div className="booking-information-value">
                    <strong>{selectedBooking.preferredDate || "Date not set"}</strong>
                    {selectedBooking.preferredAt && (
                      <span>
                        <i className="bi bi-clock" aria-hidden="true" />
                        {String(selectedBooking.preferredAt).slice(11, 16)}
                      </span>
                    )}
                  </div>
                </section>
                <section className="custom-card booking-information-card">
                  <div className="booking-information-heading">
                    <span className="booking-information-icon"><i className="bi bi-geo-alt" aria-hidden="true" /></span>
                    <div><h2>Service address</h2><p>Where we’ll provide the service</p></div>
                  </div>
                  <p className="booking-information-address">
                    {selectedBooking.address || "No service address was provided."}
                  </p>
                </section>
              </div>
              <aside className="customer-booking-detail-aside">
                <section className="custom-card booking-information-card booking-provider-card">
                  <div className="booking-information-heading">
                    <span className="booking-information-icon"><i className="bi bi-person-check" aria-hidden="true" /></span>
                    <div><h2>Service provider</h2><p>Assigned professional</p></div>
                  </div>
                  {selectedBooking.provider || selectedBooking.providerName ? (
                    <>
                      <strong className="booking-provider-name">
                        {selectedBooking.provider?.fullName || selectedBooking.providerName}
                      </strong>
                      {selectedBooking.providerPhone && (
                        <a
                          className="provider-contact-phone"
                          href={`tel:${selectedBooking.providerPhone}`}
                          aria-label={`Call provider · ${selectedBooking.providerPhone}`}
                        >
                          <i className="bi bi-telephone" aria-hidden="true" />
                          {selectedBooking.providerPhone}
                        </a>
                      )}
                      {selectedBooking.providerExperience && (
                        <span className="booking-provider-meta">
                          {selectedBooking.providerExperience.replaceAll("_", " ").toLowerCase()} experience
                        </span>
                      )}
                      {selectedBooking.providerServiceArea && (
                        <span className="booking-provider-meta">Service area: {selectedBooking.providerServiceArea}</span>
                      )}
                      {selectedBooking.providerProfessionalSummary && (
                        <p className="booking-provider-summary">{selectedBooking.providerProfessionalSummary}</p>
                      )}
                      {selectedBooking.provider && ["ACCEPTED", "CONFIRMED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS"]
                        .includes((selectedBooking.status || "").toUpperCase().replaceAll(" ", "_")) && (
                        <Link className="booking-chat-open-link" to={`/customer/bookings/${selectedBooking.id}/chat`}>
                          <i className="bi bi-chat-dots-fill" aria-hidden="true" />
                          Open chat with {selectedBooking.provider?.fullName || selectedBooking.providerName}
                        </Link>
                      )}
                    </>
                  ) : (
                    <p className="booking-provider-pending">
                      {bookingStatus === "PENDING"
                        ? "We’re finding an eligible provider for your request."
                        : "Provider details are not available yet."}
                    </p>
                  )}
                </section>
                <section className="custom-card booking-information-card booking-payment-card">
                  <div className="booking-information-heading">
                    <span className="booking-information-icon"><i className="bi bi-receipt" aria-hidden="true" /></span>
                    <div><h2>Price & payment</h2><p>Current booking amounts and status</p></div>
                  </div>
                  {selectedBooking.price != null && (
                    <div className="booking-price-total">
                      <span>Current total</span>
                      <strong>₹{Number(selectedBooking.price).toFixed(2)}</strong>
                    </div>
                  )}
                  {(selectedBooking.paymentMethod || selectedBooking.paymentStatus) && (
                    <div className="booking-payment-status">
                      {selectedBooking.paymentMethod && <span>{selectedBooking.paymentMethod}</span>}
                      {selectedBooking.paymentStatus && <strong>{selectedBooking.paymentStatus.replaceAll("_", " ")}</strong>}
                    </div>
                  )}
                  {selectedBooking.paymentMethod === "CASH" && selectedBooking.paymentCollectedAmount != null && (
                    <p className="booking-provider-meta">
                      Cash received: ₹{Number(selectedBooking.paymentCollectedAmount).toFixed(2)}
                    </p>
                  )}
                  {selectedBooking.serviceSubtotal != null && (
                    <dl className="booking-price-breakdown">
                      <div><dt>Subtotal</dt><dd>₹{Number(selectedBooking.serviceSubtotal).toFixed(2)}</dd></div>
                      {selectedBooking.discountAmount != null && (
                        <div><dt>Discount</dt><dd>−₹{Number(selectedBooking.discountAmount).toFixed(2)}</dd></div>
                      )}
                      {selectedBooking.taxAmount != null && (
                        <div><dt>Tax</dt><dd>₹{Number(selectedBooking.taxAmount).toFixed(2)}</dd></div>
                      )}
                    </dl>
                  )}
                  {selectedBooking.paymentMethod === "UPI" && (
                    <div className="booking-payment-tools">
                      <button type="button" className="admin-secondary-button"
                        onClick={() => loadPaymentHistory(selectedBooking.id)}
                        disabled={paymentHistoryLoading[selectedBooking.id]}>
                        {paymentHistoryLoading[selectedBooking.id] ? "Loading history…" : "View payment and refund history"}
                      </button>
                      {(paymentHistory[selectedBooking.id] || []).map((transaction) => (
                        <article key={transaction.transactionId} className="payment-history-entry">
                          <strong>Payment {transaction.status}</strong>
                          <span>₹{Number(transaction.amount).toFixed(2)} · {transaction.createdAt}</span>
                          {transaction.gatewayPaymentId && <small>Gateway reference: {transaction.gatewayPaymentId}</small>}
                          {(transaction.refunds || []).map((refund) => (
                            <small key={refund.refundId}>
                              Refund ₹{Number(refund.amount).toFixed(2)} · {refund.status}
                              {refund.gatewayRefundId ? ` · ${refund.gatewayRefundId}` : ""}
                              {refund.reason ? ` · ${refund.reason}` : ""}
                            </small>
                          ))}
                        </article>
                      ))}
                      {selectedBooking.paymentStatus === "PAID" && (
                        <span className="payment-confirmed-note">Payment verified by Razorpay</span>
                      )}
                      {selectedBooking.paymentStatus !== "PAID" && selectedBooking.status === "COMPLETED" && (
                        <RazorpayCheckoutButton
                          bookingId={selectedBooking.id}
                          bookingCode={selectedBooking.bookingId ?? selectedBooking.id}
                          amount={selectedBooking.price}
                          onRefresh={onRefresh}
                        />
                      )}
                      {selectedBooking.paymentStatus !== "PAID" && selectedBooking.status !== "COMPLETED" && (
                        <small>Secure checkout is available after service completion.</small>
                      )}
                    </div>
                  )}
                </section>
              </aside>
            </div>
          </div>
        ) : loading ? (
          <section className="booking-detail-skeleton" aria-label="Loading booking details">
            <span /><span /><span />
          </section>
        ) : (
          <section className="custom-card conversation-unavailable">
            <span className="booking-empty-icon" aria-hidden="true"><i className="bi bi-search" /></span>
            <h2>Booking not found</h2>
            <p>This booking is not available in your account.</p>
            <Link className="secondary-link-button" to="/customer/bookings">Back to my bookings</Link>
          </section>
        )
      )}
      {bookingId && selectedBooking && <section className="custom-card customer-booking-actions booking-detail-actions">
        <h2>Booking actions</h2>
        {[selectedBooking].map((booking) => {
          const status = (booking.status || "").toUpperCase();
          const draft = reviewDrafts[booking.id] || { rating: 5, comment: "" };
          return (
            <article className="customer-booking-action" key={`action-${booking.id}`}>
              <strong>{booking.bookingId || `Booking ${booking.id}`} · {booking.service}</strong>
              {booking.pendingPriceProposalId != null && (
                <div className="price-proposal-card">
                  <h3>Provider requests an extra charge</h3>
                  <p>Current total: ₹{booking.price} · New total if approved: <strong>₹{booking.proposedPrice}</strong></p>
                  <p>{booking.priceProposalExplanation}</p>
                  <div className="booking-page-actions">
                    <button type="button" className="accept-button" disabled={actionLoading}
                      onClick={() => onPriceProposalDecision(
                        booking.id, booking.pendingPriceProposalId, "APPROVED",
                      )}>
                      Approve extra charge
                    </button>
                    <button type="button" className="reject-button" disabled={actionLoading}
                      onClick={() => onPriceProposalDecision(
                        booking.id, booking.pendingPriceProposalId, "REJECTED",
                      )}>
                      Reject extra charge
                    </button>
                  </div>
                  <small>The booking total and invoice change only after you approve this charge.</small>
                </div>
              )}
              <button type="button" className="admin-secondary-button"
                onClick={() => loadPriceHistory(booking.id)}>
                View price history
              </button>
              {priceHistory[booking.id] && (
                <ol className="price-history-list">
                  {priceHistory[booking.id].map((record) => (
                    <li key={record.id}>
                      <strong>{record.status.replaceAll("_", " ")} · ₹{record.proposedTotal}</strong>
                      <span>{record.explanation}</span>
                      <small>
                        Base ₹{record.baseAmount}; experience ×{record.experienceMultiplier};
                        weekend ₹{record.weekendSurcharge}; urgent ₹{record.urgentSurcharge};
                        late-night ₹{record.lateNightSurcharge}; additional ₹{record.additionalAmount}
                      </small>
                    </li>
                  ))}
                </ol>
              )}
              {booking.completionPin && (
                <>
                  <span className="booking-start-pin-inline">
                    Job start PIN: <strong>{booking.completionPin}</strong> · Share it with your provider when they arrive.
                  </span>
                  {onResetCompletionPin && ["PENDING", "ACCEPTED", "CONFIRMED", "ON_THE_WAY"].includes(status) && (
                    <button
                      type="button"
                      className="reset-pin-button"
                      disabled={loading}
                      onClick={() => onResetCompletionPin(booking.id)}
                    >
                      Generate a new PIN
                    </button>
                  )}
                </>
              )}
              {["PENDING", "ACCEPTED", "CONFIRMED", "ON_THE_WAY", "ARRIVED"]
                .includes(status.replace(/[\s-]+/g, "_")) && (
                <div className="booking-cancel-control">
                  {confirmCancellationId !== booking.id ? (
                    <button type="button" className="reject-button"
                      disabled={actionLoading}
                      onClick={() => {
                        setCancellationMessage("");
                        setConfirmCancellationId(booking.id);
                      }}>
                      Cancel before job starts
                    </button>
                  ) : (
                    <div className="booking-cancel-confirm" role="group"
                      aria-label="Confirm booking cancellation">
                      <span>Cancel this booking before the provider starts work?</span>
                      <button type="button" className="reject-button"
                        disabled={actionLoading}
                        onClick={() => cancelBooking(booking.id)}>
                        {actionLoading ? "Cancelling…" : "Yes, cancel booking"}
                      </button>
                      <button type="button" className="admin-secondary-button"
                        onClick={() => setConfirmCancellationId(null)}>
                        Keep booking
                      </button>
                    </div>
                  )}
                </div>
              )}
              {status === "COMPLETED" && (
                <>
                  <form onSubmit={(event) => {
                    event.preventDefault();
                    onReview(booking.id, draft.rating, draft.comment);
                  }}>
                    <label>
                      Rating
                      <select value={draft.rating} onChange={(event) => setReviewDrafts((current) => ({
                        ...current,
                        [booking.id]: { ...draft, rating: Number(event.target.value) },
                      }))}>
                        {[5, 4, 3, 2, 1].map((rating) => <option key={rating} value={rating}>{rating} / 5</option>)}
                      </select>
                    </label>
                    <label>
                      Review
                      <input maxLength={2000} value={draft.comment} onChange={(event) => setReviewDrafts((current) => ({
                        ...current,
                        [booking.id]: { ...draft, comment: event.target.value },
                      }))} placeholder="Share your experience" />
                    </label>
                    <button type="submit">Submit review</button>
                  </form>
                  <div>
                    <button type="button" onClick={() => viewInvoice(booking.id)}>View invoice</button>
                    <button
                      type="button"
                      disabled={invoiceDownloadBusy[booking.id]}
                      onClick={() => downloadInvoice(booking.id)}
                    >
                      {invoiceDownloadBusy[booking.id] ? "Preparing PDF..." : "Download PDF invoice"}
                    </button>
                    {invoices[booking.id] && (
                      <div className="invoice-preview">
                        <strong>Invoice {invoices[booking.id].invoiceNumber}</strong>
                        <span>Subtotal ₹{Number(invoices[booking.id].subtotal).toFixed(2)}</span>
                        <span>Approved additional charges ₹{Number(invoices[booking.id].approvedAdditionalCharges || 0).toFixed(2)}</span>
                        {(invoices[booking.id].approvedChargeItems || []).map((charge) => (
                          <span key={charge.id}>
                            {charge.explanation}: ₹{Number(charge.amount).toFixed(2)}
                          </span>
                        ))}
                        <span>Discount −₹{Number(invoices[booking.id].discount || 0).toFixed(2)}</span>
                        <span>Tax ₹{Number(invoices[booking.id].tax).toFixed(2)}</span>
                        <strong>Final payable ₹{Number(invoices[booking.id].total).toFixed(2)}</strong>
                        <span>Payment: {invoices[booking.id].paymentStatus}</span>
                      </div>
                    )}
                  </div>
                </>
              )}
            </article>
          );
        })}
      </section>}
      {invoiceMessage && <p role="status">{invoiceMessage}</p>}
      {paymentHistoryError && <p role="alert">{paymentHistoryError}</p>}
      {priceHistoryMessage && <p role="alert">{priceHistoryMessage}</p>}
      <section className={`custom-card customer-booking-actions${bookingId ? " booking-support-card" : ""}`}>
        <h2>Contact support</h2>
        <form className="service-form" onSubmit={submitComplaint}>
          <label className="form-field">Related booking (optional)
            <select className="form-select" value={complaint.bookingId}
              onChange={(event) => setComplaint({ ...complaint, bookingId: event.target.value })}>
              <option value="">General support request</option>
              {bookings.map((booking) => (
                <option key={booking.id} value={booking.id}>{booking.bookingId || booking.id} · {booking.service}</option>
              ))}
            </select>
          </label>
          <label className="form-field">Subject
            <input className="form-control" required maxLength={160} value={complaint.subject}
              onChange={(event) => setComplaint({ ...complaint, subject: event.target.value })} />
          </label>
          <label className="form-field form-field-wide">Details
            <textarea className="form-control" required maxLength={4000} rows="4" value={complaint.description}
              onChange={(event) => setComplaint({ ...complaint, description: event.target.value })} />
          </label>
          <button type="submit" disabled={complaintBusy}>Submit support ticket</button>
        </form>
        {complaintMessage && <p role="status">{complaintMessage}</p>}
      </section>
    </main>
  );
}
