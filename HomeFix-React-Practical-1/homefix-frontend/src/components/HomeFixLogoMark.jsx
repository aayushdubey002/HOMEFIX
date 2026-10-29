import React from "react";

export default function HomeFixLogoMark({ className }) {
  return (
    <svg
      className={className}
      viewBox="0 0 64 64"
      fill="none"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id="homefix-logo-gradient" x1="7" y1="5" x2="57" y2="59" gradientUnits="userSpaceOnUse">
          <stop stopColor="#2786F5" />
          <stop offset="1" stopColor="#0754AA" />
        </linearGradient>
      </defs>
      <rect x="2" y="2" width="60" height="60" rx="19" fill="url(#homefix-logo-gradient)" />
      <path
        d="M14 30.5 32 15l18 15.5M20 27v21h24V27"
        stroke="white"
        strokeWidth="3.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M27 48V34h10v14M24 39h16"
        stroke="white"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="47.5" cy="17.5" r="6.5" fill="#37C9B4" />
      <path
        d="m44.8 17.4 1.8 1.8 3.8-4"
        stroke="white"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
