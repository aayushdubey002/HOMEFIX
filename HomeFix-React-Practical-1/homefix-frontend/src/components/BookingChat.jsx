import React, { useCallback, useEffect, useRef, useState } from "react";
import { fetchBookingMessages, sendBookingMessage } from "../services/apiService";

function formatChatError(error, action) {
  const status = error.response?.status;
  const responseMessage = error.response?.data?.message;
  if (status === 401) return "Your session has expired. Sign in again to continue this conversation.";
  if (status === 403) return "Your account is not permitted to access this booking conversation.";
  if (status === 404) {
    return "This chat is unavailable. It may be closed because the job is complete, or the backend needs updating.";
  }
  if (status) return `${action} failed (HTTP ${status})${responseMessage ? `: ${responseMessage}` : "."}`;
  if (error.request) return "HomeFix could not reach the server. Check that the backend is running, then retry.";
  return error.message || `${action} failed. Please retry.`;
}

export default function BookingChat({ bookingId, currentUserId, otherPartyName }) {
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [loadingError, setLoadingError] = useState("");
  const [sendError, setSendError] = useState("");
  const [sendConfirmation, setSendConfirmation] = useState("");
  const inFlight = useRef(false);
  const threadEndRef = useRef(null);

  const refreshMessages = useCallback(async (showLoading = false) => {
    if (inFlight.current) return;
    inFlight.current = true;
    if (showLoading) setLoading(true);
    try {
      const response = await fetchBookingMessages(bookingId);
      const receivedMessages = Array.isArray(response.data) ? response.data : [];
      setMessages((current) => {
        const byId = new Map(current.map((message) => [message.id, message]));
        receivedMessages.forEach((message) => {
          if (message?.id != null) byId.set(message.id, message);
        });
        return [...byId.values()].sort((first, second) => (
          new Date(first.sentAt).getTime() - new Date(second.sentAt).getTime()
        ));
      });
      setLoadingError("");
    } catch (requestError) {
      setLoadingError(formatChatError(requestError, "Loading messages"));
    } finally {
      inFlight.current = false;
      setLoading(false);
    }
  }, [bookingId]);

  useEffect(() => {
    refreshMessages(true);
    const intervalId = window.setInterval(() => refreshMessages(), 3000);
    return () => window.clearInterval(intervalId);
  }, [refreshMessages]);

  useEffect(() => {
    threadEndRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [messages]);

  const submitMessage = async (event) => {
    event.preventDefault();
    const message = draft.trim();
    if (!message || sending) return;
    setSending(true);
    setSendError("");
    setSendConfirmation("");
    try {
      const response = await sendBookingMessage(bookingId, message);
      if (response.data?.id == null || response.data?.message !== message) {
        throw new Error("The server did not confirm that your message was saved. Please retry.");
      }
      setMessages((current) => (
        current.some((item) => item.id === response.data.id)
          ? current
          : [...current, response.data]
      ));
      setDraft("");
      setSendConfirmation("Message sent.");
    } catch (requestError) {
      setSendError(formatChatError(requestError, "Sending message"));
    } finally {
      setSending(false);
    }
  };

  return (
    <section className="booking-chat-page" aria-label="Booking chat">
      <header className="booking-chat-page-header">
        <div className="booking-chat-avatar" aria-hidden="true">
          <i className="bi bi-person-fill"></i>
        </div>
        <div>
          <span className="eyebrow">BOOKING MESSAGES</span>
          <h2>{otherPartyName || "Your HomeFix contact"}</h2>
          <span className="booking-chat-online-note">
            <span className="booking-chat-live-dot"></span>
            Conversation updates automatically
          </span>
        </div>
      </header>
      {(loadingError || sendError) && (
        <div className="booking-chat-error" role="alert">
          <span>{sendError || loadingError}</span>
          {loadingError && (
            <button type="button" onClick={() => refreshMessages(true)} disabled={loading}>
              Retry loading messages
            </button>
          )}
        </div>
      )}
      <div className="booking-chat-thread" aria-live="polite" aria-busy={loading}>
        {loading && messages.length === 0 ? (
          <p className="booking-chat-empty">Loading conversation…</p>
        ) : messages.length === 0 ? (
          <div className="booking-chat-empty">
            <i className="bi bi-chat-heart" aria-hidden="true"></i>
            <strong>Start the conversation</strong>
            <span>Send a message about your booking, timing, or service details.</span>
          </div>
        ) : messages.map((message) => {
          const ownMessage = Number(message.senderId) === Number(currentUserId);
          return (
            <article
              className={`booking-chat-message${ownMessage ? " is-own" : ""}`}
              key={message.id}
            >
              <strong>{ownMessage ? "You" : message.senderName}</strong>
              <p>{message.message}</p>
              <time dateTime={message.sentAt}>
                {new Date(message.sentAt).toLocaleString()}
              </time>
            </article>
          );
        })}
        <div ref={threadEndRef} />
      </div>
      <form className="booking-chat-form" onSubmit={submitMessage}>
        <label>
          Message {otherPartyName || "participant"}
          <textarea
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value);
              setSendError("");
              setSendConfirmation("");
            }}
            maxLength={2000}
            rows={3}
            required
            placeholder="Write a message…"
          />
        </label>
        <button type="submit" className="accept-button" disabled={sending || !draft.trim()}>
          <i className="bi bi-send-fill" aria-hidden="true"></i>
          {sending ? "Sending…" : "Send message"}
        </button>
      </form>
      <div className="booking-chat-send-status" role="status" aria-live="polite">
        {sending ? "Sending your message…" : sendConfirmation}
      </div>
    </section>
  );
}
