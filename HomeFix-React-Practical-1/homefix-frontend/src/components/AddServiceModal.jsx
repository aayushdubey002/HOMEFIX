import React, { useState } from 'react';

function AddServiceModal({ show, onHide, saveService }) {
  const [serviceInfo, setServiceInfo] = useState({
    serviceName: '',
    providerName: '',
    phone: '',
    serviceArea: '',
    status: 'active'
  });

  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!show) return null;

  const handleChange = (e) => {
    const { name, value } = e.target;

    setServiceInfo((prev) => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);

    try {
      await saveService(serviceInfo);
      setServiceInfo({
        serviceName: '',
        providerName: '',
        phone: '',
        serviceArea: '',
        status: 'active'
      });
      onHide();
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="modal show d-block"
      tabIndex="-1"
      style={{ backgroundColor: 'rgba(0,0,0,.5)' }}
    >
      <div className="modal-dialog modal-dialog-centered">
        <div className="modal-content shadow border-0">
          <div className="modal-header bg-dark text-white py-2">
            <h6 className="modal-title fw-bold">
              <i className="bi bi-person-plus me-2 text-warning"></i>
              Add Service Provider
            </h6>
            <button
              type="button"
              className="btn-close btn-close-white"
              onClick={onHide}
            ></button>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="modal-body p-3">
              <div className="mb-2">
                <label className="form-label small fw-semibold">Service</label>
                <select
                  name="serviceName"
                  className="form-select form-select-sm"
                  value={serviceInfo.serviceName}
                  onChange={handleChange}
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
                <label className="form-label small fw-semibold">Provider Name</label>
                <input
                  type="text"
                  name="providerName"
                  className="form-control form-control-sm"
                  value={serviceInfo.providerName}
                  onChange={handleChange}
                  placeholder="Full name"
                  required
                />
              </div>

              <div className="mb-2">
                <label className="form-label small fw-semibold">Phone Number</label>
                <input
                  type="tel"
                  name="phone"
                  className="form-control form-control-sm"
                  value={serviceInfo.phone}
                  onChange={handleChange}
                  placeholder="10-digit number"
                  required
                />
              </div>

              <div className="mb-2">
                <label className="form-label small fw-semibold">Service Area</label>
                <input
                  type="text"
                  name="serviceArea"
                  className="form-control form-control-sm"
                  value={serviceInfo.serviceArea}
                  onChange={handleChange}
                  placeholder="Area / location"
                  required
                />
              </div>

              <div className="mb-3">
                <label className="form-label small fw-semibold">Status</label>
                <select
                  name="status"
                  className="form-select form-select-sm"
                  value={serviceInfo.status}
                  onChange={handleChange}
                >
                  <option value="active">Active</option>
                  <option value="inactive">Inactive</option>
                </select>
              </div>
            </div>

            <div className="modal-footer d-flex justify-content-between py-2 px-3">
              <button type="button" className="btn btn-outline-secondary btn-sm" onClick={onHide}>
                Cancel
              </button>
              <button type="submit" className="btn btn-warning btn-sm fw-bold" disabled={isSubmitting}>
                {isSubmitting ? 'Saving...' : 'Save Provider'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

export default AddServiceModal;
