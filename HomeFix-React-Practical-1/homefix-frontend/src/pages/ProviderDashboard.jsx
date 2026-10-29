import React, { useEffect, useState } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import BookingTable from "../components/BookingTable";
import {
  fetchProviderAvailability,
  fetchProviderDashboard,
  fetchProviderProfile,
  fetchProviderVerificationDocuments,
  fetchPaymentConfiguration,
  updateProviderAvailability,
  updateProviderLocation,
  updateProviderProfile,
  uploadProviderVerificationDocument,
} from "../services/apiService";

const verificationDocumentTypes = [
  ["IDENTITY_PROOF", "Government-issued identity proof"],
  ["SERVICE_ADDRESS_PROOF", "Proof of service address"],
  ["TRADE_CERTIFICATE", "Trade or skill certificate (optional)"],
];

const weekdays = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

const formatRequestError = (error, action) => {
  const status = error.response?.status;
  const responseBody = error.response?.data;
  const message = typeof responseBody === "string"
    ? responseBody
    : responseBody?.message
      || responseBody?.detail
      || responseBody?.title
      || error.message;

  return status
    ? `${action} failed (HTTP ${status})${message ? `: ${message}` : ""}`
    : `${action} failed: ${message || "Unknown request error."}`;
};

export default function ProviderDashboard({
  user,
  onApplicationStatus,
  bookings,
  loading,
  actionLoading,
  onAccept,
  onReject,
  onUpdateStatus,
  onProposePrice,
  error,
  onRefresh,
}) {
  const [profile, setProfile] = useState(null);
  const [metrics, setMetrics] = useState(null);
  const [availability, setAvailability] = useState({});
  const [summary, setSummary] = useState("");
  const [radius, setRadius] = useState(25);
  const [city, setCity] = useState("");
  const [workspaceBusy, setWorkspaceBusy] = useState(true);
  const [workspaceMessage, setWorkspaceMessage] = useState("");
  const [verificationDocuments, setVerificationDocuments] = useState([]);
  const [documentType, setDocumentType] = useState("IDENTITY_PROOF");
  const [documentFile, setDocumentFile] = useState(null);
  const [documentUploadBusy, setDocumentUploadBusy] = useState(false);
  const [documentInputKey, setDocumentInputKey] = useState(0);
  const [paymentConfiguration, setPaymentConfiguration] = useState({
    gatewayEnabled: false,
    configurationMessage: "Checking secure UPI checkout availability.",
  });
  const location = useLocation();
  const navigate = useNavigate();

  const refreshProviderWorkspace = async () => {
    setWorkspaceBusy(true);
    setWorkspaceMessage("");

    try {
      const endpointNames = [
        "provider profile",
        "provider dashboard",
        "working hours",
        "verification documents",
      ];
      const responses = await Promise.allSettled([
        fetchProviderProfile(),
        fetchProviderDashboard(),
        fetchProviderAvailability(),
        fetchProviderVerificationDocuments(),
      ]);
      const failedRequest = responses.findIndex(
        (response) => response.status === "rejected"
      );

      if (failedRequest !== -1) {
        throw new Error(
          formatRequestError(
            responses[failedRequest].reason,
            `Loading ${endpointNames[failedRequest]}`
          )
        );
      }

      const [
        profileResponse,
        metricsResponse,
        availabilityResponse,
        documentsResponse,
      ] = responses.map((response) => response.value);

      const loadedProfile = profileResponse.data || {};

      setProfile(loadedProfile);
      setMetrics(metricsResponse.data);
      setVerificationDocuments(documentsResponse.data || []);
      setSummary(loadedProfile.professionalSummary || "");
      setRadius(loadedProfile.serviceRadiusKm || 25);

      const saved = Object.fromEntries(
        weekdays.map((day) => [
          day,
          {
            enabled: false,
            startTime: "09:00",
            endTime: "17:00",
          },
        ])
      );

      (availabilityResponse.data || []).forEach((slot) => {
        saved[slot.dayOfWeek] = {
          enabled: slot.enabled,
          startTime: slot.startTime
            ? slot.startTime.slice(0, 5)
            : "09:00",
          endTime: slot.endTime
            ? slot.endTime.slice(0, 5)
            : "17:00",
        };
      });
      setAvailability(saved);
      return { documents: documentsResponse.data || [], error: null };
    } catch (requestError) {
      const message =
        requestError.message ||
        "Unable to load provider profile and settings.";
      setWorkspaceMessage(message);
      return { documents: null, error: message };
    } finally {
      setWorkspaceBusy(false);
    }
  };

  useEffect(() => {
    refreshProviderWorkspace();
  }, []);

  useEffect(() => {
    let active = true;
    fetchPaymentConfiguration()
      .then(({ data }) => {
        if (active) {
          setPaymentConfiguration(data || {
            gatewayEnabled: false,
            configurationMessage: "Secure UPI checkout is unavailable.",
          });
        }
      })
      .catch(() => {
        if (active) {
          setPaymentConfiguration({
            gatewayEnabled: false,
            configurationMessage: "UPI availability could not be verified. Cash is still available.",
          });
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const validPaths = [
      "/provider",
      "/provider/requests",
      "/provider/profile",
      "/provider/availability",
      "/provider/verification",
    ];
    if (validPaths.includes(location.pathname)) {
      return;
    }
    if (location.pathname.startsWith("/provider/")) {
      navigate("/provider", { replace: true });
    }
  }, [location.pathname, navigate]);

  const uploadVerificationDocument = async (event) => {
    event.preventDefault();
    if (!documentFile) {
      setWorkspaceMessage("Choose a PDF, PNG, or JPEG document to upload.");
      return;
    }
    if (documentFile.size > 10 * 1024 * 1024) {
      setWorkspaceMessage("Documents must be 10 MB or smaller.");
      return;
    }

    setDocumentUploadBusy(true);
    setWorkspaceMessage("");
    const submittedDocumentType = uploadableDocumentTypes.some(
      ([type]) => type === documentType
    )
      ? documentType
      : uploadableDocumentTypes[0]?.[0];
    if (!submittedDocumentType) {
      setWorkspaceMessage("No document type is currently available for upload.");
      setDocumentUploadBusy(false);
      return;
    }

    try {
      await uploadProviderVerificationDocument(submittedDocumentType, documentFile);
      setDocumentFile(null);
      setDocumentInputKey((key) => key + 1);
      await refreshProviderWorkspace();
      setWorkspaceMessage("Document uploaded. It is waiting for admin review.");
    } catch (requestError) {
      if (requestError.response?.status === 409) {
        const refreshResult = await refreshProviderWorkspace();
        const existingDocument = requestError.response?.data?.document;
        if (existingDocument?.documentType === submittedDocumentType) {
          setVerificationDocuments((current) => [
            existingDocument,
            ...current.filter(
              (document) => document.documentType !== existingDocument.documentType
            ),
          ]);
        }
        setDocumentFile(null);
        setDocumentInputKey((key) => key + 1);
        if (existingDocument?.documentType === submittedDocumentType) {
          const label = verificationDocumentTypes.find(
            ([type]) => type === submittedDocumentType
          )?.[1] || existingDocument.documentType.replaceAll("_", " ").toLowerCase();
          setWorkspaceMessage(
            `${label} is already ${existingDocument.status?.toLowerCase() || "submitted"}. The existing document is shown below; upload a replacement only if it was rejected.`
          );
        } else if (existingDocument?.documentType) {
          setWorkspaceMessage(
            `The server reported a conflict for a different document type (${existingDocument.documentType.replaceAll("_", " ")}). ${refreshResult.error || "Your selected document status is unchanged."} Refresh the page or contact an administrator before trying again.`
          );
        } else if (refreshResult.error) {
          setWorkspaceMessage(
            `${formatRequestError(requestError, "Uploading verification document")} ${refreshResult.error}`
          );
        } else if (refreshResult.documents?.some(
          (document) => document.documentType === submittedDocumentType
        )) {
          setWorkspaceMessage(
            "This document is already submitted or approved. Its current status is shown above; upload a replacement only if it was rejected."
          );
        } else {
          setWorkspaceMessage(
            "The server rejected this upload, but did not return a matching document record. The refreshed list still shows no document for this type. Refresh the page or contact an administrator before trying again."
          );
        }
        return;
      }
      setWorkspaceMessage(
        formatRequestError(requestError, "Uploading verification document")
      );
    } finally {
      setDocumentUploadBusy(false);
    }
  };

  const saveProfile = async (event) => {
    event.preventDefault();
    setWorkspaceBusy(true);
    setWorkspaceMessage("");

    try {
      await updateProviderProfile({
        professionalSummary: summary,
        serviceRadiusKm: Number(radius),
      });

      setWorkspaceMessage("Provider profile settings saved.");
      await refreshProviderWorkspace();
    } catch (requestError) {
      setWorkspaceMessage(
        formatRequestError(requestError, "Saving provider profile")
      );
    } finally {
      setWorkspaceBusy(false);
    }
  };

  const setOnline = async () => {
    if (!profile) return;

    setWorkspaceBusy(true);
    setWorkspaceMessage("");

    try {
      await updateProviderProfile({ online: !profile.online });

      setWorkspaceMessage(
        profile.online ? "You are now offline." : "You are now online."
      );

      await refreshProviderWorkspace();
    } catch (requestError) {
      setWorkspaceMessage(
        formatRequestError(requestError, "Updating provider status")
      );
    } finally {
      setWorkspaceBusy(false);
    }
  };

  const saveLocation = async (location) => {
    setWorkspaceBusy(true);
    setWorkspaceMessage("");

    try {
      await updateProviderLocation(location);

      setWorkspaceMessage("Provider service location saved.");
      setCity("");

      await refreshProviderWorkspace();
    } catch (requestError) {
      setWorkspaceMessage(
        formatRequestError(requestError, "Saving provider location")
      );
    } finally {
      setWorkspaceBusy(false);
    }
  };

  const saveWorkingHours = async () => {
    setWorkspaceBusy(true);
    setWorkspaceMessage("");

    try {
      const slots = weekdays
        .filter((day) => availability[day]?.enabled)
        .map((day) => ({
          dayOfWeek: day,
          startTime: availability[day].startTime,
          endTime: availability[day].endTime,
          enabled: true,
        }));

      await updateProviderAvailability(slots);

      setWorkspaceMessage("Working hours saved.");
      await refreshProviderWorkspace();
    } catch (requestError) {
      setWorkspaceMessage(
        formatRequestError(requestError, "Saving working hours")
      );
    } finally {
      setWorkspaceBusy(false);
    }
  };

  const normalizeCategory = (category) => {
    const normalized = category
      ?.trim()
      .replace(/[-_]+/g, " ")
      .replace(/\s+/g, " ")
      .toUpperCase();

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

  const category = normalizeCategory(user?.serviceCategory);

  const matchingBookings = (bookings || []).filter(
    (booking) =>
      category &&
      normalizeCategory(booking.service) === category
  );
  const activeSection = {
    "/provider": "overview",
    "/provider/requests": "requests",
    "/provider/profile": "profile",
    "/provider/availability": "availability",
    "/provider/verification": "verification",
  }[location.pathname] || "overview";
  const pageTitles = {
    overview: "Provider overview",
    requests: "Service requests",
    profile: "Your profile",
    availability: "Availability & location",
    verification: "Document verification",
  };
  const latestDocumentByType = new Map(
    verificationDocuments.map((document) => [document.documentType, document])
  );
  const approvedRequiredDocuments = ["IDENTITY_PROOF", "SERVICE_ADDRESS_PROOF"]
    .every((type) => latestDocumentByType.get(type)?.status === "APPROVED");
  const hasRejectedRequiredDocument = ["IDENTITY_PROOF", "SERVICE_ADDRESS_PROOF"]
    .some((type) => latestDocumentByType.get(type)?.status === "REJECTED");
  const uploadableDocumentTypes = profile?.verificationStatus === "APPROVED"
    || (approvedRequiredDocuments && !hasRejectedRequiredDocument)
    ? []
    : verificationDocumentTypes.filter(([type]) => {
      const currentDocument = latestDocumentByType.get(type);
      return !currentDocument || currentDocument.status === "REJECTED";
    });

  return (
    <main className="dashboard provider-dashboard">
      <div className="welcome-banner provider-welcome">
        <div className="welcome-copy">
          <span className="eyebrow">HOMEFIX PROVIDER PORTAL</span>
          <h1>{pageTitles[activeSection]}</h1>
          <p>
            {activeSection === "overview"
              ? `Welcome back, ${user?.fullName || "Provider"}. Keep your work organized in one place.`
              : activeSection === "requests"
                ? "Review matching jobs and manage accepted work."
                : activeSection === "profile"
                  ? "Keep your professional information up to date for customers."
                  : activeSection === "availability"
                    ? "Set the times and service area that work for you."
                    : "Track your document review and submit only what is needed."}
          </p>
          {activeSection === "overview" && (
            <Link className="provider-primary-link" to="/provider/requests">
              <i className="bi bi-inbox" aria-hidden="true"></i>
              View service requests
            </Link>
          )}
        </div>
        <div className="provider-welcome-status">
          <span className={`provider-status-dot ${profile?.online ? "is-online" : ""}`}></span>
          <span>{profile?.online ? "Online and available" : "Currently offline"}</span>
        </div>
      </div>

      <nav className="provider-workspace-nav" aria-label="Provider workspace pages">
        <NavLink end to="/provider"><i className="bi bi-grid-1x2" aria-hidden="true"></i>Overview</NavLink>
        <NavLink to="/provider/requests"><i className="bi bi-inbox" aria-hidden="true"></i>Requests</NavLink>
        <NavLink to="/provider/availability"><i className="bi bi-calendar-week" aria-hidden="true"></i>Availability</NavLink>
        <NavLink to="/provider/profile"><i className="bi bi-person" aria-hidden="true"></i>Profile</NavLink>
        <NavLink to="/provider/verification"><i className="bi bi-shield-check" aria-hidden="true"></i>Verification</NavLink>
      </nav>

      <section
        className="custom-card provider-workspace-card"
        aria-labelledby="provider-workspace-title"
      >
        <div className="provider-profile-heading">
          <div>
            <span className="eyebrow">PROVIDER WORKSPACE</span>
            <h2 id="provider-workspace-title">{pageTitles[activeSection]}</h2>
          </div>

          <button
            type="button"
            className={`provider-online-toggle ${profile?.online ? "is-online" : ""}`}
            onClick={setOnline}
            disabled={
              workspaceBusy ||
              !profile ||
              profile.verificationStatus !== "APPROVED"
            }
          >
            {profile?.online ? "Go offline" : "Go online"}
          </button>
        </div>

        {profile && activeSection === "overview" && (
          <p className="provider-verification-hint" role="status">
            Verification: <strong>{profile.verificationStatus?.replaceAll("_", " ")}</strong>
            {" · "}
            {profile.online ? "Online" : "Offline"}
            {profile.verificationStatus !== "APPROVED"
              && " · Complete document review before going online."}
          </p>
        )}

        <section
          className="provider-verification-documents"
          aria-labelledby="provider-documents-title"
          hidden={activeSection !== "verification"}
        >
          <div className="provider-verification-heading">
            <div>
              <span className="eyebrow">ACCOUNT VERIFICATION</span>
              <h3 id="provider-documents-title">Documents for verification</h3>
            </div>
            <span className={`provider-verification-status status-${(profile?.verificationStatus || "PENDING").toLowerCase()}`}>
              {(profile?.verificationStatus || "PENDING").replaceAll("_", " ")}
            </span>
          </div>
          <p className="small-muted">
            We accept PDF, PNG, and JPEG documents up to 10 MB. Approved documents are locked and will not ask you to upload them again.
          </p>
          <ul className="provider-document-list">
            {verificationDocumentTypes.map(([type, label]) => {
              const document = latestDocumentByType.get(type);
              return (
                <li key={type} className="provider-document-item">
                  <i className="bi bi-file-earmark-check" aria-hidden="true"></i>
                  <span>{label}</span>
                  <strong className={`provider-document-state status-${(document?.status || "MISSING").toLowerCase()}`}>
                    {document?.status || "NOT UPLOADED"}
                  </strong>
                  {document && (
                    <small>
                      {document.originalFilename}
                      {document.reviewNote && ` · ${document.reviewNote}`}
                    </small>
                  )}
                </li>
              );
            })}
          </ul>
          {uploadableDocumentTypes.length > 0 ? (
            <form className="provider-document-upload" onSubmit={uploadVerificationDocument}>
            <label>
              <span className="form-label">Document type</span>
              <select
                className="form-control"
                value={uploadableDocumentTypes.some(([type]) => type === documentType)
                  ? documentType : uploadableDocumentTypes[0]?.[0] || ""}
                onChange={(event) => setDocumentType(event.target.value)}
                disabled={documentUploadBusy}
              >
                {uploadableDocumentTypes.map(([type, label]) => (
                  <option value={type} key={type}>{label}</option>
                ))}
              </select>
            </label>
            <label>
              <span className="form-label">Choose file</span>
              <input
                key={documentInputKey}
                className="form-control"
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                onChange={(event) => setDocumentFile(event.target.files?.[0] || null)}
                disabled={documentUploadBusy}
              />
            </label>
            <button type="submit" disabled={documentUploadBusy || !documentFile}>
              {documentUploadBusy ? "Uploading..." : "Upload document"}
            </button>
            </form>
          ) : (
            <div className="provider-verification-complete" role="status">
              <i className={`bi ${profile?.verificationStatus === "APPROVED"
                ? "bi-shield-fill-check" : approvedRequiredDocuments ? "bi-hourglass-split" : "bi-check-circle-fill"}`}
              aria-hidden="true"></i>
              <div>
                <strong>{profile?.verificationStatus === "APPROVED"
                  ? "Your provider account is verified"
                  : approvedRequiredDocuments
                    ? "Documents verified — review is in progress"
                    : "Your submitted documents are under review"}</strong>
                <span>{profile?.verificationStatus === "APPROVED"
                  ? "Your approved documents are saved. No further upload is required."
                  : "You can upload a replacement if a reviewer rejects a document."}</span>
              </div>
            </div>
          )}
          <Link className="provider-application-link" to="/provider/application-status">
            View application review details
          </Link>
        </section>

        {activeSection === "overview" && (
          <>
            <div className="provider-overview-cards">
              <Link to="/provider/requests" className="provider-overview-card">
                <i className="bi bi-inbox" aria-hidden="true"></i>
                <span>Matching service requests</span>
                <strong>{matchingBookings.filter((booking) => booking.status === "PENDING").length}</strong>
                <small>View and respond to jobs</small>
              </Link>
              <Link to="/provider/availability" className="provider-overview-card">
                <i className="bi bi-calendar-week" aria-hidden="true"></i>
                <span>Working schedule</span>
                <strong>{Object.values(availability).filter((day) => day.enabled).length} days</strong>
                <small>Update your availability</small>
              </Link>
              <Link to="/provider/verification" className="provider-overview-card">
                <i className="bi bi-shield-check" aria-hidden="true"></i>
                <span>Account verification</span>
                <strong>{profile?.verificationStatus?.replaceAll("_", " ") || "Loading"}</strong>
                <small>Review your submitted documents</small>
              </Link>
            </div>
          </>
        )}

        {activeSection === "overview" && metrics && (
          <div className="provider-details-grid">
            <div className="provider-detail">
              <div>
                <small>Completed jobs</small>
                <strong>{metrics.completedJobs}</strong>
              </div>
            </div>

            <div className="provider-detail">
              <div>
                <small>Assigned jobs</small>
                <strong>{metrics.acceptedJobs}</strong>
              </div>
            </div>

            <div className="provider-detail">
              <div>
                <small>Recorded earnings</small>
                <strong>₹{metrics.earnings}</strong>
              </div>
            </div>

            <div className="provider-detail">
              <div>
                <small>Reviews</small>
                <strong>{metrics.reviewCount}</strong>
              </div>
            </div>
          </div>
        )}

        {activeSection === "profile" && (
        <form className="service-form" onSubmit={saveProfile}>
          <label className="form-field form-field-wide">
            <span className="form-label">Professional summary</span>
            <textarea
              className="form-control"
              maxLength={2000}
              rows="3"
              value={summary}
              onChange={(event) => setSummary(event.target.value)}
            />
          </label>

          <label className="form-field">
            <span className="form-label">Service radius (km)</span>
            <input
              className="form-control"
              type="number"
              min="1"
              max="200"
              step="1"
              value={radius}
              onChange={(event) => setRadius(event.target.value)}
            />
          </label>

          <button type="submit" disabled={workspaceBusy}>
            Save profile
          </button>
        </form>
        )}

        {activeSection === "availability" && (
        <section className="provider-availability-content">
        <div className="provider-settings-block">
          <h3>Service location</h3>

          <div className="provider-location-simulation">
            <input
              className="form-control"
              value={city}
              onChange={(event) => setCity(event.target.value)}
              placeholder="Mumbai or Pune (development mode)"
            />

            <button
              type="button"
              className="btn btn-outline-secondary"
              disabled={workspaceBusy || !city.trim()}
              onClick={() => saveLocation({ city: city.trim() })}
            >
              Save city
            </button>
          </div>

          <p className="small-muted">
            Current coordinates are kept private and used for nearby service matching.
          </p>
        </div>

        <div className="provider-settings-block">
          <h3>Working hours</h3>

          <div className="provider-hours-grid">
            {weekdays.map((day) => (
              <label key={day}>
                <input
                  type="checkbox"
                  checked={Boolean(availability[day]?.enabled)}
                  onChange={(event) =>
                    setAvailability((current) => ({
                      ...current,
                      [day]: {
                        ...current[day],
                        enabled: event.target.checked,
                      },
                    }))
                  }
                />

                <span>{day.slice(0, 3)}</span>

                <input
                  aria-label={`${day} start time`}
                  type="time"
                  value={availability[day]?.startTime || "09:00"}
                  disabled={!availability[day]?.enabled}
                  onChange={(event) =>
                    setAvailability((current) => ({
                      ...current,
                      [day]: {
                        ...current[day],
                        startTime: event.target.value,
                      },
                    }))
                  }
                />

                <input
                  aria-label={`${day} end time`}
                  type="time"
                  value={availability[day]?.endTime || "17:00"}
                  disabled={!availability[day]?.enabled}
                  onChange={(event) =>
                    setAvailability((current) => ({
                      ...current,
                      [day]: {
                        ...current[day],
                        endTime: event.target.value,
                      },
                    }))
                  }
                />
              </label>
            ))}
          </div>

          <button
            type="button"
            onClick={saveWorkingHours}
            disabled={workspaceBusy}
          >
            Save working hours
          </button>
        </div>
        </section>
        )}

        {workspaceMessage && (
          <p className="bookings-error" role="status">
            {workspaceMessage}
          </p>
        )}

      </section>

      <section
        className="provider-profile custom-card"
        aria-labelledby="provider-profile-title"
        hidden={activeSection !== "profile"}
      >
        <div className="provider-profile-heading">
          <span className="provider-avatar" aria-hidden="true">
            <i className="bi bi-person-badge"></i>
          </span>

          <div>
            <span className="eyebrow">YOUR PROFILE</span>
            <h2 id="provider-profile-title">Provider details</h2>
          </div>
        </div>

        <div className="provider-details-grid">
          {[
            ["bi-person", "Provider name", user?.fullName],
            ["bi-tools", "Service category", user?.serviceCategory],
            ["bi-award", "Experience", user?.experience],
            ["bi-telephone", "Phone number", user?.phone],
            ["bi-geo-alt", "Service area", user?.serviceArea],
          ].map(([icon, label, value]) => (
            <div className="provider-detail" key={label}>
              <span className="provider-detail-icon" aria-hidden="true">
                <i className={`bi ${icon}`}></i>
              </span>

              <div>
                <small>{label}</small>
                <strong>{value || "Not provided"}</strong>
              </div>
            </div>
          ))}
        </div>
      </section>

      {error && activeSection === "requests" && (
        <div className="bookings-error" role="alert">
          <span>{error}</span>
          <button
            type="button"
            onClick={onRefresh}
            disabled={loading}
          >
            Try again
          </button>
        </div>
      )}

      {activeSection === "requests" && (
      <section className="bookings-section">
        <div className="provider-request-heading">
          <div>
            <span className="eyebrow">MATCHED TO YOUR SERVICE</span>
            <h2>Requests for {user?.serviceCategory || "your service"}</h2>
          </div>
          <span>{matchingBookings.length} requests</span>
        </div>
        <BookingTable
          bookings={matchingBookings}
          user={user}
          loading={loading}
          onAccept={onAccept}
          onReject={onReject}
          onUpdateStatus={onUpdateStatus}
          onProposePrice={onProposePrice}
          upiEnabled={paymentConfiguration.gatewayEnabled}
          paymentConfigurationMessage={paymentConfiguration.configurationMessage}
          actionLoading={actionLoading}
          summaryOnly
        />
      </section>
      )}
    </main>
  );
}