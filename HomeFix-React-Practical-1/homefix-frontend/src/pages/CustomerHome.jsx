import React, { useEffect, useMemo, useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { fetchCustomerAddresses } from "../services/apiService";

function ServiceArtwork({ service, className = "" }) {
  const [failedImage, setFailedImage] = useState("");
  const imageFailed = !service.image || failedImage === service.image;

  if (imageFailed) {
    return (
      <span className={`${className} service-artwork-fallback`} aria-hidden="true">
        <i className={service.icon} />
      </span>
    );
  }

  return (
    <img
      className={className}
      src={service.image}
      alt={`${service.name} service`}
      loading="lazy"
      decoding="async"
      onError={() => setFailedImage(service.image)}
    />
  );
}

export default function CustomerHome({
  user,
  services,
  servicesLoading,
  servicesError,
  onRetryServices,
  bookings = [],
  bookingsLoading = false,
  bookingsError = "",
}) {
  const [search, setSearch] = useState("");
  const [selectedService, setSelectedService] = useState("");
  const [addresses, setAddresses] = useState([]);
  const [selectedAddress, setSelectedAddress] = useState("");
  const [addressError, setAddressError] = useState("");
  const [unreadNotifications, setUnreadNotifications] = useState(0);

  const serviceOptions = useMemo(
    () => [...new Map(services.map((service) => [
      service.service,
      { code: service.service, name: service.categoryName, service },
    ])).values()],
    [services],
  );

  const visibleServices = useMemo(() => {
    const query = search.trim().toLowerCase();
    return services.filter((service) => {
      const matchesCategory = !selectedService || service.service === selectedService;
      const searchableText = [
        service.name,
        service.description,
        service.service,
        service.slug,
      ].join(" ").toLowerCase();
      return matchesCategory && (!query || searchableText.includes(query));
    });
  }, [search, selectedService, services]);

  useEffect(() => {
    let active = true;
    fetchCustomerAddresses()
      .then((response) => {
        if (!Array.isArray(response.data)) {
          throw new Error("Saved addresses were not returned as a list.");
        }
        if (active) {
          setAddresses(response.data);
          const primaryAddress = response.data.find((address) => address.primaryAddress)
            || response.data[0];
          setSelectedAddress(primaryAddress ? String(primaryAddress.id) : "");
          setAddressError("");
        }
      })
      .catch((error) => {
        console.error("Failed to load customer addresses:", error);
        if (active) {
          setAddressError(
            error.response?.data?.message || "Saved locations are currently unavailable."
          );
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const updateUnreadCount = (event) => {
      const count = event.detail?.unread;
      if (Number.isInteger(count) && count >= 0) setUnreadNotifications(count);
    };
    window.addEventListener("homefix:notifications:count", updateUnreadCount);
    return () => window.removeEventListener("homefix:notifications:count", updateUnreadCount);
  }, []);

  const clearFilters = () => {
    setSearch("");
    setSelectedService("");
  };

  const openNotifications = () => {
    window.dispatchEvent(new Event("homefix:notifications:open"));
  };

  const submitSearch = (event) => {
    event.preventDefault();
    document.getElementById("customer-services-grid")?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  };

  const firstName = (user.fullName || "").trim().split(/\s+/)[0] || "there";
  const selectedServiceAddress = addresses.find(
    (address) => String(address.id) === selectedAddress
  );
  const bookingState = selectedServiceAddress
    ? { savedAddress: selectedServiceAddress }
    : undefined;

  return (
    <div className="customer-dashboard-shell">
      <aside className="customer-sidebar" aria-label="Customer dashboard">
        <p className="customer-sidebar-label">YOUR HOME</p>
        <nav className="customer-sidebar-nav" aria-label="Customer dashboard navigation">
          <NavLink end to="/customer" className={({ isActive }) => `customer-sidebar-link${isActive ? " is-active" : ""}`}>
            <i className="bi bi-house-door" aria-hidden="true" />
            <span>Home</span>
          </NavLink>
          <a className="customer-sidebar-link" href="#customer-services">
            <i className="bi bi-wrench-adjustable" aria-hidden="true" />
            <span>Book a Service</span>
          </a>
          <NavLink to="/customer/bookings" className={({ isActive }) => `customer-sidebar-link${isActive ? " is-active" : ""}`}>
            <i className="bi bi-calendar2-check" aria-hidden="true" />
            <span>My Bookings</span>
          </NavLink>
          <button type="button" className="customer-sidebar-link" onClick={openNotifications}>
            <i className="bi bi-bell" aria-hidden="true" />
            <span>Notifications</span>
            {unreadNotifications > 0 && (
              <span className="customer-sidebar-count" aria-label={`${unreadNotifications} unread`}>
                {unreadNotifications}
              </span>
            )}
          </button>
          <NavLink to="/customer/profile" className={({ isActive }) => `customer-sidebar-link${isActive ? " is-active" : ""}`}>
            <i className="bi bi-person-gear" aria-hidden="true" />
            <span>Profile &amp; Addresses</span>
          </NavLink>
        </nav>

        <div className="customer-sidebar-help">
          <span className="customer-sidebar-help-icon" aria-hidden="true">
            <i className="bi bi-house-heart" />
          </span>
          <strong>Need a hand at home?</strong>
          <p>Find the right professional for your next fix.</p>
          <a href="#customer-services">Explore services <i className="bi bi-arrow-right" aria-hidden="true" /></a>
        </div>
      </aside>

      <main className="dashboard customer-dashboard">
        <section className="customer-welcome-banner" aria-labelledby="customer-welcome-title">
          <img
            className="customer-welcome-image"
            src="/images/services/home-interior.jpg"
            alt=""
            fetchPriority="high"
          />
          <div className="customer-welcome-overlay" />
          <div className="customer-welcome-copy">
            <span className="customer-welcome-eyebrow">YOUR HOME, IN GOOD HANDS</span>
            <h1 id="customer-welcome-title">Welcome back, <span>{firstName}!</span></h1>
            <p className="customer-welcome-message">Your home, in good hands.</p>
            <p className="customer-welcome-subtitle">
              Trusted home services are just a few steps away.
            </p>
            <div className="customer-trust-highlights" aria-label="HomeFix service benefits">
              <span><i className="bi bi-patch-check" aria-hidden="true" /> Approved providers</span>
              <span><i className="bi bi-tag" aria-hidden="true" /> Catalog pricing</span>
              <span><i className="bi bi-geo-alt" aria-hidden="true" /> Saved addresses</span>
            </div>
            <div className="customer-welcome-actions">
              <Link className="customer-hero-primary" to="/customer/bookings">
                View My Bookings <i className="bi bi-arrow-right" aria-hidden="true" />
              </Link>
              <Link className="customer-hero-secondary" to="/customer/profile">
                <i className="bi bi-person-gear" aria-hidden="true" /> Profile &amp; Addresses
              </Link>
            </div>
          </div>
          <div className="customer-welcome-caption">
            <i className="bi bi-brightness-high" aria-hidden="true" />
            A little care goes a long way
          </div>
        </section>

        {bookings.length === 0 && !bookingsLoading && !bookingsError && !servicesLoading && services.length > 0 && (
          <section className="customer-first-booking" aria-label="Get started with your first booking">
            <span className="customer-first-booking-icon" aria-hidden="true">
              <i className="bi bi-calendar-plus" />
            </span>
            <div>
              <strong>Ready for your first HomeFix booking?</strong>
              <p>Pick a service below and choose a time that works for you.</p>
            </div>
            <a href="#customer-services">Browse services <i className="bi bi-arrow-down" aria-hidden="true" /></a>
          </section>
        )}

        <section className="home-services" id="customer-services" aria-labelledby="home-services-title">
          <div className="section-heading customer-services-heading">
            <div>
              <span className="eyebrow">FIND THE RIGHT PROFESSIONAL</span>
              <h2 id="home-services-title">What do you need help with?</h2>
              <p>Search trusted home services and book at a time that suits you.</p>
            </div>
            <button type="button" className="customer-view-all" onClick={clearFilters}>
              View All Services <i className="bi bi-arrow-right" aria-hidden="true" />
            </button>
          </div>

          <form className="customer-service-search" onSubmit={submitSearch}>
            <label className="service-search-field">
              <span className="visually-hidden">Search services</span>
              <i className="bi bi-search" aria-hidden="true" />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search services, like plumbing, electrical..."
              />
            </label>
            {addresses.length > 0 ? (
              <label className="customer-location-select">
                <i className="bi bi-geo-alt" aria-hidden="true" />
                <span className="visually-hidden">Choose a saved service address</span>
                <select
                  aria-label="Choose a saved service address"
                  value={selectedAddress}
                  onChange={(event) => setSelectedAddress(event.target.value)}
                >
                  {addresses.map((address) => (
                    <option key={address.id} value={String(address.id)}>
                      {address.label || "Saved address"}: {[address.city, address.postalCode].filter(Boolean).join(" · ")}
                    </option>
                  ))}
                </select>
                <i className="bi bi-chevron-down customer-location-chevron" aria-hidden="true" />
              </label>
            ) : (
              <Link className="customer-location-empty" to="/customer/profile">
                <i className="bi bi-geo-alt" aria-hidden="true" />
                <span>
                  <small>{addressError ? "Location unavailable" : "Your location"}</small>
                  <strong>{addressError ? "Manage saved addresses" : "Add a service address"}</strong>
                </span>
                <i className="bi bi-chevron-right" aria-hidden="true" />
              </Link>
            )}
            <button className="customer-search-button" type="submit">
              <i className="bi bi-search" aria-hidden="true" /> Search
            </button>
          </form>

          {services.length > 0 && (
            <div className="customer-category-section">
              <div className="customer-category-heading">
                <h3>Browse by category</h3>
                <label className="service-category-filter">
                  <span className="visually-hidden">Filter by service category</span>
                  <select
                    value={selectedService}
                    onChange={(event) => setSelectedService(event.target.value)}
                  >
                    <option value="">All services</option>
                    {serviceOptions.map((option) => (
                      <option key={option.code} value={option.code}>{option.name}</option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="customer-category-list" aria-label="Service category filters">
                <button
                  type="button"
                  className={`customer-category-card${selectedService === "" ? " is-selected" : ""}`}
                  aria-pressed={selectedService === ""}
                  onClick={() => setSelectedService("")}
                >
                  <span className="customer-category-all-icon"><i className="bi bi-grid" aria-hidden="true" /></span>
                  <span className="customer-category-copy">
                    <strong>All services</strong>
                    <small>Explore everything</small>
                  </span>
                  <i className="bi bi-chevron-right customer-category-arrow" aria-hidden="true" />
                </button>
                {serviceOptions.map(({ code, name, service }) => (
                  <button
                    type="button"
                    className={`customer-category-card${selectedService === code ? " is-selected" : ""}`}
                    key={code}
                    aria-pressed={selectedService === code}
                    onClick={() => setSelectedService(code)}
                  >
                    <ServiceArtwork service={service} className="customer-category-image" />
                    <span className="customer-category-copy">
                      <strong>{name}</strong>
                      <small>{service.shortDescription}</small>
                    </span>
                    <i className="bi bi-chevron-right customer-category-arrow" aria-hidden="true" />
                  </button>
                ))}
              </div>
            </div>
          )}

          {servicesError && (
            <div className="bookings-error" role="alert">
              <span>{servicesError}</span>
              <button type="button" onClick={onRetryServices} disabled={servicesLoading}>
                Try again
              </button>
            </div>
          )}

          <div className="customer-service-results-heading" id="customer-services-grid">
            <div>
              <span className="eyebrow">BOOK WITH CONFIDENCE</span>
              <h3>Services for your home</h3>
            </div>
            {!servicesLoading && services.length > 0 && (
              <span className="service-results-count" role="status">
                {visibleServices.length} of {services.length} services
              </span>
            )}
          </div>

          {servicesLoading ? (
            <div className="home-service-grid customer-service-grid" role="status" aria-label="Loading services and current prices">
              {Array.from({ length: 3 }, (_, index) => (
                <div className="customer-service-skeleton" key={index}>
                  <span className="customer-skeleton-image" />
                  <span className="customer-skeleton-line" />
                  <span className="customer-skeleton-line short" />
                  <span className="customer-skeleton-button" />
                </div>
              ))}
            </div>
          ) : services.length === 0 ? (
            <div className="customer-services-empty">
              <span><i className="bi bi-house-gear" aria-hidden="true" /></span>
              <h3>Services will be back shortly</h3>
              <p>
                {servicesError
                  ? "We couldn't load the service catalog. Please try again."
                  : "No services are available right now. Please check back soon."}
              </p>
              {servicesError && (
                <button type="button" onClick={onRetryServices} disabled={servicesLoading}>
                  Try again
                </button>
              )}
            </div>
          ) : visibleServices.length === 0 ? (
            <div className="no-bookings service-search-empty">
              <i className="bi bi-search" aria-hidden="true" />
              <p>No services match those filters.</p>
              <button type="button" className="text-link" onClick={clearFilters}>
                Clear search and filters
              </button>
            </div>
          ) : (
            <div className="home-service-grid customer-service-grid">
              {visibleServices.map((service) => (
                <article className="home-service-card customer-service-card" key={service.slug}>
                  <Link
                    className="customer-service-image-link"
                    to={`/customer/book/${service.slug}`}
                    state={bookingState}
                    aria-label={`View ${service.name} booking details`}
                    tabIndex={-1}
                  >
                    <ServiceArtwork service={service} className="customer-service-image" />
                    <span className="customer-service-image-overlay">
                      <i className="bi bi-arrow-up-right" aria-hidden="true" />
                    </span>
                  </Link>
                  <div className="customer-service-card-body">
                    <span className="customer-service-card-category">
                      <i className={service.icon} aria-hidden="true" /> {service.categoryName.toUpperCase()}
                    </span>
                    <h3>{service.name}</h3>
                    <p>{service.shortDescription}</p>
                    <div className="home-service-card-footer customer-service-card-footer">
                      <span className="customer-price-label">Starting from <strong>₹{service.startingPrice.toLocaleString("en-IN")}</strong></span>
                      <Link
                        className="service-book-link"
                        to={`/customer/book/${service.slug}`}
                        state={bookingState}
                        aria-label={`Book ${service.name}`}
                      >
                        Book Now <i className="bi bi-arrow-right" aria-hidden="true" />
                      </Link>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
