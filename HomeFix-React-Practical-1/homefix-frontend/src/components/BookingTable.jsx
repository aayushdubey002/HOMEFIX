import React from 'react';

function BookingTable({ list, isLoading, refreshData }) {
  return (
    <div className="custom-card shadow-sm p-3 mb-4">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h6 className="fw-bold text-dark mb-0">
          <i className="bi bi-tools me-2 text-primary"></i>
          Recent Service Bookings
        </h6>
        <button className="btn btn-outline-secondary btn-sm" onClick={refreshData}>
          <i className="bi bi-arrow-clockwise me-1"></i>Refresh
        </button>
      </div>

      <div className="table-responsive">
        <table className="table table-hover align-middle mb-0">
          <thead className="table-light small">
            <tr>
              <th>Booking ID</th>
              <th>Customer</th>
              <th>Service</th>
              <th>Provider</th>
              <th>Status</th>
            </tr>
          </thead>

          <tbody className="small">
            {isLoading ? (
              <tr>
                <td colSpan="5" className="text-center py-4">
                  <div className="spinner-border spinner-border-sm text-primary me-2"></div>
                  Loading bookings...
                </td>
              </tr>
            ) : list.length === 0 ? (
              <tr>
                <td colSpan="5" className="text-center py-3 text-muted">
                  No bookings available.
                </td>
              </tr>
            ) : (
              list.map((item) => (
                <tr key={item.id}>
                  <td><code>{item.id}</code></td>
                  <td className="fw-semibold">{item.customerName}</td>
                  <td>{item.service}</td>
                  <td>{item.providerName}</td>
                  <td>
                    <span className={`badge ${
                      item.status === 'COMPLETED'
                        ? 'bg-success'
                        : item.status === 'PENDING'
                        ? 'bg-warning text-dark'
                        : 'bg-primary'
                    }`}>
                      {item.status}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default BookingTable;