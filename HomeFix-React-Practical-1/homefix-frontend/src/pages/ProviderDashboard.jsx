import React from "react";
import BookingTable from "../components/BookingTable";

export default function ProviderDashboard({
  user,
  bookings,
  loading,
  actionLoading,
  onAccept,
  onReject,
  error,
  onRefresh,
}) {
  const normalizeCategory = (category) => {
    const normalized = category?.trim().replace(/[-_]+/g, " ").replace(/\s+/g, " ").toUpperCase();
    const aliases = {
      PLUMBER: "PLUMBING",
      ELECTRICIAN: "ELECTRICAL",
      "AC SERVICE": "AC REPAIR",
      "AC REPAIR SERVICE": "AC REPAIR",
      "AIR CONDITIONING": "AC REPAIR",
      "HOME CLEANING": "CLEANING",
      CARPENTER: "CARPENTRY",
    };
    return aliases[normalized] || normalized;
  };

  const category = normalizeCategory(user.serviceCategory);
  const matchingBookings = bookings.filter(
    (booking) => category && normalizeCategory(booking.service) === category
  );

  return (
    <main className="dashboard provider-dashboard">
      <div className="welcome-banner provider-welcome">
        <div className="welcome-copy">
          <span className="eyebrow">YOUR PROVIDER WORKSPACE</span>
          <h1>Welcome, {user.fullName || "Provider"}!</h1>
          <p>Review matching service requests, connect with customers, and manage your work.</p>
        </div>
        <div className="welcome-illustration" aria-hidden="true">
          <i className="bi bi-tools"></i>
        </div>
      </div>

      <section className="provider-profile custom-card" aria-labelledby="provider-profile-title">
        <div className="provider-profile-heading">
          <span className="provider-avatar" aria-hidden="true"><i className="bi bi-person-badge"></i></span>
          <div>
            <span className="eyebrow">YOUR PROFILE</span>
            <h2 id="provider-profile-title">Provider details</h2>
          </div>
        </div>
        <div className="provider-details-grid">
          {[
            ["bi-person", "Provider name", user.fullName],
            ["bi-tools", "Service category", user.serviceCategory],
            ["bi-award", "Experience", user.experience],
            ["bi-telephone", "Phone number", user.phone],
            ["bi-geo-alt", "Service area", user.serviceArea],
          ].map(([icon, label, value]) => (
            <div className="provider-detail" key={label}>
              <span className="provider-detail-icon" aria-hidden="true"><i className={`bi ${icon}`}></i></span>
              <div><small>{label}</small><strong>{value || "Not provided"}</strong></div>
            </div>
          ))}
        </div>
      </section>

      {error && (
        <div className="bookings-error" role="alert">
          <span>{error}</span>
          <button type="button" onClick={onRefresh} disabled={loading}>Try again</button>
        </div>
      )}

      <section className="bookings-section">
        <BookingTable
          bookings={matchingBookings}
          user={user}
          loading={loading}
          onAccept={onAccept}
          onReject={onReject}
          actionLoading={actionLoading}
        />
      </section>
    </main>
  );
}
