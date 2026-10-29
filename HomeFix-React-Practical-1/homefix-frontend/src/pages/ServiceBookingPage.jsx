import React from "react";
import { Link, Navigate, useLocation, useParams } from "react-router-dom";
import ServiceRequestForm from "../components/ServiceRequestForm";
import { findService } from "../services/serviceCatalog";

export default function ServiceBookingPage({
  user,
  services,
  servicesLoading,
  servicesError,
  onRetryServices,
  onSubmit,
}) {
  const { serviceSlug } = useParams();
  const location = useLocation();
  const service = findService(serviceSlug, services);

  if (servicesLoading) {
    return <main className="dashboard"><p role="status">Loading service details...</p></main>;
  }

  if (servicesError) {
    return (
      <main className="dashboard">
        <div className="bookings-error" role="alert">
          <span>{servicesError}</span>
          <button type="button" onClick={onRetryServices} disabled={servicesLoading}>Try again</button>
        </div>
      </main>
    );
  }

  if (!service) return <Navigate to="/customer" replace />;

  return (
    <main className="dashboard service-booking-page">
      <Link className="back-link" to="/customer">
        <i className="bi bi-arrow-left" aria-hidden="true"></i> All services
      </Link>

      <section className="service-detail-banner">
        <span className="service-detail-icon">
          <img
            src={service.image}
            alt={`${service.name} service`}
            onError={(event) => {
              event.currentTarget.hidden = true;
            }}
          />
          <i className={service.icon} aria-hidden="true"></i>
        </span>
        <div>
          <span className="eyebrow">HOMEFIX SERVICE</span>
          <h1>{service.name}</h1>
          <p>{service.description}</p>
        </div>
        <div className="service-detail-price">
          <small>Starting from</small>
          <strong>₹{service.startingPrice}</strong>
        </div>
      </section>

      <ServiceRequestForm
        key={service.slug}
        service={service}
        user={user}
        savedAddress={location.state?.savedAddress}
        onSubmit={onSubmit}
      />
    </main>
  );
}
