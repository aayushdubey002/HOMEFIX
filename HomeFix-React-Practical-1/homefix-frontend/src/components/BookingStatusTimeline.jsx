import React from "react";

const steps = [
  ["PENDING", "Request sent"],
  ["ACCEPTED", "Provider accepted"],
  ["CONFIRMED", "Appointment confirmed"],
  ["ON_THE_WAY", "Provider on the way"],
  ["ARRIVED", "Provider arrived"],
  ["IN_PROGRESS", "Work in progress"],
  ["COMPLETED", "Completed"],
];

const terminalMessages = {
  CANCELLED: "This booking was cancelled.",
  REJECTED: "This booking was rejected. Please contact HomeFix for help.",
};

const statusMessages = {
  PENDING: "Your request is waiting for an eligible provider to accept it.",
  ACCEPTED: "A provider accepted your request. The appointment is being confirmed.",
  CONFIRMED: "Your appointment is confirmed.",
  ON_THE_WAY: "Your provider is on the way.",
  ARRIVED: "Your provider has arrived.",
  IN_PROGRESS: "Your service is in progress.",
  COMPLETED: "Your service is complete.",
};

export default function BookingStatusTimeline({ status }) {
  const normalizedStatus = String(status || "UNKNOWN")
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, "_");
  const currentIndex = steps.findIndex(([step]) => step === normalizedStatus);
  const terminalMessage = terminalMessages[normalizedStatus];

  return (
    <section
      className="booking-status-timeline"
      aria-label="Booking progress"
      aria-live="polite"
    >
      <h2>Booking progress</h2>
      <p>{statusMessages[normalizedStatus] || (terminalMessage
        ? "This booking is closed."
        : "Booking status is being updated.")}</p>
      {terminalMessage && (
        <p className="booking-status-terminal" role="status">{terminalMessage}</p>
      )}
      <ol
        className="booking-status-steps"
        style={{ "--booking-progress": `${Math.max(0, currentIndex) / (steps.length - 1) * 100}%` }}
      >
        {steps.map(([step, label], index) => {
          const complete = currentIndex >= 0 && index < currentIndex;
          const current = index === currentIndex;
          return (
            <li
              className={`booking-status-step${complete ? " is-complete" : ""}${current ? " is-current" : ""}`}
              key={step}
              aria-current={current ? "step" : undefined}
            >
              <span className="booking-status-step-marker" aria-hidden="true">
                {complete ? <i className="bi bi-check-lg" /> : index + 1}
              </span>
              <span>{label}</span>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
