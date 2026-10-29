import React, { useEffect, useRef, useState } from "react";
import {
  cancelRazorpayCheckout,
  createRazorpayOrder,
  fetchPaymentConfiguration,
  verifyRazorpayPayment,
} from "../services/apiService";

const checkoutScriptUrl = "https://checkout.razorpay.com/v1/checkout.js";

function loadRazorpayCheckout() {
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    let script = document.querySelector(`script[src="${checkoutScriptUrl}"]`);
    if (!script) {
      script = document.createElement("script");
      script.src = checkoutScriptUrl;
      script.async = true;
      script.dataset.homefixRazorpay = "true";
      document.body.appendChild(script);
    }
    script.addEventListener("load", resolve, { once: true });
    script.addEventListener("error", () => reject(new Error("Secure checkout could not be loaded.")), { once: true });
  });
}

function idempotencyKey() {
  return window.crypto?.randomUUID?.()
    || `hf-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

export default function RazorpayCheckoutButton({
  bookingId,
  bookingCode,
  amount,
  onRefresh,
}) {
  const [configuration, setConfiguration] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const currentIdempotencyKey = useRef("");
  const verificationStarted = useRef(false);

  useEffect(() => {
    let active = true;
    fetchPaymentConfiguration()
      .then((response) => {
        if (active) setConfiguration(response.data);
      })
      .catch(() => {
        if (active) setConfiguration({ gatewayEnabled: false, unavailable: true });
      });
    return () => { active = false; };
  }, []);

  const startCheckout = async () => {
    if (busy) return;
    verificationStarted.current = false;
    setBusy(true);
    setMessage("");
    try {
      if (!currentIdempotencyKey.current) {
        currentIdempotencyKey.current = idempotencyKey();
      }
      const orderResponse = await createRazorpayOrder(
        bookingId,
        currentIdempotencyKey.current,
      );
      await loadRazorpayCheckout();
      const checkout = new window.Razorpay({
        key: orderResponse.data.keyId,
        order_id: orderResponse.data.orderId,
        amount: orderResponse.data.amount,
        currency: orderResponse.data.currency,
        name: "HomeFix",
        description: `Booking ${bookingCode}`,
        method: { upi: true },
        handler: async (checkoutResponse) => {
          verificationStarted.current = true;
          setMessage("Verifying payment with Razorpay...");
          try {
            const verification = await verifyRazorpayPayment(
              orderResponse.data.transactionId,
              {
                razorpayOrderId: checkoutResponse.razorpay_order_id,
                razorpayPaymentId: checkoutResponse.razorpay_payment_id,
                razorpaySignature: checkoutResponse.razorpay_signature,
              },
            );
            if (verification.data?.verified === true) {
              currentIdempotencyKey.current = "";
              setMessage("Payment verified by Razorpay.");
            } else {
              setMessage("Payment is awaiting gateway capture confirmation. This page will refresh automatically.");
            }
            await onRefresh?.();
          } catch (verificationError) {
            setMessage(
              verificationError.response?.data?.message
                || "Payment verification is pending. Refresh to check the gateway status.",
            );
          }
        },
        modal: {
          ondismiss: async () => {
            if (verificationStarted.current) return;
            try {
              await cancelRazorpayCheckout(orderResponse.data.transactionId);
              currentIdempotencyKey.current = "";
              setMessage("Checkout cancelled. No payment was recorded.");
            } catch (cancelError) {
              setMessage(
                cancelError.response?.data?.message
                  || "Checkout closed. Payment status may still be pending; refresh before retrying.",
              );
            }
            await onRefresh?.();
          },
        },
        theme: { color: "#0f766e" },
      });
      checkout.on("payment.failed", (event) => {
        verificationStarted.current = true;
        currentIdempotencyKey.current = "";
        setMessage(event.error?.description
          || "Razorpay reported a failed attempt. HomeFix will show the verified status when the gateway confirms it.");
      });
      checkout.open();
    } catch (requestError) {
      setMessage(
        requestError.response?.data?.message
          || requestError.message
          || "Unable to start secure checkout. Please retry.",
      );
    } finally {
      setBusy(false);
    }
  };

  if (configuration && !configuration.gatewayEnabled) {
    return (
      <p className="payment-disclaimer" role="status">
        {configuration.unavailable
          ? "Secure checkout availability could not be verified. No payment has been initiated."
          : (configuration.configurationMessage
            || "Secure UPI checkout is not configured on this server. No payment has been initiated.")}
      </p>
    );
  }

  return (
    <div className="razorpay-checkout">
      <button type="button" className="accept-button" onClick={startCheckout}
        disabled={busy || !configuration?.gatewayEnabled}>
        {busy ? "Preparing secure checkout..." : `Pay ₹${Number(amount).toFixed(2)} by UPI`}
      </button>
      {configuration?.testMode && <small>Razorpay test mode</small>}
      {message && <p role="status">{message}</p>}
    </div>
  );
}
