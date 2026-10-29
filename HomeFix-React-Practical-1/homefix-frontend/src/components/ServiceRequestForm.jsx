import React, { useRef, useState } from "react";
import {
  fetchLocationConfig,
  fetchPaymentConfiguration,
} from "../services/apiService";

function localDateString(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function ServiceRequestForm({ service, user, savedAddress, onSubmit }) {
  const bookingSteps = [
    { title: "Contact", description: "Your details" },
    { title: "Problem", description: "What needs fixing" },
    { title: "Address", description: "Where we visit" },
    { title: "Schedule", description: "Choose a time" },
    { title: "Payment", description: "Select a method" },
    { title: "Review", description: "Confirm booking" },
  ];
  const [formData, setFormData] = useState({
    customerName: user.fullName || "",
    service: service.requestService,
    phone: user.phone || "",
    address: [
      savedAddress?.streetAddress,
      savedAddress?.postalCode,
    ].filter(Boolean).join(", "),
    preferredDate: "",
    preferredTime: "09:00",
    latitude: savedAddress?.latitude ?? "",
    longitude: savedAddress?.longitude ?? "",
    city: savedAddress?.city || "",
    notes: "",
    paymentMethod: "CASH",
  });
  const [busy, setBusy] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [problemPhoto, setProblemPhoto] = useState(null);
  const [problemPhotoPreview, setProblemPhotoPreview] = useState("");
  const [status, setStatus] = useState("");
  const [locationStatus, setLocationStatus] = useState("");
  const [simulationCities, setSimulationCities] = useState([]);
  const [paymentConfiguration, setPaymentConfiguration] = useState({
    upiEnabled: false,
    gatewayEnabled: false,
  });
  const [paymentConfigurationError, setPaymentConfigurationError] = useState("");

  React.useEffect(() => {
    let active = true;
    fetchLocationConfig()
      .then((response) => {
        if (active && response.data?.simulationEnabled) {
          setSimulationCities(response.data.simulationCities || []);
        }
      })
      .catch((error) => {
        console.error("Unable to load location configuration:", error);
        if (active) {
          setLocationStatus("Location settings are unavailable; you can still use GPS or enter a manual address.");
        }
      });
    return () => { active = false; };
  }, []);

  React.useEffect(() => {
    let active = true;
    fetchPaymentConfiguration()
      .then((response) => {
        if (active) {
          setPaymentConfiguration(response.data || { upiEnabled: false, gatewayEnabled: false });
        }
      })
      .catch(() => {
        if (active) {
          setPaymentConfiguration({ upiEnabled: false, gatewayEnabled: false });
          setPaymentConfigurationError("Unable to verify secure UPI checkout availability. Cash remains available.");
        }
      });
    return () => { active = false; };
  }, []);

  React.useEffect(() => () => {
    if (problemPhotoPreview) URL.revokeObjectURL(problemPhotoPreview);
  }, [problemPhotoPreview]);

  const submissionInProgress = useRef(false);

  const onInputChange = (event) => {
    const { name, value } = event.target;
    setFormData((previous) => ({ ...previous, [name]: value }));
    if (status) setStatus("");
  };

  const onProblemPhotoChange = (event) => {
    const file = event.target.files?.[0] || null;
    if (!file) {
      setProblemPhoto(null);
      setProblemPhotoPreview("");
      return;
    }
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      setStatus("Choose a PNG, JPEG, or WebP image.");
      event.target.value = "";
      return;
    }
    if (file.size > 3 * 1024 * 1024) {
      setStatus("Problem photos must be 3 MB or smaller.");
      event.target.value = "";
      return;
    }
    setStatus("");
    setProblemPhoto(file);
    setProblemPhotoPreview(URL.createObjectURL(file));
  };

  const readProblemPhoto = (file) => new Promise((resolve, reject) => {
    if (!file) {
      resolve(null);
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = String(reader.result || "");
      const separatorIndex = dataUrl.indexOf(",");
      if (separatorIndex < 0) {
        reject(new Error("Could not read the selected problem photo."));
        return;
      }
      resolve(dataUrl.slice(separatorIndex + 1));
    };
    reader.onerror = () => reject(new Error("Could not read the selected problem photo."));
    reader.readAsDataURL(file);
  });

  const useCurrentLocation = () => {
    if (!navigator.geolocation) {
      setLocationStatus("This browser does not support location. Enter your address manually.");
      return;
    }
    setLocationStatus("Requesting location permission...");
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setFormData((previous) => ({
          ...previous,
          latitude: coords.latitude.toString(),
          longitude: coords.longitude.toString(),
        }));
        setLocationStatus("Location added. Your precise coordinates are used only for provider matching and this booking.");
      },
      (error) => {
        const messages = {
          1: "Location permission was denied. You can continue with a manual address.",
          2: "Your location is currently unavailable. You can continue with a manual address.",
          3: "Location request timed out. You can continue with a manual address.",
        };
        setLocationStatus(messages[error.code] || "Could not read your location. Enter your address manually.");
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 },
    );
  };

  const useSimulationCity = (selectedCity) => {
    const coordinates = {
      MUMBAI: [19.0760, 72.8777],
      PUNE: [18.5204, 73.8567],
    }[selectedCity.toUpperCase()];
    if (!coordinates) return;
    setFormData((previous) => ({
      ...previous,
      city: selectedCity,
      latitude: coordinates[0].toString(),
      longitude: coordinates[1].toString(),
    }));
    setLocationStatus(`${selectedCity} development location selected.`);
  };

  const onFormSubmit = async (event) => {
    event.preventDefault();
    if (busy) return;
    if (submissionInProgress.current) return;

    if (currentStep < bookingSteps.length - 1) {
      if (currentStep === 3 && formData.preferredDate < localDateString()) {
        setStatus("Choose today or a future preferred date.");
        return;
      }
      setStatus("");
      setCurrentStep((step) => Math.min(step + 1, bookingSteps.length - 1));
      return;
    }

    if (formData.preferredDate < localDateString()) {
      setStatus("Choose today or a future preferred date.");
      return;
    }

    submissionInProgress.current = true;
    setBusy(true);
    setStatus("");
    try {
      const problemImageBase64 = await readProblemPhoto(problemPhoto);
      await onSubmit({
        ...formData,
        customerName: formData.customerName.trim(),
        address: [
          formData.address.trim(),
          formData.city.trim(),
        ].filter(Boolean).join(", "),
        notes: formData.notes.trim(),
        problemImageBase64,
        paymentMethod: formData.paymentMethod,
        latitude: formData.latitude ? Number(formData.latitude) : null,
        longitude: formData.longitude ? Number(formData.longitude) : null,
        price: service.startingPrice,
      });
    } catch (error) {
      setStatus(
        error.response?.data?.message
          || error.response?.data?.detail
          || (typeof error.response?.data === "string" ? error.response.data : "")
          || error.message
          || "Unable to save your booking. Please try again."
      );
    } finally {
      submissionInProgress.current = false;
      setBusy(false);
    }
  };

  return (
    <section className="custom-card service-form-card" aria-labelledby="booking-form-title">
      <div className="service-form-header">
        <div className="service-form-icon" aria-hidden="true">
          <i className="bi bi-calendar2-check"></i>
        </div>
        <div>
          <span className="eyebrow">SIMPLE, SECURE BOOKING</span>
          <h2 id="booking-form-title">Book {service.name}</h2>
          <p className="small-muted">Tell us what you need and when it works for you.</p>
        </div>
      </div>

      <ol className="booking-wizard-progress" aria-label="Booking steps">
        {bookingSteps.map((step, index) => (
          <li
            key={step.title}
            className={index === currentStep ? "is-current" : index < currentStep ? "is-complete" : ""}
            aria-current={index === currentStep ? "step" : undefined}
          >
            <span className="booking-wizard-marker">{index < currentStep ? "✓" : index + 1}</span>
            <span className="booking-wizard-step-copy">
              <strong>{step.title}</strong>
              <small>{step.description}</small>
            </span>
          </li>
        ))}
      </ol>

      <form onSubmit={onFormSubmit} className="service-form booking-wizard-form">
        <div className="booking-wizard-content" aria-live="polite">
          {currentStep === 0 && (
            <div className="booking-wizard-fields">
              <h3>How can we reach you?</h3>
              <p className="small-muted">We’ll use these details to coordinate your {service.name.toLowerCase()}.</p>
              <div className="form-field">
                <label htmlFor="customerName" className="form-label">Customer name</label>
                <input id="customerName" type="text" name="customerName" className="form-control"
                  value={formData.customerName} onChange={onInputChange}
                  placeholder="Enter your full name" autoComplete="name" maxLength={120} required />
              </div>
              <div className="form-field">
                <label htmlFor="phone" className="form-label">Phone number</label>
                <input id="phone" type="tel" name="phone" className="form-control"
                  value={formData.phone} onChange={onInputChange}
                  placeholder="Enter your 10-digit phone number" autoComplete="tel"
                  inputMode="numeric" pattern="[0-9]{10}" maxLength={10}
                  title="Enter a 10-digit phone number" required />
              </div>
            </div>
          )}

          {currentStep === 1 && (
            <div className="booking-wizard-fields">
              <h3>What exactly needs fixing?</h3>
              <p className="small-muted">A clear description helps the provider understand the work before arriving.</p>
              <div className="form-field form-field-wide">
                <label htmlFor="notes" className="form-label">Describe the problem</label>
                <textarea id="notes" name="notes" className="form-control" rows="4"
                  value={formData.notes} onChange={onInputChange}
                  placeholder="For example: water is leaking under the kitchen sink"
                  maxLength={2000} required />
                <small className="small-muted">Be specific about where the issue is and what you have noticed.</small>
              </div>
              <div className="form-field form-field-wide">
                <label htmlFor="problemPhoto" className="form-label">
                  Add a photo <span className="text-secondary">(Optional)</span>
                </label>
                <input id="problemPhoto" type="file" className="form-control"
                  accept="image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp"
                  onChange={onProblemPhotoChange} />
                <small className="small-muted">PNG, JPEG, or WebP · up to 3 MB. Avoid including people or private documents.</small>
                {problemPhotoPreview && (
                  <div className="booking-problem-photo-preview">
                    <img src={problemPhotoPreview} alt="Preview of the problem photo" />
                    <div>
                      <strong>{problemPhoto?.name}</strong>
                      <span>{(problemPhoto.size / 1024 / 1024).toFixed(2)} MB</span>
                      <button type="button" className="booking-remove-photo"
                        onClick={() => {
                          setProblemPhoto(null);
                          setProblemPhotoPreview("");
                          const input = document.getElementById("problemPhoto");
                          if (input) input.value = "";
                        }}>
                        Remove photo
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {currentStep === 2 && (
            <div className="booking-wizard-fields">
              <h3>Where should we provide the service?</h3>
              <p className="small-muted">Enter the visit address. Location sharing is optional and helps match nearby providers.</p>
              <div className="form-field form-field-wide">
                <label htmlFor="address" className="form-label">Service address</label>
                <textarea id="address" name="address" className="form-control" rows="2"
                  value={formData.address} onChange={onInputChange}
                  placeholder="Enter the complete address for the service"
                  autoComplete="street-address" maxLength={500} required />
              </div>
              <div className="form-field form-field-wide">
                <label htmlFor="city" className="form-label">City <span className="text-secondary">(Optional)</span></label>
                <input id="city" type="text" name="city" className="form-control"
                  value={formData.city} onChange={onInputChange}
                  placeholder="Enter your city" autoComplete="address-level2" maxLength={100} />
              </div>
              <div className="form-field form-field-wide location-actions">
                <button type="button" className="btn btn-outline-primary" onClick={useCurrentLocation}>
                  <i className="bi bi-geo-alt" aria-hidden="true"></i> Use my current location
                </button>
                {simulationCities.map((city) => (
                  <button type="button" className="btn btn-outline-secondary" key={city}
                    onClick={() => useSimulationCity(city)}>
                    Use {city} development location
                  </button>
                ))}
                {locationStatus && <p className="location-status" role="status">{locationStatus}</p>}
              </div>
            </div>
          )}

          {currentStep === 3 && (
            <div className="booking-wizard-fields">
              <h3>When would you like the service?</h3>
              <p className="small-muted">Choose a preferred date and time for your service.</p>
              <div className="form-field">
                <label htmlFor="preferredDate" className="form-label">Preferred date</label>
                <input id="preferredDate" type="date" name="preferredDate" className="form-control"
                  value={formData.preferredDate} onChange={onInputChange}
                  min={localDateString()} required />
              </div>
              <div className="form-field">
                <label htmlFor="preferredTime" className="form-label">Preferred time</label>
                <input id="preferredTime" type="time" name="preferredTime" className="form-control"
                  value={formData.preferredTime} onChange={onInputChange} required />
              </div>
            </div>
          )}

          {currentStep === 4 && (
            <div className="booking-wizard-fields">
              <h3>Choose your payment method</h3>
              <p className="small-muted">No payment is taken during booking.</p>
              <fieldset className="form-field form-field-wide payment-method-fieldset">
                <legend className="form-label">How would you like to pay?</legend>
                <div className="payment-method-options">
                  <label className={formData.paymentMethod === "CASH" ? "selected" : ""}>
                    <input type="radio" name="paymentMethod" value="CASH"
                      checked={formData.paymentMethod === "CASH"} onChange={onInputChange} />
                    <i className="bi bi-cash-stack" aria-hidden="true"></i>
                    <span><strong>Cash</strong><small>Pay the provider after the work.</small></span>
                  </label>
                  <label className={`${formData.paymentMethod === "UPI" ? "selected" : ""} ${paymentConfiguration.gatewayEnabled ? "" : "disabled"}`}>
                    <input type="radio" name="paymentMethod" value="UPI"
                      checked={formData.paymentMethod === "UPI"} onChange={onInputChange}
                      disabled={!paymentConfiguration.gatewayEnabled} />
                    <i className="bi bi-qr-code" aria-hidden="true"></i>
                    <span><strong>UPI via Razorpay</strong><small>{paymentConfiguration.gatewayEnabled
                      ? "Pay securely after the provider completes the service."
                      : "Secure UPI checkout is not configured."}</small></span>
                  </label>
                </div>
                {paymentConfigurationError && (
                  <p className="payment-disclaimer" role="status">{paymentConfigurationError}</p>
                )}
                {formData.paymentMethod === "UPI" && paymentConfiguration.gatewayEnabled && (
                  <p className="payment-method-note" role="status">
                    Razorpay will verify the transaction before HomeFix marks it paid.
                  </p>
                )}
              </fieldset>
            </div>
          )}

          {currentStep === 5 && (
            <div className="booking-wizard-fields">
              <h3>Review your booking</h3>
              <p className="small-muted">Check the details before sending your request to eligible providers.</p>
              <dl className="booking-review-summary">
                <div><dt>Service</dt><dd>{service.name}</dd></div>
                <div><dt>Customer</dt><dd>{formData.customerName}</dd></div>
                <div><dt>Phone</dt><dd>{formData.phone}</dd></div>
                <div><dt>Service address</dt><dd>{[formData.address, formData.city].filter(Boolean).join(", ")}</dd></div>
                <div><dt>Preferred time</dt><dd>{formData.preferredDate} · {formData.preferredTime}</dd></div>
                <div><dt>Payment method</dt><dd>{formData.paymentMethod === "UPI" ? "UPI via Razorpay" : "Cash"}</dd></div>
                <div><dt>Problem</dt><dd>{formData.notes}</dd></div>
                <div>
                  <dt>Problem photo</dt>
                  <dd>
                    {problemPhoto ? (
                      <span className="booking-review-photo">
                        <img src={problemPhotoPreview} alt="" />
                        {problemPhoto.name}
                      </span>
                    ) : "No photo added"}
                  </dd>
                </div>
                <div className="booking-review-price">
                  <dt>Starting price</dt><dd>₹{service.startingPrice}</dd>
                </div>
              </dl>
              <p className="payment-disclaimer">
                This is a starting estimate. The server confirms the final saved booking amount.
              </p>
            </div>
          )}
        </div>

        <div className="starting-price" role="status">
          <span className="price-icon" aria-hidden="true"><i className="bi bi-tag"></i></span>
          <span><small>{service.name} starting from</small><strong>₹{service.startingPrice}</strong></span>
          <small className="price-note">The server confirms the saved booking price.</small>
        </div>

        {status && <p className="booking-form-error" role="alert">{status}</p>}

        <div className="booking-wizard-actions">
          <button type="button" className="booking-wizard-back"
            onClick={() => { setStatus(""); setCurrentStep((step) => Math.max(0, step - 1)); }}
            disabled={currentStep === 0 || busy}>
            Back
          </button>
          <span className="booking-wizard-step-count">Step {currentStep + 1} of {bookingSteps.length}</span>
          <button type="submit" className="service-submit-btn" disabled={busy}>
            {busy ? (
              <><span className="spinner-border spinner-border-sm" aria-hidden="true"></span> Saving booking...</>
            ) : currentStep === bookingSteps.length - 1 ? (
              <><i className="bi bi-check2-circle" aria-hidden="true"></i> Confirm Booking</>
            ) : (
              <>Continue <i className="bi bi-arrow-right" aria-hidden="true"></i></>
            )}
          </button>
        </div>

        <p className="form-privacy-note">
          <i className="bi bi-shield-check me-1"></i>
          Your details are only used to arrange your service.
        </p>
      </form>
    </section>
  );
}

export default ServiceRequestForm;
