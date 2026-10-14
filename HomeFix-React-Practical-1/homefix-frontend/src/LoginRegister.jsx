import "./LoginRegister.css";
import React, { useRef, useState } from "react";
import { registerUser, loginUser } from "./services/apiService";

const initialForm = {
  fullName: "",
  email: "",
  password: "",
  phone: "",
  serviceCategory: "",
  experience: "",
  serviceArea: ""
};

export default function LoginRegister({ onLogin }) {
  const [activeTab, setActiveTab] = useState("login");
  const [role, setRole] = useState("CUSTOMER");
  const [form, setForm] = useState(initialForm);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(false);
  const submissionInFlight = useRef(false);

  const isRegister = activeTab === "register";
  const isProvider = role === "PROVIDER";

  const handleChange = (e) => {
    const { name, value } = e.target;

    setForm((prev) => ({
      ...prev,
      [name]: value
    }));
  };

  const switchTab = (tab) => {
    setActiveTab(tab);
    setStatus("");
    setForm(initialForm);
  };

  const handleRoleChange = (newRole) => {
    setRole(newRole);
    setStatus("");
    setForm(initialForm);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (submissionInFlight.current) return;
    submissionInFlight.current = true;
    setLoading(true);
    setStatus("");

    try {
      if (isRegister) {
        const registrationData = {
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          password: form.password,
          role,
          phone: form.phone.trim(),
          serviceCategory: isProvider ? form.serviceCategory : "",
          experience: isProvider ? form.experience : "",
          serviceArea: isProvider ? form.serviceArea.trim() : ""
        };

        const response = await registerUser(registrationData);

        if (response.status !== 201 || !response.data?.message) {
          throw new Error("The backend did not confirm account creation.");
        }
        setStatus(response.data.message);

        setForm(initialForm);
        setActiveTab("login");
      } else {
        const response = await loginUser({
          email: form.email.trim(),
          password: form.password,
          role
        });

        const user = response?.data?.user;
        const accessToken = response?.data?.accessToken;

        if (!user?.id || !accessToken) {
          setStatus(
            response?.data?.message
              || "Login response did not include an authenticated session."
          );
          return;
        }

        // Prevent signing in through the wrong role option
        if (
          user.role &&
          user.role.toUpperCase() !== role
        ) {
          setStatus(
            `This account is registered as ${user.role.toLowerCase()}. Please select the correct account type.`
          );
          return;
        }

        const loggedInUser = {
          id: user.id,
          email: user.email,
          fullName: user.fullName || "",
          role: user.role || role,
          phone: user.phone || "",
          serviceCategory: user.serviceCategory || "",
          experience: user.experience || "",
          serviceArea: user.serviceArea || ""
        };

        setStatus("Login successful!");

        if (onLogin) {
          onLogin(loggedInUser, accessToken);
        }

        setForm(initialForm);
      }
    } catch (error) {
      const timedOut = error?.code === "ECONNABORTED" || error?.code === "ETIMEDOUT";
      setStatus(
        timedOut && isRegister
          ? "Registration timed out before the server replied. Your account may still have been created; try signing in before registering again."
          : error?.response?.data?.message ||
            "Request failed. Please check your connection and backend server."
      );
    } finally {
      submissionInFlight.current = false;
      setLoading(false);
    }
  };

  return (
    <div className="homefix-auth-page">
      <div className="homefix-auth-card">

        {/* LEFT SIDE */}
        <section className="homefix-auth-left">
          <div className="homefix-brand">
            <span className="homefix-brand-icon">⌂</span>
            <span>HomeFix</span>
          </div>

          <div className="homefix-auth-heading">
            <h1>
              {isRegister ? "Create Account" : "Welcome Back!"}
            </h1>

            <p className="homefix-auth-subtitle">
              {isRegister
                ? "Join HomeFix and get started."
                : "Sign in to continue to HomeFix."}
            </p>
          </div>

          {/* ACCOUNT TYPE */}
          <div className="homefix-input-group">
            <label htmlFor="accountRole">I am a</label>
            <select
              id="accountRole"
              value={role}
              onChange={(e) => handleRoleChange(e.target.value)}
              className="homefix-role-select"
            >
              <option value="CUSTOMER">Customer</option>
              <option value="PROVIDER">Service Provider</option>
            </select>
          </div>

          {/* LOGIN / SIGNUP TABS */}
          <div className="homefix-auth-tabs">
            <button
              type="button"
              className={`homefix-auth-tab ${
                activeTab === "login" ? "active" : ""
              }`}
              onClick={() => switchTab("login")}
            >
              Sign In
            </button>

            <button
              type="button"
              className={`homefix-auth-tab ${
                activeTab === "register" ? "active" : ""
              }`}
              onClick={() => switchTab("register")}
            >
              Sign Up
            </button>
          </div>

          <form
            className="homefix-auth-form"
            onSubmit={handleSubmit}
          >
            {isRegister && (
              <div className="homefix-input-group">
                <label htmlFor="fullName">Full Name</label>
                <input
                  id="fullName"
                  name="fullName"
                  type="text"
                  placeholder="Enter your full name"
                  value={form.fullName}
                  onChange={handleChange}
                  autoComplete="name"
                  required
                />
              </div>
            )}

            <div className="homefix-input-group">
              <label htmlFor="email">Email Address</label>
              <input
                id="email"
                name="email"
                type="email"
                placeholder="Enter your email"
                value={form.email}
                onChange={handleChange}
                autoComplete="email"
                maxLength={254}
                required
              />
            </div>

            <div className="homefix-input-group">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                name="password"
                type="password"
                placeholder="Enter your password"
                value={form.password}
                onChange={handleChange}
                autoComplete={
                  isRegister ? "new-password" : "current-password"
                }
                minLength={8}
                required
              />
              <small className="homefix-field-hint">
                At least 8 characters.
              </small>
            </div>

            {isRegister && (
              <>
                <div className="homefix-input-group">
                  <label htmlFor="phone">Phone Number</label>
                  <input
                    id="phone"
                    name="phone"
                    type="tel"
                    placeholder="Enter your phone number"
                    value={form.phone}
                    onChange={handleChange}
                    autoComplete="tel"
                    pattern="[0-9]{10}"
                    maxLength={10}
                    title="Enter a 10-digit phone number"
                    required
                  />
                </div>

                {isProvider && (
                  <>
                    <div className="homefix-input-group">
                      <label htmlFor="serviceCategory">
                        Service Category
                      </label>
                      <select
                        id="serviceCategory"
                        name="serviceCategory"
                        value={form.serviceCategory}
                        onChange={handleChange}
                        className="homefix-role-select"
                        required
                      >
                        <option value="">Select a service</option>
                        <option value="ELECTRICIAN">Electrician</option>
                        <option value="PLUMBER">Plumber</option>
                        <option value="AC_REPAIR">AC Repair</option>
                        <option value="CLEANING">Cleaning</option>
                        <option value="CARPENTER">Carpenter</option>
                        <option value="PAINTING">Painting</option>
                      </select>
                    </div>

                    <div className="homefix-input-group">
                      <label htmlFor="experience">
                        Experience
                      </label>
                      <select
                        id="experience"
                        name="experience"
                        value={form.experience}
                        onChange={handleChange}
                        className="homefix-role-select"
                        required
                      >
                        <option value="">Select experience</option>
                        <option value="LESS_THAN_1_YEAR">
                          Less than 1 year
                        </option>
                        <option value="1-3_YEARS">1–3 years</option>
                        <option value="3-5_YEARS">3–5 years</option>
                        <option value="5_PLUS_YEARS">
                          5+ years
                        </option>
                      </select>
                    </div>

                    <div className="homefix-input-group">
                      <label htmlFor="serviceArea">
                        Service Area / Address
                      </label>
                      <input
                        id="serviceArea"
                        name="serviceArea"
                        type="text"
                        placeholder="Enter your service area"
                        value={form.serviceArea}
                        onChange={handleChange}
                        autoComplete="street-address"
                        required
                      />
                    </div>
                  </>
                )}
              </>
            )}

            <button
              type="submit"
              className="homefix-submit-btn"
              disabled={loading}
            >
              {loading
                ? "Please wait..."
                : isRegister
                  ? isProvider
                    ? "Register as Provider"
                    : "Create Customer Account"
                  : "Sign In"}
            </button>

            {status && (
              <div
                className="homefix-auth-status"
                role="status"
                aria-live="polite"
              >
                {status}
              </div>
            )}
          </form>

          <p className="homefix-auth-switch">
            {isRegister
              ? "Already have an account? "
              : "Don't have an account? "}

            <button
              type="button"
              onClick={() =>
                switchTab(isRegister ? "login" : "register")
              }
            >
              {isRegister ? "Sign In" : "Sign Up Now"}
            </button>
          </p>

          <p className="homefix-auth-footer">
            HomeFix · Making home care easier
          </p>
        </section>

        {/* RIGHT SIDE ILLUSTRATION */}
        <section className="homefix-auth-right">
          <div className="homefix-art-circle circle-one"></div>
          <div className="homefix-art-circle circle-two"></div>

          <div className="homefix-art-content">
            <span className="homefix-art-label">
              YOUR HOME, OUR CARE
            </span>

            <h2>
              {isProvider
                ? "Your skills, our platform."
                : "Home services, made simple."}
            </h2>

            <p>
              {isProvider
                ? "Connect with customers who need your services."
                : "Find trusted help for repairs and everyday home maintenance."}
            </p>

            <div className="homefix-house-illustration">
              <div className="homefix-house-roof"></div>

              <div className="homefix-house">
                <div className="homefix-house-window"></div>
                <div className="homefix-house-window second"></div>
                <div className="homefix-house-door"></div>
              </div>

              <div className="homefix-house-base"></div>
            </div>

            <div className="homefix-service-pills">
              <span>🔧 Plumbing</span>
              <span>⚡ Electrical</span>
              <span>❄️ AC Repair</span>
            </div>
          </div>

          <div className="homefix-art-bottom">
            Reliable service for your home.
          </div>
        </section>
      </div>
    </div>
  );
}