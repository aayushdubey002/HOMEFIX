import React, { useEffect, useState } from "react";
import { fetchProviderProfile, fetchProviderVerificationDocuments } from "../services/apiService";

const requiredDocumentTypes = ["IDENTITY_PROOF", "SERVICE_ADDRESS_PROOF"];

export default function ProviderApplicationStatusPage({ onContinue }) {
  const [profile, setProfile] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshVersion, setRefreshVersion] = useState(0);

  useEffect(() => {
    let active = true;
    let firstLoad = true;

    const loadStatus = async () => {
      if (firstLoad && active) setLoading(true);
      try {
        const [profileResponse, documentsResponse] = await Promise.all([
          fetchProviderProfile(),
          fetchProviderVerificationDocuments(),
        ]);
        if (!active) return;
        setProfile(profileResponse.data || {});
        setDocuments(documentsResponse.data || []);
        setError("");
      } catch (requestError) {
        if (!active) return;
        setError(
          requestError.response?.data?.message
            || "Unable to load your application status. Please try again."
        );
      } finally {
        if (active) {
          firstLoad = false;
          setLoading(false);
        }
      }
    };

    loadStatus();
    const intervalId = window.setInterval(loadStatus, 15000);
    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, [refreshVersion]);

  const status = profile?.verificationStatus?.toUpperCase() || "PENDING";
  const latestDocuments = new Map();
  documents.forEach((document) => {
    if (!latestDocuments.has(document.documentType)) {
      latestDocuments.set(document.documentType, document);
    }
  });
  const missingRequiredDocuments = requiredDocumentTypes.filter(
    (type) => !latestDocuments.has(type)
  );
  const rejectedDocuments = requiredDocumentTypes
    .map((type) => latestDocuments.get(type))
    .filter((document) => document?.status === "REJECTED");

  let state = "pending";
  if (status === "APPROVED") state = "approved";
  else if (status === "REJECTED" || rejectedDocuments.length > 0) state = "rejected";

  return (
    <main className="provider-application-page">
      <section className={`provider-application-panel state-${state}`} aria-live="polite">
        <span className="provider-application-mark" aria-hidden="true">
          <i className={`bi ${state === "approved" ? "bi-check-lg" : state === "rejected" ? "bi-arrow-repeat" : "bi-hourglass-split"}`}></i>
        </span>

        {loading && !profile ? (
          <div role="status" className="provider-application-copy">
            <span className="eyebrow">PROVIDER APPLICATION</span>
            <h1>Checking your application</h1>
            <p>Loading your latest verification status.</p>
          </div>
        ) : error && !profile ? (
          <div className="provider-application-copy">
            <span className="eyebrow">PROVIDER APPLICATION</span>
            <h1>We couldn’t load your status</h1>
            <p role="alert">{error}</p>
            <button type="button" onClick={() => setRefreshVersion((value) => value + 1)}>
              Try again
            </button>
          </div>
        ) : state === "approved" ? (
          <div className="provider-application-copy">
            <span className="eyebrow">APPLICATION APPROVED</span>
            <h1>Congratulations, you’re approved!</h1>
            <p>Your provider verification is complete. Head to your workspace and go online to start receiving service requests.</p>
            <button type="button" onClick={onContinue}>Start working</button>
          </div>
        ) : state === "rejected" ? (
          <div className="provider-application-copy">
            <span className="eyebrow">APPLICATION NEEDS AN UPDATE</span>
            <h1>Sorry, your application wasn’t approved</h1>
            <p>Review the document feedback, upload corrected documents, and submit your application again.</p>
            {rejectedDocuments.map((document) => (
              <p className="provider-application-feedback" key={document.id}>
                {document.originalFilename}: {document.reviewNote || "Please upload a clearer or correct document."}
              </p>
            ))}
            <button type="button" onClick={onContinue}>Update documents and reapply</button>
          </div>
        ) : missingRequiredDocuments.length > 0 ? (
          <div className="provider-application-copy">
            <span className="eyebrow">PROFILE SUBMITTED</span>
            <h1>Complete your verification documents</h1>
            <p>Your profile is saved. Upload identity proof and proof of service address so the admin can review your application.</p>
            <button type="button" onClick={onContinue}>Upload documents</button>
          </div>
        ) : (
          <div className="provider-application-copy">
            <span className="eyebrow">APPLICATION SUBMITTED</span>
            <h1>Please wait for admin approval</h1>
            <p>Your required documents are submitted and your application is waiting for HomeFix admin review. This page will show the result when it’s ready.</p>
            <button type="button" className="provider-application-refresh" onClick={() => setRefreshVersion((value) => value + 1)}>
              Check status now
            </button>
          </div>
        )}
      </section>

      {error && profile && <p className="provider-application-error" role="alert">{error}</p>}
      {loading && profile && <p className="provider-application-updating" role="status">Checking for status updates...</p>}
    </main>
  );
}