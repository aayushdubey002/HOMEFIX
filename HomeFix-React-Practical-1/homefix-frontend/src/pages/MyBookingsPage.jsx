import React from "react";
import { Link } from "react-router-dom";
import BookingTable from "../components/BookingTable";

export default function MyBookingsPage({ bookings, loading, error, onRefresh }) {
  return (
    <main className="dashboard my-bookings-page">
      <div className="bookings-page-heading">
        <div>
          <span className="eyebrow">YOUR HOMEFIX ACTIVITY</span>
          <h1>My Bookings</h1>
          <p>Track your service requests and see provider updates.</p>
        </div>
        <div className="bookings-page-actions">
          <button
            type="button"
            className="refresh-bookings-button"
            onClick={onRefresh}
            disabled={loading}
          >
            <i className={`bi ${loading ? "bi-arrow-repeat spin-icon" : "bi-arrow-clockwise"}`} aria-hidden="true"></i>
            {loading ? "Refreshing..." : "Refresh"}
          </button>
          <Link className="secondary-link-button" to="/customer">Browse Services</Link>
        </div>
      </div>

      {error && (
        <div className="bookings-error" role="alert">
          <span>{error}</span>
          <button type="button" onClick={onRefresh} disabled={loading}>Try again</button>
        </div>
      )}

      <BookingTable
        bookings={bookings}
        user={{ role: "CUSTOMER" }}
        loading={loading}
      />
    </main>
  );
}
