import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  addCustomerAddress,
  fetchCustomerAddresses,
  fetchCustomerProfile,
  updateCustomerProfile,
} from "../services/apiService";

export default function CustomerProfilePage() {
  const [profile, setProfile] = useState({ fullName: "", email: "", phone: "" });
  const [addresses, setAddresses] = useState([]);
  const [address, setAddress] = useState({
    label: "Home", streetAddress: "", city: "", postalCode: "",
    latitude: "", longitude: "", primaryAddress: false,
  });
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const load = async () => {
    setBusy(true);
    setMessage("");
    try {
      const [profileResponse, addressesResponse] = await Promise.all([
        fetchCustomerProfile(), fetchCustomerAddresses(),
      ]);
      setProfile(profileResponse.data);
      setAddresses(addressesResponse.data);
    } catch (error) {
      setMessage(error.response?.data?.message || error.message || "Unable to load your profile.");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const saveProfile = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      await updateCustomerProfile({ fullName: profile.fullName, phone: profile.phone });
      setMessage("Profile updated.");
    } catch (error) {
      setMessage(error.response?.data?.message || error.message || "Unable to update your profile.");
    } finally {
      setBusy(false);
    }
  };

  const saveAddress = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      await addCustomerAddress({
        ...address,
        latitude: address.latitude === "" ? null : Number(address.latitude),
        longitude: address.longitude === "" ? null : Number(address.longitude),
      });
      setAddress({
        label: "Home", streetAddress: "", city: "", postalCode: "",
        latitude: "", longitude: "", primaryAddress: false,
      });
      await load();
      setMessage("Address saved.");
    } catch (error) {
      setMessage(error.response?.data?.message || error.message || "Unable to save this address.");
    } finally {
      setBusy(false);
    }
  };

  const useLocation = () => {
    if (!navigator.geolocation) {
      setMessage("This browser does not support location. Enter the address manually.");
      return;
    }
    setMessage("Requesting location permission...");
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setAddress((current) => ({
          ...current,
          latitude: coords.latitude.toString(),
          longitude: coords.longitude.toString(),
        }));
        setMessage("Coordinates added to this address. Enter the street and city manually.");
      },
      (error) => setMessage(error.code === 1
        ? "Location permission denied. You can still save a manual address."
        : "Location unavailable. You can still save a manual address."),
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 },
    );
  };

  return (
    <main className="dashboard customer-profile-page">
      <Link className="back-link" to="/customer">← Customer home</Link>
      <section className="custom-card profile-settings-card">
        <span className="eyebrow">YOUR ACCOUNT</span>
        <h1>Profile and saved addresses</h1>
        <form className="service-form" onSubmit={saveProfile}>
          <label className="form-field">Full name
            <input className="form-control" required maxLength={120} value={profile.fullName}
              onChange={(event) => setProfile({ ...profile, fullName: event.target.value })} />
          </label>
          <label className="form-field">Email
            <input className="form-control" value={profile.email} readOnly />
          </label>
          <label className="form-field">Phone
            <input className="form-control" required pattern="[0-9]{10}" maxLength={10}
              value={profile.phone} onChange={(event) => setProfile({ ...profile, phone: event.target.value })} />
          </label>
          <button type="submit" disabled={busy}>Save profile</button>
        </form>
      </section>

      <section className="custom-card profile-settings-card">
        <h2>Add an address</h2>
        <form className="service-form" onSubmit={saveAddress}>
          <label className="form-field">Label
            <input className="form-control" required maxLength={40} value={address.label}
              onChange={(event) => setAddress({ ...address, label: event.target.value })} />
          </label>
          <label className="form-field form-field-wide">Street address
            <input className="form-control" required maxLength={500} value={address.streetAddress}
              onChange={(event) => setAddress({ ...address, streetAddress: event.target.value })} />
          </label>
          <label className="form-field">City
            <input className="form-control" required maxLength={100} value={address.city}
              onChange={(event) => setAddress({ ...address, city: event.target.value })} />
          </label>
          <label className="form-field">Postal code
            <input className="form-control" maxLength={20} value={address.postalCode}
              onChange={(event) => setAddress({ ...address, postalCode: event.target.value })} />
          </label>
          <label className="profile-primary-address">
            <input type="checkbox" checked={address.primaryAddress}
              onChange={(event) => setAddress({ ...address, primaryAddress: event.target.checked })} />
            Make this my primary address
          </label>
          <button type="button" className="btn btn-outline-primary" onClick={useLocation}>Add GPS coordinates</button>
          <button type="submit" disabled={busy}>Save address</button>
        </form>
        <div className="profile-address-list">
          {addresses.map((item) => (
            <article key={item.id}><strong>{item.label}{item.primaryAddress ? " · Primary" : ""}</strong>
              <span>{item.streetAddress}, {item.city} {item.postalCode}</span></article>
          ))}
          {!busy && addresses.length === 0 && <p>No saved addresses yet.</p>}
        </div>
      </section>
      {message && <p className="app-message" role="status">{message}</p>}
      <button type="button" className="refresh-bookings-button" onClick={load} disabled={busy}>Refresh profile</button>
    </main>
  );
}
