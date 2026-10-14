import React from "react";

function ServiceStats({ stats }) {
  const cards = [
    {
      title: "Total Customers",
      value: stats.totalCustomers,
      icon: "bi-people",
      color: "primary",
      description: "Registered customers"
    },
    {
      title: "Active Providers",
      value: stats.activeProviders,
      icon: "bi-person-check",
      color: "success",
      description: "Service providers"
    },
    {
      title: "Total Bookings",
      value: stats.totalBookings,
      icon: "bi-calendar-check",
      color: "warning",
      description: "Service requests"
    },
    {
      title: "Pending Requests",
      value: stats.pendingRequests,
      icon: "bi-hourglass-split",
      color: "danger",
      description: "Awaiting processing"
    }
  ];

  return (
    <div className="row g-3 mb-4">
      {cards.map((card) => (
        <div
          className="col-12 col-sm-6 col-xl-3"
          key={card.title}
        >
          <div className="stat-box h-100 p-3">
            <div className="d-flex justify-content-between align-items-start">
              <div>
                <p className="text-secondary small fw-semibold mb-2">
                  {card.title}
                </p>

                <h3 className="fw-bold mb-2">
                  {card.value ?? 0}
                </h3>

                <span className="small-muted">
                  {card.description}
                </span>
              </div>

              <div
                className={`rounded-3 p-3 bg-${card.color}-subtle`}
              >
                <i
                  className={`bi ${card.icon} text-${card.color} metric-icon`}
                  aria-hidden="true"
                />
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export default ServiceStats;