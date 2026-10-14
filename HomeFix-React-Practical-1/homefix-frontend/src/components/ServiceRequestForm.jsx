import React, { useRef, useState } from "react";

function localDateString(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function ServiceRequestForm({ service, user, onSubmit }) {
  const [formData, setFormData] = useState({
    customerName: user.fullName || "",
    service: service.requestService,
    phone: user.phone || "",
    address: "",
    preferredDate: "",
    notes: "",
  });
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState("");
  const submissionInProgress = useRef(false);

  const onInputChange = (event) => {
    const { name, value } = event.target;
    setFormData((previous) => ({ ...previous, [name]: value }));
    if (status) setStatus("");
  };

  const onFormSubmit = async (event) => {
    event.preventDefault();
    if (submissionInProgress.current) return;

    if (formData.preferredDate < localDateString()) {
      setStatus("Choose today or a future preferred date.");
      return;
    }

    submissionInProgress.current = true;
    setBusy(true);
    setStatus("");
    try {
      await onSubmit({
        ...formData,
        customerName: formData.customerName.trim(),
        address: formData.address.trim(),
        notes: formData.notes.trim(),
        price: service.startingPrice,
      });
    } catch (error) {
      setStatus(
        error.response?.data?.message
          || error.message
          || "Unable to save your booking. Please try again."
      );
    } finally {
      submissionInProgress.current = false;
      setBusy(false);
    }
  };

  return (
    <section className="custom-card service-form-card" aria-labelledby="booking-form-title">
      <div className="service-form-header">
        <div className="service-form-icon" aria-hidden="true">
          <i className="bi bi-calendar2-check"></i>
        </div>
        <div>
          <span className="eyebrow">SIMPLE, SECURE BOOKING</span>
          <h2 id="booking-form-title">Book {service.name}</h2>
          <p className="small-muted">Tell us what you need and when it works for you.</p>
        </div>
      </div>

      <form onSubmit={onFormSubmit} className="service-form">
        <div className="form-field">
          <label htmlFor="customerName" className="form-label">Customer name</label>
          <input
            id="customerName"
            type="text"
            name="customerName"
            className="form-control"
            value={formData.customerName}
            onChange={onInputChange}
            placeholder="Enter your full name"
            autoComplete="name"
            maxLength={120}
            required
          />
        </div>

        <div className="form-field">
          <label htmlFor="phone" className="form-label">Phone number</label>
          <input
            id="phone"
            type="tel"
            name="phone"
            className="form-control"
            value={formData.phone}
            onChange={onInputChange}
            placeholder="Enter your 10-digit phone number"
            autoComplete="tel"
            inputMode="numeric"
            pattern="[0-9]{10}"
            maxLength={10}
            title="Enter a 10-digit phone number"
            required
          />
        </div>

        <div className="form-field form-field-wide">
          <label htmlFor="address" className="form-label">Service address</label>
          <textarea
            id="address"
            name="address"
            className="form-control"
            rows="2"
            value={formData.address}
            onChange={onInputChange}
            placeholder="Enter the complete address for the service"
            autoComplete="street-address"
            maxLength={500}
            required
          />
        </div>

        <div className="form-field">
          <label htmlFor="preferredDate" className="form-label">Preferred date</label>
          <input
            id="preferredDate"
            type="date"
            name="preferredDate"
            className="form-control"
            value={formData.preferredDate}
            onChange={onInputChange}
            min={localDateString()}
            required
          />
        </div>

        <div className="form-field form-field-wide">
          <label htmlFor="notes" className="form-label">
            Describe the problem <span className="text-secondary">(Optional)</span>
          </label>
          <textarea
            id="notes"
            name="notes"
            className="form-control"
            rows="3"
            value={formData.notes}
            onChange={onInputChange}
            placeholder="Share any helpful details about the work needed"
            maxLength={2000}
          />
        </div>

        <div className="starting-price" role="status">
          <span className="price-icon" aria-hidden="true"><i className="bi bi-tag"></i></span>
          <span><small>{service.name} starting from</small><strong>₹{service.startingPrice}</strong></span>
          <small className="price-note">The server confirms the saved booking price.</small>
        </div>

        {status && <p className="booking-form-error" role="alert">{status}</p>}

        <button type="submit" className="service-submit-btn" disabled={busy}>
          {busy ? (
            <><span className="spinner-border spinner-border-sm" aria-hidden="true"></span> Saving booking...</>
          ) : (
            <><i className="bi bi-check2-circle" aria-hidden="true"></i> Confirm Booking</>
          )}
        </button>

        <p className="form-privacy-note">
          <i className="bi bi-shield-check me-1"></i>
          Your details are only used to arrange your service.
        </p>
      </form>
    </section>
  );
}

export default ServiceRequestForm;
