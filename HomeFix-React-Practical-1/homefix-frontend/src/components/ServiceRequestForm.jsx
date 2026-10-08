import React, { useState } from 'react';

function ServiceRequestForm({ handleRequest }) {
  const [formData, setFormData] = useState({
    customerName: '',
    service: '',
    address: '',
    preferredDate: '',
    notes: ''
  });

  const [busy, setBusy] = useState(false);

  const onInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const onFormSubmit = async (e) => {
    e.preventDefault();
    setBusy(true);

    try {
      await handleRequest(formData);
      setFormData({
        customerName: '',
        service: '',
        address: '',
        preferredDate: '',
        notes: ''
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="custom-card shadow-sm p-3">
      <h6 className="fw-bold text-dark mb-3">
        <i className="bi bi-wrench-adjustable-circle me-2 text-primary"></i>
        Request Home Service
      </h6>

      <form onSubmit={onFormSubmit}>
        <div className="mb-2">
          <label className="form-label small text-muted">Customer Name</label>
          <input
            type="text"
            name="customerName"
            className="form-control form-control-sm"
            value={formData.customerName}
            onChange={onInputChange}
            placeholder="Enter full name"
            required
          />
        </div>

        <div className="mb-2">
          <label className="form-label small text-muted">Service</label>
          <select
            name="service"
            className="form-select form-select-sm"
            value={formData.service}
            onChange={onInputChange}
            required
          >
            <option value="">Select service...</option>
            <option value="Plumbing">Plumbing</option>
            <option value="Electrical">Electrical</option>
            <option value="Cleaning">Cleaning</option>
            <option value="AC Repair">AC Repair</option>
            <option value="Appliance Repair">Appliance Repair</option>
          </select>
        </div>

        <div className="mb-2">
          <label className="form-label small text-muted">Address</label>
          <textarea
            name="address"
            className="form-control form-control-sm"
            rows="2"
            value={formData.address}
            onChange={onInputChange}
            placeholder="Enter service address"
            required
          />
        </div>

        <div className="mb-2">
          <label className="form-label small text-muted">Preferred Date</label>
          <input
            type="date"
            name="preferredDate"
            className="form-control form-control-sm"
            value={formData.preferredDate}
            onChange={onInputChange}
            required
          />
        </div>

        <div className="mb-3">
          <label className="form-label small text-muted">Remarks</label>
          <input
            type="text"
            name="notes"
            className="form-control form-control-sm"
            value={formData.notes}
            onChange={onInputChange}
            placeholder="Describe the problem"
          />
        </div>

        <button type="submit" className="btn btn-primary btn-sm w-100 fw-bold" disabled={busy}>
          {busy ? (
            <>
              <span className="spinner-border spinner-border-sm me-2"></span>
              Submitting...
            </>
          ) : (
            <>
              <i className="bi bi-send me-1"></i>Request Service
            </>
          )}
        </button>
      </form>
    </div>
  );
}

export default ServiceRequestForm;