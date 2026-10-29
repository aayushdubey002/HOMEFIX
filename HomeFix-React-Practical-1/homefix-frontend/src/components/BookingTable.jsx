import React, { useEffect, useState } from "react";
import { fetchBookingProblemImage } from "../services/apiService";
import { Link } from "react-router-dom";

function BookingTable({
  bookings = [],
  user,
  onAccept,
  onReject,
  onUpdateStatus,
  onProposePrice,
  upiEnabled = false,
  paymentConfigurationMessage = "",
  loading = false,
  actionLoading = false,
  summaryOnly = false,
}) {
  const [completionPins, setCompletionPins] = useState({});
  const [completionPaymentDrafts, setCompletionPaymentDrafts] = useState({});
  const [extraChargeDrafts, setExtraChargeDrafts] = useState({});
  const [completionFeedback, setCompletionFeedback] = useState({});
  const [completionSubmitting, setCompletionSubmitting] = useState({});
  const [requestStatusFilter, setRequestStatusFilter] = useState("ALL");
  const [requestSearch, setRequestSearch] = useState("");
  const [problemImageStates, setProblemImageStates] = useState({});
  const [problemImageRetries, setProblemImageRetries] = useState({});
  const [reviewedPhotoIds, setReviewedPhotoIds] = useState({});
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

  const problemImageStatus = (booking) => (
    booking.problemImageAvailable === false
      ? "unavailable"
      : problemImageStates[booking.id]?.status || "loading"
  );

  const canAcceptAfterPhotoReview = (booking) => (
    !canAccept(booking)
    || booking.problemImageAvailable === false
    || (problemImageStates[booking.id]?.status === "loaded" && reviewedPhotoIds[booking.id])
  );

  const completeService = async (booking) => {
    const draft = completionPaymentDrafts[booking.id] || {};
    const paymentMethod = draft.paymentMethod
      || (booking.paymentMethod === "UPI" && upiEnabled ? "UPI" : "CASH");
    const cashReceivedAmount = paymentMethod === "CASH"
      ? Number(draft.cashReceivedAmount
        ?? booking.paymentCollectedAmount
        ?? booking.price
        ?? 0)
      : null;

    setCompletionSubmitting((current) => ({ ...current, [booking.id]: true }));
    setCompletionFeedback((current) => ({
      ...current,
      [booking.id]: { type: "pending", message: "Saving completion and payment details..." },
    }));

    try {
      const result = await onUpdateStatus(booking.id, "COMPLETED", {
        paymentMethod,
        cashReceivedAmount,
      });
      if (result?.success === false) {
        setCompletionFeedback((current) => ({
          ...current,
          [booking.id]: {
            type: "error",
            message: result.message || "The service could not be completed. Please try again.",
          },
        }));
        return;
      }
      setCompletionFeedback((current) => ({
        ...current,
        [booking.id]: {
          type: "success",
          message: paymentMethod === "UPI"
            ? "Service completed. UPI payment is still pending customer checkout."
            : "Service completed and cash collection recorded.",
        },
      }));
    } catch (error) {
      setCompletionFeedback((current) => ({
        ...current,
        [booking.id]: {
          type: "error",
          message: error.message || "The service could not be completed. Please try again.",
        },
      }));
    } finally {
      setCompletionSubmitting((current) => ({ ...current, [booking.id]: false }));
    }
  };

  const proposeExtraCharge = async (booking) => {
    const draft = extraChargeDrafts[booking.id] || {};
    const type = String(draft.type || "").trim();
    const details = String(draft.details || "").trim();
    const amount = Number(draft.amount);
    if (!type || !details || !Number.isFinite(amount) || amount <= 0 || !onProposePrice) {
      setCompletionFeedback((current) => ({
        ...current,
        [booking.id]: {
          type: "error",
          message: "Enter the charge type, a valid amount, and why it is needed.",
        },
      }));
      return;
    }

    setCompletionSubmitting((current) => ({ ...current, [booking.id]: true }));
    setCompletionFeedback((current) => ({
      ...current,
      [booking.id]: { type: "pending", message: "Sending charge to the customer for approval..." },
    }));
    try {
      const result = await onProposePrice(booking.id, amount, `${type}: ${details}`);
      if (result?.success === false) {
        setCompletionFeedback((current) => ({
          ...current,
          [booking.id]: {
            type: "error",
            message: result.message || "The extra charge could not be sent for approval.",
          },
        }));
        return;
      }
      setCompletionFeedback((current) => ({
        ...current,
        [booking.id]: {
          type: "success",
          message: "Charge sent. Complete the service after the customer approves or rejects it.",
        },
      }));
      setExtraChargeDrafts((current) => ({
        ...current,
        [booking.id]: { mode: "NONE", type: "", amount: "", details: "" },
      }));
    } finally {
      setCompletionSubmitting((current) => ({ ...current, [booking.id]: false }));
    }
  };

  const visibleBookings = isProvider
    ? bookings.filter((booking) => {
      const status = getStatus(booking.status);
      const statusMatches = requestStatusFilter === "ALL"
        || (requestStatusFilter === "OPEN" && status === "PENDING")
        || (requestStatusFilter === "ACTIVE"
          && ["ACCEPTED", "CONFIRMED", "ON THE WAY", "ARRIVED", "IN PROGRESS"].includes(status))
        || (requestStatusFilter === "COMPLETED" && status === "COMPLETED");
      const query = requestSearch.trim().toLowerCase();
      const searchMatches = !query || [
        booking.bookingCode,
        booking.bookingId,
        booking.id,
        booking.service,
        booking.customerName,
        booking.address,
      ].some((value) => String(value || "").toLowerCase().includes(query));
      return statusMatches && searchMatches;
    })
    : bookings;

  const problemImageBookingIds = isProvider
    ? visibleBookings
      .filter((booking) => (
        booking.problemImageAvailable !== false
        && (canAccept(booking)
          || Number(booking.provider?.id) === Number(user?.id))
      ))
      .map((booking) => booking.id)
      .join(",")
    : "";
  const problemImageRetryKey = problemImageBookingIds
    .split(",")
    .filter(Boolean)
    .map((bookingId) => `${bookingId}:${problemImageRetries[bookingId] || 0}`)
    .join(",");
  const providerGroups = [
    {
      title: "New requests",
      bookings: visibleBookings.filter((booking) => getStatus(booking.status) === "PENDING"),
    },
    {
      title: "Active jobs",
      bookings: visibleBookings.filter((booking) => (
        ["ACCEPTED", "CONFIRMED", "ON THE WAY", "ARRIVED", "IN PROGRESS"]
          .includes(getStatus(booking.status))
      )),
    },
    {
      title: "Completed jobs",
      bookings: visibleBookings.filter((booking) => getStatus(booking.status) === "COMPLETED"),
    },
    {
      title: "Closed requests",
      bookings: visibleBookings.filter((booking) => (
        ["CANCELLED", "REJECTED"].includes(getStatus(booking.status))
      )),
    },
  ].filter((group) => group.bookings.length > 0);

  useEffect(() => {
    const bookingIds = problemImageBookingIds
      ? problemImageBookingIds.split(",")
      : [];
    if (bookingIds.length === 0) {
      setProblemImageStates({});
      return undefined;
    }

    let active = true;
    const objectUrls = [];
    setProblemImageStates((current) => ({
      ...current,
      ...Object.fromEntries(bookingIds.map((bookingId) => [
        bookingId,
        { status: "loading" },
      ])),
    }));
    Promise.all(bookingIds.map(async (bookingId) => {
      try {
        const response = await fetchBookingProblemImage(bookingId);
        if (!(response.data instanceof Blob) || !response.data.type.startsWith("image/")) {
          throw new Error("The problem photo response was not an image.");
        }
        const objectUrl = URL.createObjectURL(response.data);
        objectUrls.push(objectUrl);
        if (active) {
          setProblemImageStates((current) => ({
            ...current,
            [bookingId]: { status: "loaded", url: objectUrl },
          }));
        }
      } catch (error) {
        if (active) {
          setProblemImageStates((current) => ({
            ...current,
            [bookingId]: {
              status: "error",
              message: error.response?.status === 404
                ? "The photo could not be confirmed. Refresh the request before accepting."
                : "Problem photo could not be loaded. Retry before accepting this request.",
            },
          }));
        }
      }
    }));

    return () => {
      active = false;
      objectUrls.forEach((objectUrl) => URL.revokeObjectURL(objectUrl));
    };
  }, [problemImageBookingIds, problemImageRetryKey]);

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
        <>
          {isProvider && (
            <div className="provider-request-filters">
              <label>
                Find a request
                <input
                  type="search"
                  value={requestSearch}
                  onChange={(event) => setRequestSearch(event.target.value)}
                  placeholder="Booking, service, customer or address"
                />
              </label>
              <label>
                Show
                <select
                  value={requestStatusFilter}
                  onChange={(event) => setRequestStatusFilter(event.target.value)}
                >
                  <option value="ALL">All requests</option>
                  <option value="OPEN">New requests</option>
                  <option value="ACTIVE">Active jobs</option>
                  <option value="COMPLETED">Completed jobs</option>
                </select>
              </label>
              <span className="provider-request-result-count">
                {visibleBookings.length} of {bookings.length} requests
              </span>
            </div>
          )}
          {visibleBookings.length === 0 ? (
            <div className="no-bookings">
              <p>No requests match these filters. Try another status or search term.</p>
            </div>
          ) : isProvider && summaryOnly ? (
            <div className="provider-booking-groups">
              {providerGroups.map((group) => (
                <section className="provider-booking-group" key={group.title}>
                  <div className="provider-booking-group-heading">
                    <h3>{group.title}</h3>
                    <span>{group.bookings.length}</span>
                  </div>
                  <div className="provider-booking-card-grid">
                    {group.bookings.map((booking) => {
                      const imageState = problemImageStates[booking.id];
                      return (
                        <article className="provider-booking-summary" key={booking.id}>
                          <div className="provider-booking-summary-top">
                            <span>{booking.bookingCode || booking.bookingId || `Booking ${booking.id}`}</span>
                            <span className={`booking-status ${getStatusClass(booking.status)}`}>
                              {getStatus(booking.status)}
                            </span>
                          </div>
                          <h4>{booking.customerName || "Customer"}</h4>
                          <p className="provider-booking-service">{booking.service || "Home service"}</p>
                          {booking.providerRequestIssue === "OVERLAPPING_JOB" && (
                            <p className="provider-request-availability-warning" role="status">
                              <i className="bi bi-calendar-x" aria-hidden="true"></i>
                              Appointment overlaps another assigned job.
                            </p>
                          )}
                          {booking.providerRequestIssue === "OUTSIDE_AVAILABILITY" && (
                            <p className="provider-request-availability-warning" role="status">
                              <i className="bi bi-calendar-x" aria-hidden="true"></i>
                              Appointment is outside your saved working hours.
                            </p>
                          )}
                          <dl>
                            <div><dt>Appointment</dt><dd>{booking.preferredDate || "Date not set"}{booking.preferredAt ? ` · ${String(booking.preferredAt).slice(11, 16)}` : ""}</dd></div>
                            <div><dt>Estimated price</dt><dd>₹{Number(booking.price || 0).toFixed(2)}</dd></div>
                          </dl>
                          {booking.notes && (
                            <div className="provider-booking-problem">
                              <strong>Customer-described problem</strong>
                              <p>{booking.notes}</p>
                            </div>
                          )}
                          {booking.problemImageAvailable === false ? (
                            <p className="provider-booking-photo-note">No problem photo added.</p>
                          ) : imageState?.status === "loaded" ? (
                            <a className="provider-booking-photo-preview" href={imageState.url}
                              target="_blank" rel="noreferrer">
                              <img src={imageState.url} alt={`Customer photo of the ${booking.service || "service"} problem`} />
                              <span>View full photo</span>
                            </a>
                          ) : imageState?.status === "error" ? (
                            <p className="provider-booking-photo-note" role="status">{imageState.message}</p>
                          ) : booking.problemImageAvailable !== false ? (
                            <p className="provider-booking-photo-note" role="status">Loading customer photo…</p>
                          ) : null}
                          <Link className="provider-primary-link" to={`/provider/bookings/${booking.id}`}>
                            Open job workspace <i className="bi bi-arrow-right" aria-hidden="true"></i>
                          </Link>
                        </article>
                      );
                    })}
                  </div>
                </section>
              ))}
            </div>
          ) : <div className="booking-table-wrapper">
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
                {isCustomer && <th>Payment</th>}
                <th>Provider</th>
                <th>Status</th>

                {isProvider && <th>Action</th>}
              </tr>
            </thead>

            <tbody>
              {visibleBookings.map((booking) => (
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

                  {isCustomer && (
                    <td>
                      <strong>{booking.paymentMethod || "CASH"}</strong>
                      <small>{booking.paymentStatus || "UNPAID"}</small>
                    </td>
                  )}

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
                    <td className={booking.provider
                      && Number(booking.provider.id) === Number(user?.id)
                      ? "has-assigned-workspace"
                      : ""}>
                      {booking.provider
                        && Number(booking.provider.id) === Number(user?.id)
                        && getStatus(booking.status) !== "PENDING" && (
                        <Link className="provider-job-open-link" to={`/provider/bookings/${booking.id}`}>
                          <i className="bi bi-briefcase-fill" aria-hidden="true"></i>
                          Open job workspace
                          <i className="bi bi-arrow-right" aria-hidden="true"></i>
                        </Link>
                      )}
                      {(booking.notes
                        || booking.problemImageAvailable
                        || (canAccept(booking) && booking.problemImageAvailable !== false)) && (
                        <div className="provider-request-problem">
                          {booking.notes && (
                            <div>
                              <strong>Customer-described problem</strong>
                              <p>{booking.notes}</p>
                            </div>
                          )}
                          {booking.problemImageAvailable === false ? (
                            <span className="provider-problem-image-loading" role="status">
                              No customer photo was added to this request.
                            </span>
                          ) : (booking.problemImageAvailable
                            || canAccept(booking)) && (
                            problemImageStates[booking.id]?.status === "loaded" ? (
                              <a
                                className="provider-problem-image-link"
                                href={problemImageStates[booking.id].url}
                                target="_blank"
                                rel="noreferrer"
                              >
                                <img
                                  src={problemImageStates[booking.id].url}
                                  alt={`Customer photo of the ${booking.service || "service"} problem`}
                                />
                                <span>View full photo</span>
                              </a>
                            ) : problemImageStatus(booking) === "error" ? (
                              <div className="provider-problem-image-error" role="alert">
                                <span>{problemImageStates[booking.id]?.message}</span>
                                <button
                                  type="button"
                                  className="admin-secondary-button"
                                  onClick={() => setProblemImageRetries((current) => ({
                                    ...current,
                                    [booking.id]: (current[booking.id] || 0) + 1,
                                  }))}
                                >
                                  Retry photo
                                </button>
                              </div>
                            ) : (
                              <span className="provider-problem-image-loading" role="status">
                                Loading customer photo before you can accept…
                              </span>
                            )
                          )}
                          {canAccept(booking)
                            && booking.problemImageAvailable !== false
                            && problemImageStatus(booking) === "loaded" && (
                            <label className="provider-photo-review">
                              <input
                                type="checkbox"
                                checked={Boolean(reviewedPhotoIds[booking.id])}
                                onChange={(event) => setReviewedPhotoIds((current) => ({
                                  ...current,
                                  [booking.id]: event.target.checked,
                                }))}
                              />
                              I have reviewed the customer’s problem photo.
                            </label>
                          )}
                        </div>
                      )}
                      {getStatus(booking.status) === "ACCEPTED" ? (
                        <div className="booking-actions">
                          <span className="assigned-label">Accepted</span>
                          {booking.proposedPrice != null && (
                            <span className="assigned-label">
                              Extra charge awaiting customer approval. Service completion is paused.
                            </span>
                          )}
                          {onUpdateStatus && (
                            <button type="button" className="accept-button" disabled={actionLoading}
                              onClick={() => onUpdateStatus(booking.id, "CONFIRMED")}>Confirm</button>
                          )}
                        </div>
                      ) : canAccept(booking) ? (
                        <div className="booking-actions">
                          <button
                            type="button"
                            className="accept-button"
                            disabled={actionLoading || !canAcceptAfterPhotoReview(booking)}
                            title={!canAcceptAfterPhotoReview(booking)
                              ? "Load and review the customer photo before accepting."
                              : undefined}
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
                        <div className="booking-actions">
                          <span className="assigned-label">{getStatus(booking.status)}</span>
                          {(booking.pendingPriceProposalId != null || booking.proposedPrice != null) && (
                            <span className="assigned-label">
                              Extra charge awaiting customer approval. Service completion is paused.
                            </span>
                          )}
                          {getStatus(booking.status) === "IN PROGRESS" ? (
                            <div className="provider-payment-confirmation">
                              {booking.pendingPriceProposalId == null && booking.proposedPrice == null ? (
                                <>
                                  <label>
                                    Extra charge
                                    <select
                                      value={extraChargeDrafts[booking.id]?.mode || "NONE"}
                                      onChange={(event) => setExtraChargeDrafts((current) => ({
                                        ...current,
                                        [booking.id]: {
                                          ...(current[booking.id] || {}),
                                          mode: event.target.value,
                                        },
                                      }))}
                                    >
                                      <option value="NONE">No extra charge</option>
                                      <option value="ADD">Add an extra charge</option>
                                    </select>
                                  </label>
                                  {extraChargeDrafts[booking.id]?.mode === "ADD" && (
                                    <>
                                      <label>
                                        Charge type
                                        <input
                                          type="text"
                                          maxLength={80}
                                          required
                                          placeholder="Tools, materials, replacement part"
                                          value={extraChargeDrafts[booking.id]?.type || ""}
                                          onChange={(event) => setExtraChargeDrafts((current) => ({
                                            ...current,
                                            [booking.id]: {
                                              ...(current[booking.id] || {}),
                                              type: event.target.value,
                                            },
                                          }))}
                                        />
                                      </label>
                                      <label>
                                        Additional amount (₹)
                                        <input
                                          type="number"
                                          min="0.01"
                                          step="0.01"
                                          max="99999999.99"
                                          required
                                          value={extraChargeDrafts[booking.id]?.amount || ""}
                                          onChange={(event) => setExtraChargeDrafts((current) => ({
                                            ...current,
                                            [booking.id]: {
                                              ...(current[booking.id] || {}),
                                              amount: event.target.value,
                                            },
                                          }))}
                                        />
                                      </label>
                                      <label>
                                        Why is this charge needed?
                                        <textarea
                                          maxLength={400}
                                          required
                                          value={extraChargeDrafts[booking.id]?.details || ""}
                                          onChange={(event) => setExtraChargeDrafts((current) => ({
                                            ...current,
                                            [booking.id]: {
                                              ...(current[booking.id] || {}),
                                              details: event.target.value,
                                            },
                                          }))}
                                        />
                                      </label>
                                      <span className="payment-disclaimer">
                                        The customer must approve this charge before it is added to the booking total and invoice.
                                      </span>
                                    </>
                                  )}
                                </>
                              ) : (
                                <span className="assigned-label" role="status">
                                  Waiting for the customer to approve or reject the extra charge.
                                </span>
                              )}
                              <span className="assigned-label">
                                Payment: {booking.paymentStatus || "UNPAID"} · {booking.paymentMethod || "CASH"}
                              </span>
                              <label>
                                Payment collected by
                                <select
                                  value={completionPaymentDrafts[booking.id]?.paymentMethod
                                    || (booking.paymentMethod === "UPI" && upiEnabled ? "UPI" : "CASH")}
                                  disabled={actionLoading || booking.paymentStatus === "PAID"}
                                  onChange={(event) => setCompletionPaymentDrafts((current) => ({
                                    ...current,
                                    [booking.id]: {
                                      ...(current[booking.id] || {}),
                                      paymentMethod: event.target.value,
                                    },
                                  }))}
                                >
                                  <option value="CASH">Cash</option>
                                  <option value="UPI" disabled={!upiEnabled}>
                                    UPI through HomeFix checkout{upiEnabled ? "" : " (unavailable)"}
                                  </option>
                                </select>
                              </label>
                              {(completionPaymentDrafts[booking.id]?.paymentMethod
                                || (booking.paymentMethod === "UPI" && upiEnabled ? "UPI" : "CASH")) === "CASH" ? (
                                <>
                                  <label>
                                    Cash received (₹) · payable ₹{Number(booking.price || 0).toFixed(2)}
                                    <input
                                      type="number"
                                      min={booking.price || "0.01"}
                                      step="0.01"
                                      required
                                      value={completionPaymentDrafts[booking.id]?.cashReceivedAmount
                                        ?? booking.paymentCollectedAmount
                                        ?? booking.price
                                        ?? ""}
                                      disabled={actionLoading || booking.paymentStatus === "PAID"}
                                      onChange={(event) => setCompletionPaymentDrafts((current) => ({
                                        ...current,
                                        [booking.id]: {
                                          ...(current[booking.id] || {}),
                                          cashReceivedAmount: event.target.value,
                                        },
                                      }))}
                                    />
                                  </label>
                                  <span className="payment-disclaimer">
                                    The recorded cash must cover the saved payable amount. Any change due is calculated automatically.
                                  </span>
                                </>
                              ) : (
                                <span className="payment-disclaimer">
                                  Customer pays through verified HomeFix checkout after service completion. The provider cannot mark UPI as paid.
                                </span>
                              )}
                              {booking.pendingPriceProposalId == null && booking.proposedPrice == null && (
                                <button
                                  type="button"
                                  className="accept-button"
                                  disabled={
                                    actionLoading
                                    || completionSubmitting[booking.id]
                                    || (extraChargeDrafts[booking.id]?.mode === "ADD"
                                      ? (
                                        !extraChargeDrafts[booking.id]?.type?.trim()
                                        || !extraChargeDrafts[booking.id]?.details?.trim()
                                        || Number(extraChargeDrafts[booking.id]?.amount) <= 0
                                      )
                                      : (
                                        (completionPaymentDrafts[booking.id]?.paymentMethod
                                          || (booking.paymentMethod === "UPI" && upiEnabled ? "UPI" : "CASH")) === "CASH"
                                        && Number(completionPaymentDrafts[booking.id]?.cashReceivedAmount
                                          ?? booking.paymentCollectedAmount
                                          ?? booking.price
                                          ?? 0) < Number(booking.price || 0)
                                      ))
                                  }
                                  onClick={() => extraChargeDrafts[booking.id]?.mode === "ADD"
                                    ? proposeExtraCharge(booking)
                                    : completeService(booking)}
                                >
                                  {completionSubmitting[booking.id]
                                    ? "Saving…"
                                    : extraChargeDrafts[booking.id]?.mode === "ADD"
                                      ? "Send charge for customer approval"
                                      : "Complete service"}
                                </button>
                              )}
                              {completionFeedback[booking.id] && (
                                <span
                                  className={`provider-completion-feedback is-${completionFeedback[booking.id].type}`}
                                  role={completionFeedback[booking.id].type === "error" ? "alert" : "status"}
                                >
                                  {completionFeedback[booking.id].message}
                                </span>
                              )}
                              {booking.paymentCollectedAmount != null && (
                                <span className="assigned-label">
                                  Cash recorded: ₹{Number(booking.paymentCollectedAmount).toFixed(2)}
                                </span>
                              )}
                              {!upiEnabled && (
                                <span className="payment-disclaimer" role="status">
                                  {paymentConfigurationMessage || "Secure UPI checkout is unavailable. Select cash instead."}
                                </span>
                              )}
                            </div>
                          ) : ["ON THE WAY", "ARRIVED"].includes(getStatus(booking.status)) ? (
                            <div className="provider-start-pin">
                              {getStatus(booking.status) === "ON THE WAY" && (
                                <button
                                  type="button"
                                  className="accept-button"
                                  disabled={actionLoading}
                                  onClick={() => onUpdateStatus(booking.id, "ARRIVED")}
                                >
                                  Mark arrived
                                </button>
                              )}
                              <label>
                                Customer job-start PIN
                                <input
                                  type="text"
                                  inputMode="numeric"
                                  autoComplete="off"
                                  pattern="[0-9]{6}"
                                  maxLength={6}
                                  value={completionPins[booking.id] || ""}
                                  onChange={(event) => setCompletionPins((current) => ({
                                    ...current,
                                    [booking.id]: event.target.value.replace(/\D/g, "").slice(0, 6),
                                  }))}
                                  placeholder="6-digit PIN"
                                />
                              </label>
                              <button
                                type="button"
                                className="accept-button"
                                disabled={actionLoading || !/^\d{6}$/.test(completionPins[booking.id] || "")}
                                onClick={() => onUpdateStatus(booking.id, "IN_PROGRESS", {
                                  completionPin: completionPins[booking.id],
                                })}
                              >
                                Verify PIN & start job
                              </button>
                            </div>
                          ) : onUpdateStatus && ({
                            CONFIRMED: ["ON_THE_WAY", "Mark on the way"],
                          })[getStatus(booking.status)] && (
                            <button type="button" className="accept-button" disabled={actionLoading}
                              onClick={() => onUpdateStatus(
                                booking.id,
                                ({
                                  CONFIRMED: "ON_THE_WAY",
                                })[getStatus(booking.status)],
                              )}>
                              {({
                                CONFIRMED: "Mark on the way",
                              })[getStatus(booking.status)]}
                            </button>
                          )}
                        </div>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          </div>}
        </>
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