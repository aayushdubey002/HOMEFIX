import "./LoginRegister.css";
import React, { useEffect, useRef, useState } from "react";
import { fetchServiceCatalog, registerUser, loginUser } from "./services/apiService";
import HomeFixLogoMark from "./components/HomeFixLogoMark";

const initialForm = {
  fullName: "",
  email: "",
  password: "",
  phone: "",
  serviceCategory: "",
  experience: "",
  serviceArea: ""
};

const accountRoles = [
  { value: "CUSTOMER", label: "Customer", icon: "bi-person-fill", description: "Book trusted home help" },
  { value: "PROVIDER", label: "Service Provider", icon: "bi-tools", description: "Grow your service business" },
  { value: "ADMIN", label: "Administrator", icon: "bi-shield-lock-fill", description: "Manage the HomeFix platform" }
];

export default function LoginRegister({ onLogin }) {
  const [activeTab, setActiveTab] = useState("login");
  const [role, setRole] = useState("CUSTOMER");
  const [form, setForm] = useState(initialForm);
  const [status, setStatus] = useState("");
  const [statusType, setStatusType] = useState("info");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [serviceCategories, setServiceCategories] = useState([]);
  const [serviceCategoriesLoading, setServiceCategoriesLoading] = useState(false);
  const [serviceCategoriesError, setServiceCategoriesError] = useState("");
  const submissionInFlight = useRef(false);

  const isRegister = activeTab === "register";
  const isProvider = role === "PROVIDER";
  const isAdminRegistration = isRegister && role === "ADMIN";

  useEffect(() => {
    if (!isRegister || !isProvider) return undefined;

    let active = true;
    setServiceCategoriesLoading(true);
    setServiceCategoriesError("");

    fetchServiceCatalog()
      .then((response) => {
        const categories = response.data;
        if (!Array.isArray(categories) || categories.length === 0) {
          throw new Error("No active service categories are available.");
        }
        if (active) {
          setServiceCategories(categories);
        }
      })
      .catch((error) => {
        if (active) {
          setServiceCategories([]);
          setServiceCategoriesError(
            error.response?.data?.message
              || error.message
              || "Unable to load service categories. Please try again."
          );
        }
      })
      .finally(() => {
        if (active) setServiceCategoriesLoading(false);
      });

    return () => {
      active = false;
    };
  }, [isRegister, isProvider]);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
  };

  const switchTab = (tab) => {
    setActiveTab(tab);
    setStatus("");
    setStatusType("info");
    setShowPassword(false);
    setForm(initialForm);
  };

  const handleRoleChange = (newRole) => {
    setRole(newRole);
    setStatus("");
    setStatusType("info");
    setForm(initialForm);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (submissionInFlight.current) return;
    if (isAdminRegistration) {
      setStatus("Administrator accounts are provisioned by HomeFix and cannot be created here.");
      setStatusType("error");
      return;
    }

    submissionInFlight.current = true;
    setLoading(true);
    setStatus("");
    setStatusType("info");

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

        if (isProvider) {
          const loginResponse = await loginUser({
            email: registrationData.email,
            password: registrationData.password,
            role: "PROVIDER"
          });
          const user = loginResponse?.data?.user;
          const accessToken = loginResponse?.data?.accessToken;
          if (!user?.id || !accessToken || user.role?.toUpperCase() !== "PROVIDER") {
            throw new Error("Provider application was submitted, but automatic sign-in could not be verified.");
          }

          const loggedInUser = {
            id: user.id,
            email: user.email,
            fullName: user.fullName || "",
            role: user.role,
            phone: user.phone || "",
            serviceCategory: user.serviceCategory || "",
            experience: user.experience || "",
            serviceArea: user.serviceArea || "",
            providerApproved: user.providerApproved === true
          };
          sessionStorage.setItem("homefixAccessToken", accessToken);
          onLogin?.(loggedInUser, accessToken);
          setForm(initialForm);
          return;
        }

        setStatus(response.data.message);
        setStatusType("success");
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
        if (!user?.id || !accessToken || !user.role) {
          throw new Error(
            response?.data?.message
              || "Login response did not include a verified user role and authenticated session."
          );
        }

        if (user.role.toUpperCase() !== role) {
          setStatus(
            `This account is registered as ${user.role.toLowerCase()}. Please select the correct account type.`
          );
          setStatusType("error");
          return;
        }

        const loggedInUser = {
          id: user.id,
          email: user.email,
          fullName: user.fullName || "",
          role: user.role,
          phone: user.phone || "",
          serviceCategory: user.serviceCategory || "",
          experience: user.experience || "",
          serviceArea: user.serviceArea || "",
          providerApproved: user.providerApproved === true
        };

        sessionStorage.setItem("homefixAccessToken", accessToken);
        onLogin?.(loggedInUser, accessToken);
        setForm(initialForm);
      }
    } catch (error) {
      console.error("HomeFix authentication error:", error);
      setStatus(
        error?.response?.data?.message
          || (error?.response?.status === 401
            ? "Invalid email or password."
            : error?.response?.status === 403
              ? "This account does not have access to the selected sign-in option."
              : error?.response
                ? "Unable to complete your request. Please try again."
                : "Unable to reach HomeFix. Check your connection and try again.")
      );
      setStatusType("error");
    } finally {
      submissionInFlight.current = false;
      setLoading(false);
    }
  };

  return (
    <main className="homefix-auth-page">
      <div className="homefix-auth-card">
        <section className="homefix-auth-left" aria-labelledby="homefix-auth-title">
          <a className="homefix-brand" href="/login" aria-label="HomeFix home">
            <HomeFixLogoMark className="homefix-brand-icon" />
            <span className="homefix-brand-copy">
              <span className="homefix-brand-name">HomeFix</span>
              <small>Home services, made simple</small>
            </span>
          </a>

          <div className="homefix-auth-heading">
            <span className="homefix-eyebrow">
              {isRegister ? "GET STARTED WITH HOMEFIX" : "WELCOME TO HOMEFIX"}
            </span>
            <h1 id="homefix-auth-title">
              {isRegister ? "Create your account" : "Welcome Back!"}
            </h1>
            <p className="homefix-auth-subtitle">
              {isRegister
                ? "A better way to care for your home starts here."
                : "Sign in to book trusted help and take care of home."}
            </p>
          </div>

          <div className="homefix-role-field">
            <span className="homefix-field-label" id="homefix-role-label">
              {isRegister ? "I want to join as a" : "Continue as"}
            </span>
            <div className="homefix-role-options" role="group" aria-labelledby="homefix-role-label">
              {accountRoles.map((accountRole) => (
                <button
                  key={accountRole.value}
                  type="button"
                  className={`homefix-role-option ${role === accountRole.value ? "is-selected" : ""}`}
                  aria-pressed={role === accountRole.value}
                  onClick={() => handleRoleChange(accountRole.value)}
                >
                  <span className="homefix-role-icon" aria-hidden="true">
                    <i className={`bi ${accountRole.icon}`} />
                  </span>
                  <span className="homefix-role-copy">
                    <strong>{accountRole.label}</strong>
                    <small>{accountRole.description}</small>
                  </span>
                  <i className="bi bi-check-circle-fill homefix-role-check" aria-hidden="true" />
                </button>
              ))}
            </div>
          </div>

          {isAdminRegistration ? (
            <div className="homefix-admin-notice" role="status">
              <i className="bi bi-shield-lock-fill" aria-hidden="true" />
              <span>
                Administrator accounts are provisioned by HomeFix. Sign in with an authorized admin account or choose another role to register.
              </span>
            </div>
          ) : (
            <form
              className="homefix-auth-form"
              onSubmit={handleSubmit}
              aria-busy={loading}
            >
              {isRegister && (
                <div className="homefix-input-group">
                  <label htmlFor="fullName">Full Name</label>
                  <div className="homefix-input-wrap">
                    <i className="bi bi-person homefix-input-icon" aria-hidden="true" />
                    <input
                      id="fullName"
                      name="fullName"
                      type="text"
                      placeholder="Your full name"
                      value={form.fullName}
                      onChange={handleChange}
                      autoComplete="name"
                      maxLength={100}
                      required
                    />
                  </div>
                </div>
              )}

              <div className="homefix-input-group">
                <label htmlFor="email">Email Address</label>
                <div className="homefix-input-wrap">
                  <i className="bi bi-envelope homefix-input-icon" aria-hidden="true" />
                  <input
                    id="email"
                    name="email"
                    type="email"
                    placeholder="you@example.com"
                    value={form.email}
                    onChange={handleChange}
                    autoComplete="email"
                    maxLength={254}
                    required
                  />
                </div>
              </div>

              <div className="homefix-input-group">
                <label htmlFor="password">Password</label>
                <div className="homefix-input-wrap homefix-password-wrap">
                  <i className="bi bi-lock homefix-input-icon" aria-hidden="true" />
                  <input
                    id="password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    placeholder={isRegister ? "Create a password" : "Enter your password"}
                    value={form.password}
                    onChange={handleChange}
                    autoComplete={isRegister ? "new-password" : "current-password"}
                    minLength={8}
                    aria-describedby={isRegister ? "homefix-password-hint" : undefined}
                    required
                  />
                  <button
                    type="button"
                    className="homefix-password-toggle"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword((visible) => !visible)}
                  >
                    <i className={`bi ${showPassword ? "bi-eye-slash" : "bi-eye"}`} aria-hidden="true" />
                  </button>
                </div>
                {isRegister && (
                  <small className="homefix-field-hint" id="homefix-password-hint">
                    Use at least 8 characters.
                  </small>
                )}
              </div>

              {isRegister && (
                <>
                  <div className="homefix-input-group">
                    <label htmlFor="phone">Phone Number</label>
                    <div className="homefix-input-wrap">
                      <i className="bi bi-telephone homefix-input-icon" aria-hidden="true" />
                      <input
                        id="phone"
                        name="phone"
                        type="tel"
                        placeholder="10-digit mobile number"
                        value={form.phone}
                        onChange={handleChange}
                        autoComplete="tel"
                        inputMode="numeric"
                        pattern="[0-9]{10}"
                        maxLength={10}
                        title="Enter a 10-digit phone number"
                        required
                      />
                    </div>
                  </div>

                  {isProvider && (
                    <>
                      <div className="homefix-input-group">
                        <label htmlFor="serviceCategory">Service Category</label>
                        <div className="homefix-input-wrap homefix-select-wrap">
                          <i className="bi bi-wrench-adjustable homefix-input-icon" aria-hidden="true" />
                          <select
                            id="serviceCategory"
                            name="serviceCategory"
                            value={form.serviceCategory}
                            onChange={handleChange}
                            required
                          >
                            <option value="">Select a service</option>
                            {serviceCategories.map((category) => (
                              <option key={category.slug} value={category.service}>
                                {category.name}
                              </option>
                            ))}
                          </select>
                        </div>
                        {serviceCategoriesLoading && (
                          <small role="status">Loading available services...</small>
                        )}
                        {serviceCategoriesError && (
                          <small className="homefix-inline-error" role="alert">{serviceCategoriesError}</small>
                        )}
                      </div>

                      <div className="homefix-input-group">
                        <label htmlFor="experience">Experience</label>
                        <div className="homefix-input-wrap homefix-select-wrap">
                          <i className="bi bi-briefcase homefix-input-icon" aria-hidden="true" />
                          <select
                            id="experience"
                            name="experience"
                            value={form.experience}
                            onChange={handleChange}
                            required
                          >
                            <option value="">Select experience</option>
                            <option value="LESS_THAN_1_YEAR">Less than 1 year</option>
                            <option value="1-3_YEARS">1–3 years</option>
                            <option value="3-5_YEARS">3–5 years</option>
                            <option value="5_PLUS_YEARS">5+ years</option>
                          </select>
                        </div>
                      </div>

                      <div className="homefix-input-group">
                        <label htmlFor="serviceArea">Service Area / Address</label>
                        <div className="homefix-input-wrap">
                          <i className="bi bi-geo-alt homefix-input-icon" aria-hidden="true" />
                          <input
                            id="serviceArea"
                            name="serviceArea"
                            type="text"
                            placeholder="City or neighborhood you serve"
                            value={form.serviceArea}
                            onChange={handleChange}
                            autoComplete="street-address"
                            maxLength={200}
                            required
                          />
                        </div>
                      </div>
                    </>
                  )}
                </>
              )}

              {status && (
                <div
                  className={`homefix-auth-status is-${statusType}`}
                  role={statusType === "error" ? "alert" : "status"}
                  aria-live="polite"
                >
                  <i
                    className={`bi ${statusType === "error" ? "bi-exclamation-circle-fill" : statusType === "success" ? "bi-check-circle-fill" : "bi-info-circle-fill"}`}
                    aria-hidden="true"
                  />
                  <span>{status}</span>
                </div>
              )}

              <button
                type="submit"
                className="homefix-submit-btn"
                disabled={loading || (isRegister && isProvider
                  && (serviceCategoriesLoading || serviceCategories.length === 0
                    || Boolean(serviceCategoriesError)))}
              >
                {loading ? (
                  <>
                    <span className="homefix-spinner" aria-hidden="true" />
                    {isRegister ? "Creating your account..." : "Signing you in..."}
                  </>
                ) : (
                  <>
                    {isRegister
                      ? isProvider ? "Submit provider application" : "Create account"
                      : "Sign In"}
                    <i className="bi bi-arrow-right" aria-hidden="true" />
                  </>
                )}
              </button>
            </form>
          )}

          <p className="homefix-auth-switch">
            {isRegister ? "Already have an account?" : "New to HomeFix?"}
            <button
              type="button"
              onClick={() => switchTab(isRegister ? "login" : "register")}
            >
              {isRegister ? "Sign In" : "Sign Up"}
            </button>
          </p>

          <p className="homefix-auth-footer">
            <i className="bi bi-shield-check" aria-hidden="true" />
            Secure access to the care your home deserves
          </p>
        </section>

        <aside className="homefix-auth-right" aria-label="HomeFix services">
          <div className="homefix-art-orb homefix-art-orb-one" aria-hidden="true" />
          <div className="homefix-art-orb homefix-art-orb-two" aria-hidden="true" />
          <div className="homefix-art-content">
            <span className="homefix-art-label">
              <span className="homefix-art-label-dot" />
              HOME CARE, REIMAGINED
            </span>
            <h2>Home services,<br />made simple</h2>
            <p>Trusted local professionals, thoughtful service, and peace of mind — all in one place.</p>

            <div className="homefix-home-illustration" aria-hidden="true">
              <svg viewBox="0 0 560 390" role="img">
                <defs>
                  <linearGradient id="homefixSky" x1="0" x2="1" y1="0" y2="1">
                    <stop offset="0" stopColor="#d8eaff" />
                    <stop offset="1" stopColor="#f4f8ff" />
                  </linearGradient>
                  <linearGradient id="homefixRoof" x1="0" x2="1">
                    <stop offset="0" stopColor="#ffcf77" />
                    <stop offset="1" stopColor="#f3a955" />
                  </linearGradient>
                  <filter id="homefixShadow" x="-30%" y="-30%" width="160%" height="180%">
                    <feDropShadow dx="0" dy="14" stdDeviation="13" floodColor="#082c69" floodOpacity=".18" />
                  </filter>
                </defs>
                <ellipse cx="282" cy="342" rx="206" ry="25" fill="#9dc0ee" opacity=".34" />
                <rect x="70" y="58" width="420" height="270" rx="30" fill="url(#homefixSky)" />
                <circle cx="414" cy="112" r="36" fill="#fff" opacity=".62" />
                <path d="M92 271c44-54 76-55 121 0s80 49 116-1 75-50 130 2v56H92z" fill="#c5dbf5" />
                <g filter="url(#homefixShadow)">
                  <path d="m160 190 120-94 121 94v122H160z" fill="#fff" />
                  <path d="m137 195 143-112 143 112-18 20-125-98-125 98z" fill="url(#homefixRoof)" />
                  <path d="M207 210h55v53h-55z" fill="#cce4ff" />
                  <path d="M234 210v53m-27-26h55" stroke="#fff" strokeWidth="5" />
                  <path d="M310 210h55v53h-55z" fill="#cce4ff" />
                  <path d="M337 210v53m-27-26h55" stroke="#fff" strokeWidth="5" />
                  <path d="M260 244h42v68h-42z" fill="#f3a955" />
                  <circle cx="292" cy="281" r="2.8" fill="#fff" />
                  <path d="M157 308h247" stroke="#8eb3e5" strokeWidth="7" strokeLinecap="round" />
                </g>
                <g>
                  <rect x="109" y="222" width="32" height="83" rx="10" fill="#78a9e3" />
                  <circle cx="125" cy="218" r="25" fill="#5c9d78" />
                  <circle cx="108" cy="237" r="17" fill="#77b18a" />
                  <circle cx="141" cy="239" r="18" fill="#69a77f" />
                  <rect x="396" y="225" width="32" height="80" rx="10" fill="#78a9e3" />
                  <circle cx="412" cy="216" r="24" fill="#5c9d78" />
                  <circle cx="395" cy="236" r="17" fill="#77b18a" />
                  <circle cx="428" cy="238" r="18" fill="#69a77f" />
                </g>
                <g transform="translate(347 38)">
                  <rect width="126" height="56" rx="18" fill="#fff" />
                  <circle cx="28" cy="28" r="14" fill="#e7f6ed" />
                  <path d="m22 28 4 4 8-9" fill="none" stroke="#31a16c" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                  <text x="50" y="25" fill="#28415f" fontFamily="Arial, sans-serif" fontSize="11" fontWeight="700">HOMEFIX</text>
                  <text x="50" y="39" fill="#71839a" fontFamily="Arial, sans-serif" fontSize="9">Trusted pros</text>
                </g>
              </svg>
            </div>

            <div className="homefix-service-list" aria-label="Popular services">
              <span><i className="bi bi-wrench-adjustable" aria-hidden="true" /> Plumbing</span>
              <span><i className="bi bi-lightning-charge-fill" aria-hidden="true" /> Electrical</span>
              <span><i className="bi bi-snow" aria-hidden="true" /> AC Repair</span>
            </div>

            <div className="homefix-trust-note">
              <span className="homefix-trust-icon"><i className="bi bi-patch-check-fill" aria-hidden="true" /></span>
              <span><strong>Good help, right at home.</strong><small>Reliable services, made for everyday life.</small></span>
            </div>
          </div>
          <span className="homefix-art-copyright">HOMEFIX · YOUR HOME, IN GOOD HANDS</span>
        </aside>
      </div>
    </main>
  );
}
