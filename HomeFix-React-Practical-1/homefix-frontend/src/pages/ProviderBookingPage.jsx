import React, { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import BookingStatusTimeline from "../components/BookingStatusTimeline";
import {
  fetchPaymentConfiguration,
  fetchBookingProblemImage,
} from "../services/apiService";

const normalizeStatus = (status) => (status || "").toUpperCase().replaceAll(" ", "_");

export default function ProviderBookingPage({
  view = "overview",
  bookings,
  user,
  loading,
  actionLoading,
  onAccept,
  onReject,
  onUpdateStatus,
  onProposePrice,
  onCancel,
  onRefresh,
}) {
  const { bookingId } = useParams();
  const navigate = useNavigate();
  const booking = bookings.find((item) => String(item.id) === bookingId);
  const assignedToCurrentProvider = Number(booking?.provider?.id) === Number(user?.id);
  const status = normalizeStatus(booking?.status);
  const isOpenRequest = Boolean(booking && status === "PENDING" && !booking.provider);
  const requestIssueMessages = {
    OVERLAPPING_JOB: "This appointment overlaps another assigned job, so it cannot be accepted. Dismiss this request and have the customer submit it for a different time.",
    OUTSIDE_AVAILABILITY: "This appointment is outside your saved working hours. Update your availability or dismiss the request.",
    INVALID_APPOINTMENT: "This request has an invalid appointment time and cannot be accepted. Refresh the request or dismiss it.",
  };
  const requestIssueMessage = requestIssueMessages[booking?.providerRequestIssue];
  const canViewBooking = assignedToCurrentProvider || isOpenRequest;
  const chatAvailable = ["ACCEPTED", "CONFIRMED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS"]
    .includes(status);
  const [completionPin, setCompletionPin] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("CASH");
  const [cashReceivedAmount, setCashReceivedAmount] = useState("");
  const [upiEnabled, setUpiEnabled] = useState(false);
  const [paymentConfigurationMessage, setPaymentConfigurationMessage] = useState("");
  const [maximumExtraCharge, setMaximumExtraCharge] = useState(null);
  const [problemImageUrl, setProblemImageUrl] = useState("");
  const [problemImageStatus, setProblemImageStatus] = useState("idle");
  const [problemImageError, setProblemImageError] = useState("");
  const [problemImageRetry, setProblemImageRetry] = useState(0);
  const [problemPhotoReviewed, setProblemPhotoReviewed] = useState(false);
  const [priceAmount, setPriceAmount] = useState("");
  const [extraChargeMode, setExtraChargeMode] = useState("NONE");
  const [extraChargeType, setExtraChargeType] = useState("");
  const [extraChargeDetails, setExtraChargeDetails] = useState("");
  const [pageNotice, setPageNotice] = useState("");
  const [pageError, setPageError] = useState("");
  const [confirmCancellation, setConfirmCancellation] = useState(false);

  useEffect(() => {
    let active = true;
    fetchPaymentConfiguration()
      .then(({ data }) => {
        if (active) {
          setUpiEnabled(Boolean(data?.upiEnabled));
          setPaymentConfigurationMessage(data?.configurationMessage || "");
          setMaximumExtraCharge(data?.maximumProviderAdditionalCharge == null
            ? null
            : Number(data.maximumProviderAdditionalCharge));
        }
      })
      .catch(() => {
        if (active) {
          setPaymentConfigurationMessage("UPI availability could not be verified. Cash is available.");
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!booking || !canViewBooking) {
      setProblemImageUrl("");
      setProblemImageStatus("idle");
      setProblemImageError("");
      return undefined;
    }
    if (booking.problemImageAvailable === false) {
      setProblemImageUrl("");
      setProblemImageStatus("unavailable");
      setProblemImageError("");
      return undefined;
    }

    let active = true;
    let objectUrl;
    setProblemImageStatus("loading");
    setProblemImageError("");
    fetchBookingProblemImage(booking.id)
      .then(({ data }) => {
        if (!(data instanceof Blob) || !data.type.startsWith("image/")) {
          throw new Error("The protected problem-photo endpoint did not return an image.");
        }
        objectUrl = URL.createObjectURL(data);
        if (active) {
          setProblemImageUrl(objectUrl);
          setProblemImageStatus("loaded");
        }
      })
      .catch((error) => {
        if (active) {
          setProblemImageUrl("");
          const notFound = error.response?.status === 404;
          setProblemImageStatus(notFound ? "unavailable" : "error");
          setProblemImageError(notFound
            ? "No customer photo is available for this booking."
            : error.response?.status === 401
              ? "Your session expired. Sign in again to view the customer photo."
              : error.response?.status === 403
                ? "Your account cannot access this booking photo."
                : "The customer photo could not be loaded. Check the backend connection and retry.");
        }
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [booking?.id, booking?.problemImageAvailable, canViewBooking, problemImageRetry]);

  useEffect(() => {
    setProblemPhotoReviewed(false);
  }, [booking?.id]);

  useEffect(() => {
    if (booking) {
      setPaymentMethod(booking.paymentMethod === "UPI" && upiEnabled ? "UPI" : "CASH");
      setCashReceivedAmount(String(booking.paymentCollectedAmount ?? booking.price ?? ""));
    }
  }, [booking?.id, booking?.paymentMethod, booking?.paymentCollectedAmount, booking?.price, upiEnabled]);

  if (!booking && loading) {
    return <main className="dashboard"><section className="custom-card"><p>Loading job details…</p></section></main>;
  }
  if (!booking || !canViewBooking) {
    return (
      <main className="dashboard provider-job-page">
        <Link className="secondary-link-button" to="/provider/requests">Back to requests</Link>
        <section className="custom-card conversation-unavailable">
          <i className="bi bi-shield-lock" aria-hidden="true"></i>
          <h1>Job not found</h1>
          <p>This job is unavailable or has not been assigned to your account.</p>
          <button type="button" className="accept-button" onClick={onRefresh}>Refresh requests</button>
        </section>
      </main>
    );
  }

  const progressAction = {
    ACCEPTED: { next: "CONFIRMED", label: "Confirm this service request" },
    CONFIRMED: { next: "ON_THE_WAY", label: "I’m on the way" },
    ON_THE_WAY: { next: "ARRIVED", label: "I have arrived" },
  }[status];

  const handleProgress = async (nextStatus) => {
    setPageError("");
    const result = await onUpdateStatus(booking.id, nextStatus);
    if (result?.success === false) setPageError(result.message || "Unable to update job progress.");
  };

  const handleStartWork = async (event) => {
    event.preventDefault();
    setPageError("");
    const result = await onUpdateStatus(booking.id, "IN_PROGRESS", {
      completionPin,
    });
    if (result?.success === false) setPageError(result.message || "The PIN could not be verified.");
    else {
      setCompletionPin("");
      navigate(`/provider/bookings/${booking.id}`);
    }
  };

  const handleCompleteWork = async (event) => {
    event.preventDefault();
    setPageError("");
    setPageNotice("");
    if (extraChargeMode === "ADD") {
      if (extraChargeLimitUnavailable) {
        setPageError("The extra-charge limit could not be loaded. Refresh the page before requesting a charge.");
        return;
      }
      if (extraChargeAmount > maximumExtraCharge) {
        setPageError(`The extra charge cannot exceed ₹${maximumExtraCharge.toFixed(2)}.`);
        return;
      }
      if (!onProposePrice) {
        setPageError("Extra-charge approval is unavailable. Refresh and try again.");
        return;
      }
      const explanation = `${extraChargeType.trim()}: ${extraChargeDetails.trim()}`;
      const result = await onProposePrice(booking.id, priceAmount, explanation);
      if (result?.success === false) {
        setPageError(result.message || "Unable to send the extra charge for approval.");
      } else {
        setExtraChargeMode("NONE");
        setExtraChargeType("");
        setExtraChargeDetails("");
        setPriceAmount("");
        setPageNotice("Charge sent to the customer. Completion is available after they approve or reject it.");
      }
      return;
    }

    const details = {
      paymentMethod,
      cashReceivedAmount: paymentMethod === "CASH" ? Number(cashReceivedAmount) : null,
    };
    const result = await onUpdateStatus(booking.id, "COMPLETED", details);
    if (result?.success === false) setPageError(result.message || "Unable to complete this service.");
    else navigate(`/provider/bookings/${booking.id}`);
  };

  const canCancelBeforePin = ["ACCEPTED", "CONFIRMED", "ON_THE_WAY", "ARRIVED"]
    .includes(status);
  const hasPendingExtraCharge = booking.pendingPriceProposalId != null || booking.proposedPrice != null;
  const extraChargeAmount = Number(priceAmount);
  const extraChargeLimitUnavailable = maximumExtraCharge == null
    || !Number.isFinite(maximumExtraCharge);
  const extraChargeExceedsLimit = extraChargeMode === "ADD"
    && !extraChargeLimitUnavailable
    && extraChargeAmount > maximumExtraCharge;
  const cancelBooking = async () => {
    setPageError("");
    const result = await onCancel?.(booking.id);
    if (result?.success) {
      navigate("/provider/requests");
    } else {
      setPageError(result?.message || "This booking could not be cancelled. Refresh and try again.");
      setConfirmCancellation(false);
    }
  };
  const cancellationControl = canCancelBeforePin && (
    <div className="booking-cancel-control">
      {!confirmCancellation ? (
        <button
          type="button"
          className="reject-button"
          disabled={actionLoading}
          onClick={() => setConfirmCancellation(true)}
        >
          Cancel before job starts
        </button>
      ) : (
        <div className="booking-cancel-confirm" role="group" aria-label="Confirm booking cancellation">
          <span>Cancel this booking? The customer will be notified.</span>
          <button type="button" className="reject-button" disabled={actionLoading}
            onClick={cancelBooking}>
            {actionLoading ? "Cancelling…" : "Yes, cancel booking"}
          </button>
          <button type="button" className="admin-secondary-button"
            onClick={() => setConfirmCancellation(false)}>
            Keep booking
          </button>
        </div>
      )}
    </div>
  );

  if (isOpenRequest) {
    const photoReviewed = booking.problemImageAvailable === false
      || (problemImageStatus === "loaded" && problemPhotoReviewed);
    return (
      <main className="dashboard provider-job-page provider-request-detail-page">
        <div className="provider-job-toolbar">
          <Link className="secondary-link-button" to="/provider/requests">
            <i className="bi bi-arrow-left" aria-hidden="true"></i> Back to requests
          </Link>
        </div>
        <section className="provider-job-header custom-card">
          <div>
            <span className="eyebrow">NEW SERVICE REQUEST</span>
            <h1>{booking.service || "Home service"}</h1>
            <p>Booking {booking.bookingId || booking.id} · {booking.preferredDate || "Date not set"}</p>
          </div>
          <span className="booking-status status-pending">PENDING</span>
        </section>
        <div className="provider-job-grid">
          <section className="custom-card provider-job-details">
            <div className="provider-job-section-heading">
              <span className="eyebrow">CUSTOMER REQUEST</span>
              <h2>Service details</h2>
            </div>
            <dl>
              <div><dt>Customer</dt><dd>{booking.customerName || "Customer"}</dd></div>
              <div><dt>Appointment</dt><dd>{booking.preferredDate || "Date not set"}{booking.preferredAt ? ` · ${String(booking.preferredAt).slice(11, 16)}` : ""}</dd></div>
              <div><dt>Address</dt><dd>{booking.address || "Not provided"}</dd></div>
              <div><dt>Estimated price</dt><dd>₹{Number(booking.price || 0).toFixed(2)}</dd></div>
            </dl>
            {requestIssueMessage && (
              <p className="provider-request-availability-warning" role="status">
                <i className="bi bi-calendar-x" aria-hidden="true"></i>
                {requestIssueMessage}
              </p>
            )}
            {booking.notes && (
              <div className="provider-job-problem">
                <h3>Problem described by customer</h3>
                <p>{booking.notes}</p>
              </div>
            )}
          </section>
          <section className="custom-card provider-job-details">
            <div className="provider-job-section-heading">
              <span className="eyebrow">PHOTO REVIEW</span>
              <h2>Customer problem photo</h2>
            </div>
            {problemImageStatus === "loading" && (
              <p className="provider-job-photo-status" role="status">Loading customer photo…</p>
            )}
            {problemImageStatus === "loaded" && problemImageUrl && (
              <>
                <a href={problemImageUrl} target="_blank" rel="noreferrer" className="provider-job-photo">
                  <img src={problemImageUrl} alt="Customer’s service problem" />
                  <span>Open customer photo</span>
                </a>
                <label className="provider-photo-review">
                  <input type="checkbox" checked={problemPhotoReviewed}
                    onChange={(event) => setProblemPhotoReviewed(event.target.checked)} />
                  I have reviewed the customer’s problem photo
                </label>
              </>
            )}
            {problemImageStatus === "unavailable" && (
              <p className="provider-job-photo-status">No customer photo is available for this request.</p>
            )}
            {problemImageStatus === "error" && (
              <div className="provider-job-photo-error" role="alert">
                <span>{problemImageError || "The customer photo could not be loaded."}</span>
                <button type="button" className="admin-secondary-button"
                  onClick={() => setProblemImageRetry((attempt) => attempt + 1)}>
                  Retry photo
                </button>
              </div>
            )}
            <div className="provider-request-detail-actions">
              <button type="button" className="accept-button"
                disabled={actionLoading || !photoReviewed || Boolean(requestIssueMessage)}
                onClick={() => onAccept?.(booking.id)}>
                {actionLoading ? "Accepting…" : requestIssueMessage ? "Unavailable at this time" : "Accept this request"}
              </button>
              <button type="button" className="reject-button" disabled={actionLoading}
                onClick={async () => {
                  const dismissed = await onReject?.(booking.id);
                  if (dismissed) navigate("/provider/requests");
                }}>
                {actionLoading ? "Please wait…" : "Dismiss request"}
              </button>
            </div>
            {!photoReviewed && booking.problemImageAvailable !== false && (
              <small className="provider-photo-review-hint">
                Load and review the customer photo before accepting this request.
              </small>
            )}
            {pageError && <p className="bookings-error" role="alert">{pageError}</p>}
          </section>
        </div>
      </main>
    );
  }

  if (view === "start" || view === "payment") {
    const isStartScreen = view === "start";
    const canOpenScreen = isStartScreen ? status === "ARRIVED" : status === "IN_PROGRESS";
    return (
      <main className="dashboard provider-job-page provider-job-workflow-page">
        <div className="provider-job-toolbar">
          <button type="button" className="secondary-link-button"
            onClick={() => navigate(`/provider/bookings/${booking.id}`)}>
            <i className="bi bi-arrow-left" aria-hidden="true"></i> Back to job
          </button>
          {chatAvailable && (
            <Link className="booking-chat-open-link" to={`/provider/bookings/${booking.id}/chat`}>
              <i className="bi bi-chat-dots-fill" aria-hidden="true"></i> Customer chat
            </Link>
          )}
        </div>
        <section className="custom-card provider-workflow-card">
          <span className="eyebrow">BOOKING {booking.bookingId || booking.id}</span>
          <h1>{isStartScreen ? "Verify job-start PIN" : "Record payment & complete service"}</h1>
          <p>{booking.service} · {booking.customerName || "Customer"}</p>
          <BookingStatusTimeline status={booking.status} />
          {cancellationControl}
          {!canOpenScreen ? (
            <div className="conversation-unavailable">
              <i className="bi bi-info-circle" aria-hidden="true"></i>
              <p>
                {isStartScreen
                  ? "PIN verification is available after you mark that you have arrived."
                  : "Payment recording is available after the job has started."}
              </p>
              <button type="button" className="accept-button"
                onClick={() => navigate(`/provider/bookings/${booking.id}`)}>
                Return to job steps
              </button>
            </div>
          ) : isStartScreen ? (
            <form className="provider-job-form provider-workflow-form" onSubmit={handleStartWork}>
              <div className="provider-job-step-icon"><i className="bi bi-shield-lock" aria-hidden="true"></i></div>
              <h2>Ask the customer for their six-digit PIN</h2>
              <p>This verifies the right customer before service work begins.</p>
              <label>Customer job-start PIN
                <input type="text" inputMode="numeric" pattern="[0-9]{6}" maxLength={6}
                  autoComplete="one-time-code" value={completionPin}
                  onChange={(event) => setCompletionPin(event.target.value.replace(/\D/g, "").slice(0, 6))}
                  placeholder="Enter 6-digit PIN" required autoFocus />
              </label>
              <button type="submit" className="accept-button"
                disabled={actionLoading || !/^\d{6}$/.test(completionPin)}>
                {actionLoading ? "Verifying…" : "Verify PIN & start work"}
              </button>
            </form>
          ) : (
            <form className="provider-job-form provider-workflow-form" onSubmit={handleCompleteWork}>
              <div className="provider-job-step-icon"><i className="bi bi-wallet2" aria-hidden="true"></i></div>
              <h2>Confirm charges and payment</h2>
              <p>Final payable amount: <strong>₹{Number(booking.price || 0).toFixed(2)}</strong></p>
              {hasPendingExtraCharge ? (
                <p className="provider-extra-charge-pending" role="status">
                  An extra charge is awaiting the customer’s decision. You can complete the service after they approve or reject it.
                </p>
              ) : (
                <>
                  <label>Extra charge
                    <select value={extraChargeMode} onChange={(event) => setExtraChargeMode(event.target.value)}
                      disabled={actionLoading}>
                      <option value="NONE">No extra charge</option>
                      <option value="ADD">Add an extra charge</option>
                    </select>
                  </label>
                  {extraChargeMode === "ADD" && (
                    <>
                      <label>Charge type
                        <input type="text" maxLength={80} required value={extraChargeType}
                          onChange={(event) => setExtraChargeType(event.target.value)}
                          placeholder="Tools, materials, replacement part" />
                      </label>
                      <label>Additional amount (₹)
                        <input type="text" inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?"
                          maxLength={11} required
                          value={priceAmount}
                          onChange={(event) => setPriceAmount(event.target.value)}
                          placeholder="Enter the extra amount" />
                      </label>
                      {extraChargeLimitUnavailable ? (
                        <p className="provider-extra-charge-pending" role="status">
                          The allowed charge limit is loading. Refresh if it does not appear.
                        </p>
                      ) : (
                        <p className="payment-disclaimer">
                          Maximum extra charge: ₹{maximumExtraCharge.toFixed(2)}
                        </p>
                      )}
                      {extraChargeExceedsLimit && (
                        <p className="provider-extra-charge-error" role="alert">
                          This amount is above the allowed maximum of ₹{maximumExtraCharge.toFixed(2)}. Lower the amount or contact the administrator.
                        </p>
                      )}
                      <label>Why is this charge needed?
                        <textarea maxLength={400} required value={extraChargeDetails}
                          onChange={(event) => setExtraChargeDetails(event.target.value)}
                          placeholder="Explain the additional tools or materials required" />
                      </label>
                      <p className="payment-disclaimer">
                        The customer must approve this charge before it is added to the booking total and invoice.
                      </p>
                    </>
                  )}
                </>
              )}
              {extraChargeMode === "NONE" && (
                <>
                  <label>Payment method
                    <select value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value)}
                      disabled={actionLoading || booking.paymentStatus === "PAID"}>
                      <option value="CASH">Cash</option>
                      <option value="UPI" disabled={!upiEnabled}>UPI through HomeFix checkout{upiEnabled ? "" : " (unavailable)"}</option>
                    </select>
                  </label>
                  {paymentMethod === "CASH" ? (
                    <label>Cash received (₹)
                      <input type="number" min={booking.price || "0.01"} step="0.01"
                        value={cashReceivedAmount} onChange={(event) => setCashReceivedAmount(event.target.value)}
                        disabled={actionLoading || booking.paymentStatus === "PAID"} required autoFocus />
                    </label>
                  ) : (
                    <p className="payment-disclaimer">
                      The customer completes UPI through HomeFix checkout. Payment is not marked paid manually.
                    </p>
                  )}
                  {!upiEnabled && paymentConfigurationMessage && (
                    <small className="payment-disclaimer">{paymentConfigurationMessage}</small>
                  )}
                </>
              )}
              <button type="submit" className="accept-button"
                disabled={actionLoading || hasPendingExtraCharge
                  || (extraChargeMode === "ADD"
                    ? extraChargeLimitUnavailable || extraChargeExceedsLimit
                      || !extraChargeType.trim() || !extraChargeDetails.trim()
                      || !Number.isFinite(extraChargeAmount) || extraChargeAmount <= 0
                    : paymentMethod === "CASH"
                      && Number(cashReceivedAmount) < Number(booking.price || 0))}>
                {actionLoading
                  ? "Saving…"
                  : hasPendingExtraCharge
                    ? "Waiting for customer decision"
                    : extraChargeMode === "ADD"
                      ? "Send charge for customer approval"
                      : "Complete service"}
              </button>
            </form>
          )}
          {pageError && <p className="bookings-error" role="alert">{pageError}</p>}
          {pageNotice && <p className="provider-extra-charge-pending" role="status">{pageNotice}</p>}
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard provider-job-page">
      <div className="provider-job-toolbar">
        <Link className="secondary-link-button" to="/provider/requests">
          <i className="bi bi-arrow-left" aria-hidden="true"></i> All requests
        </Link>
        <Link className="booking-chat-open-link" to={`/provider/bookings/${booking.id}/chat`}>
          <i className="bi bi-chat-dots-fill" aria-hidden="true"></i> Open customer chat
        </Link>
      </div>
      <section className="provider-job-header custom-card">
        <div>
          <span className="eyebrow">PROVIDER JOB WORKSPACE</span>
          <h1>{booking.service || "Home service"}</h1>
          <p>Booking {booking.bookingId || booking.id} · {booking.preferredDate || "Date not set"}</p>
        </div>
        <span className={`booking-status status-${status.toLowerCase()}`}>{status.replaceAll("_", " ")}</span>
      </section>

      <div className="provider-job-grid">
        <section className="custom-card provider-job-details">
          <div className="provider-job-section-heading">
            <span className="eyebrow">CUSTOMER REQUEST</span>
            <h2>Service details</h2>
          </div>
          <dl>
            <div><dt>Customer</dt><dd>{booking.customerName || "Customer"}</dd></div>
            <div><dt>Contact</dt><dd><a href={`tel:${booking.phone}`}>{booking.phone || "Not provided"}</a></dd></div>
            <div><dt>Address</dt><dd>{booking.address || "Not provided"}</dd></div>
            <div><dt>Appointment</dt><dd>{booking.preferredDate || "Not set"}{booking.preferredAt ? ` · ${String(booking.preferredAt).slice(11, 16)}` : ""}</dd></div>
            <div><dt>Estimated amount</dt><dd>₹{Number(booking.price || 0).toFixed(2)}</dd></div>
          </dl>
          {booking.notes && (
            <div className="provider-job-problem">
              <h3>Problem described by customer</h3>
              <p>{booking.notes}</p>
            </div>
          )}
          {problemImageStatus === "loading" && (
            <p className="provider-job-photo-status" role="status">Loading customer photo…</p>
          )}
          {problemImageStatus === "loaded" && problemImageUrl && (
            <a href={problemImageUrl} target="_blank" rel="noreferrer" className="provider-job-photo">
              <img src={problemImageUrl} alt="Customer’s service problem" />
              <span>Open customer photo</span>
            </a>
          )}
          {problemImageStatus === "unavailable" && (
            <p className="provider-job-photo-status">No photo was added to this booking.</p>
          )}
          {problemImageStatus === "error" && (
            <div className="provider-job-photo-error" role="alert">
              <span>{problemImageError || "The customer photo could not be loaded."}</span>
              <button type="button" className="admin-secondary-button"
                onClick={() => setProblemImageRetry((attempt) => attempt + 1)}>
                Retry photo
              </button>
            </div>
          )}
        </section>

        <section className="custom-card provider-job-progress">
          <div className="provider-job-section-heading">
            <span className="eyebrow">JOB PROGRESS</span>
            <h2>Service steps</h2>
          </div>
          <BookingStatusTimeline status={booking.status} />
          {progressAction && (
            <div className="provider-job-action-card">
              <span className="provider-job-step-icon"><i className="bi bi-check2-circle" aria-hidden="true"></i></span>
              <div>
                <h3>{progressAction.label}</h3>
                <p>Update the customer when you are ready for this next step.</p>
              </div>
              <button type="button" className="accept-button" disabled={actionLoading}
                onClick={() => handleProgress(progressAction.next)}>
                {actionLoading ? "Saving…" : progressAction.label}
              </button>
            </div>
          )}
          {status === "ON_THE_WAY" && (
            <div className="provider-job-action-card">
              <span className="provider-job-step-icon"><i className="bi bi-geo-alt" aria-hidden="true"></i></span>
              <div><h3>Confirm your arrival</h3><p>Mark arrived before asking the customer to share the job-start PIN.</p></div>
              <button type="button" className="accept-button" disabled={actionLoading}
                onClick={() => handleProgress("ARRIVED")}>Mark arrived</button>
            </div>
          )}
          {status === "ARRIVED" && (
            <div className="provider-job-action-card">
              <span className="provider-job-step-icon"><i className="bi bi-shield-lock" aria-hidden="true"></i></span>
              <div>
                <h3>Start service safely</h3>
                <p>Verify the customer’s six-digit job-start PIN on the next screen.</p>
              </div>
              <button type="button" className="accept-button"
                onClick={() => navigate(`/provider/bookings/${booking.id}/start`)}>
                Open PIN verification
              </button>
            </div>
          )}
          {status === "IN_PROGRESS" && (
            <div className="provider-job-action-card">
              <span className="provider-job-step-icon"><i className="bi bi-wallet2" aria-hidden="true"></i></span>
              <div>
                <h3>Record payment when work is complete</h3>
                <p>Payable amount: <strong>₹{Number(booking.price || 0).toFixed(2)}</strong> · {booking.paymentStatus || "UNPAID"}</p>
              </div>
              <button type="button" className="accept-button"
                onClick={() => navigate(`/provider/bookings/${booking.id}/payment`)}>
                Open payment recording
              </button>
            </div>
          )}
          {status === "COMPLETED" && (
            <div className="provider-job-completed" role="status">
              <i className="bi bi-check-circle-fill" aria-hidden="true"></i>
              <div><strong>Service completed</strong><span>Payment status: {booking.paymentStatus || "UNPAID"}</span></div>
            </div>
          )}
          {cancellationControl}
          {(booking.pendingPriceProposalId != null || booking.proposedPrice != null) && (
            <p className="price-proposal-card">
              Extra charge awaiting customer approval. Service completion is paused.
            </p>
          )}
        </section>
      </div>
      {pageError && <p className="bookings-error" role="alert">{pageError}</p>}
      {booking.providerProfessionalSummary && (
        <section className="custom-card provider-job-bio">
          <h2>Your provider profile visible to customer</h2>
          <p>{booking.providerProfessionalSummary}</p>
        </section>
      )}
      <button type="button" className="admin-secondary-button" onClick={() => {
        onRefresh();
        navigate(`/provider/bookings/${booking.id}`);
      }}>Refresh job details</button>
    </main>
  );
}
