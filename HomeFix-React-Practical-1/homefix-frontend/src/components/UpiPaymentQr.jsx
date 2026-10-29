import React from "react";
import { QRCodeSVG } from "qrcode.react";

export function createUpiPaymentUri({ upiId, amount, bookingId, merchantName = "HomeFix" }) {
  const params = new URLSearchParams({
    pa: upiId,
    pn: merchantName,
    am: Number(amount).toFixed(2),
    cu: "INR",
    tn: `HomeFix booking ${bookingId}`,
  });
  return `upi://pay?${params.toString()}`;
}

export default function UpiPaymentQr({ upiId, amount, bookingId, merchantName }) {
  if (!upiId || amount == null || !bookingId) return null;
  const paymentUri = createUpiPaymentUri({ upiId, amount, bookingId, merchantName });

  return (
    <div className="upi-payment-qr">
      <QRCodeSVG value={paymentUri} size={190} level="M" title={`UPI payment QR for booking ${bookingId}`} />
      <div>
        <strong>Pay ₹{Number(amount).toFixed(2)} by UPI</strong>
        <span>UPI ID: {upiId}</span>
        <a href={paymentUri}>Open UPI app</a>
      </div>
    </div>
  );
}