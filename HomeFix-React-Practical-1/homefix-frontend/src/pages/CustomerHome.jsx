import React from "react";
import { Link } from "react-router-dom";

export default function CustomerHome({
  user,
  services,
  servicesLoading,
  servicesError,
  onRetryServices,
}) {
  return (
    <main className="dashboard customer-dashboard">
      <section className="welcome-banner">
        <div className="welcome-copy">
          <span className="eyebrow">YOUR HOME, IN GOOD HANDS</span>
          <h1>Welcome back, {(user.fullName || "there").trim().split(/\s+/)[0]}!</h1>
          <p>
            Trusted home services are just a few steps away. Choose what you
            need and book a convenient time.
          </p>
          <Link className="welcome-bookings-link" to="/customer/bookings">
            <i className="bi bi-calendar2-check" aria-hidden="true"></i>
            View My Bookings
          </Link>
        </div>
        <div className="welcome-illustration" aria-hidden="true">
          <i className="bi bi-house-gear-fill"></i>
        </div>
      </section>

      <section className="home-services" aria-labelledby="home-services-title">
        <div className="section-heading">
          <div>
            <span className="eyebrow">HELP AROUND THE HOME</span>
            <h2 id="home-services-title">What do you need help with?</h2>
            <p>Choose a service to see details and schedule your booking.</p>
          </div>
          <Link className="text-link" to="/customer/bookings">My Bookings</Link>
        </div>

        {servicesError && (
          <div className="bookings-error" role="alert">
            <span>{servicesError}</span>
            <button type="button" onClick={onRetryServices} disabled={servicesLoading}>
              Try again
            </button>
          </div>
        )}

        {servicesLoading ? (
          <p role="status">Loading services and current prices...</p>
        ) : services.length === 0 ? (
          <div className="no-bookings">
            <p>No services are available right now. Please try again later.</p>
          </div>
        ) : (
        <div className="home-service-grid">
          {services.map((service) => (
            <article className="home-service-card" key={service.slug}>
              <span className="home-service-icon" aria-hidden="true">
                <i className={service.icon}></i>
              </span>
              <h3>{service.name}</h3>
              <p>{service.shortDescription}</p>
              <div className="home-service-card-footer">
                <span>Starting from <strong>₹{service.startingPrice}</strong></span>
                <Link
                  className="service-book-link"
                  to={`/customer/book/${service.slug}`}
                  aria-label={`Book ${service.name}`}
                >
                  Book Now <i className="bi bi-arrow-right" aria-hidden="true"></i>
                </Link>
              </div>
            </article>
          ))}
        </div>
        )}
      </section>

      <aside className="customer-account-summary">
        <span className="customer-account-icon" aria-hidden="true">
          <i className="bi bi-person-circle"></i>
        </span>
        <div>
          <span className="eyebrow">YOUR ACCOUNT</span>
          <strong>{user.fullName || "HomeFix customer"}</strong>
          <span>{user.email}</span>
        </div>
      </aside>
    </main>
  );
}
