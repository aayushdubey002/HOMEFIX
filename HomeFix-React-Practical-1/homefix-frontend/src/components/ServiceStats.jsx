import React from 'react';

function ServiceStats({ stats }) {
  return (
    <div className="row g-3 mb-4">
      <div className="col-12 col-sm-6 col-md-3">
        <div className="p-3 stat-box border-start border-primary border-4">
          <div className="d-flex justify-content-between align-items-center">
            <div>
              <span className="text-secondary small fw-bold text-uppercase">
                Total Customers
              </span>
              <h4 className="fw-bold mt-1 mb-0">{stats.totalCustomers}</h4>
            </div>
            <i className="bi bi-people text-primary metric-icon"></i>
          </div>
        </div>
      </div>

      <div className="col-12 col-sm-6 col-md-3">
        <div className="p-3 stat-box border-start border-success border-4">
          <div className="d-flex justify-content-between align-items-center">
            <div>
              <span className="text-secondary small fw-bold text-uppercase">
                Active Providers
              </span>
              <h4 className="fw-bold mt-1 mb-0">{stats.activeProviders}</h4>
            </div>
            <i className="bi bi-person-check text-success metric-icon"></i>
          </div>
        </div>
      </div>

      <div className="col-12 col-sm-6 col-md-3">
        <div className="p-3 stat-box border-start border-warning border-4">
          <div className="d-flex justify-content-between align-items-center">
            <div>
              <span className="text-secondary small fw-bold text-uppercase">
                Total Bookings
              </span>
              <h4 className="fw-bold mt-1 mb-0">{stats.totalBookings}</h4>
            </div>
            <i className="bi bi-calendar-check text-warning metric-icon"></i>
          </div>
        </div>
      </div>

      <div className="col-12 col-sm-6 col-md-3">
        <div className="p-3 stat-box border-start border-danger border-4">
          <div className="d-flex justify-content-between align-items-center">
            <div>
              <span className="text-secondary small fw-bold text-uppercase">
                Pending Requests
              </span>
              <h4 className="fw-bold mt-1 mb-0">{stats.pendingRequests}</h4>
            </div>
            <i className="bi bi-hourglass-split text-danger metric-icon"></i>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ServiceStats;