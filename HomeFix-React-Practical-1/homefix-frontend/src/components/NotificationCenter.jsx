import React, { useEffect, useRef, useState } from "react";
import { fetchNotifications, markNotificationRead } from "../services/apiService";

export default function NotificationCenter() {
  const [notifications, setNotifications] = useState([]);
  const [message, setMessage] = useState("");
  const detailsRef = useRef(null);

  const load = async () => {
    try {
      const response = await fetchNotifications();
      setNotifications(response.data);
      setMessage("");
    } catch (error) {
      setMessage(error.response?.data?.message || "Notifications are currently unavailable.");
    }
  };

  useEffect(() => {
    load();
    const interval = window.setInterval(load, 30000);
    return () => window.clearInterval(interval);
  }, []);

  useEffect(() => {
    const openNotifications = () => {
      if (detailsRef.current) detailsRef.current.open = true;
    };
    window.addEventListener("homefix:notifications:open", openNotifications);
    return () => window.removeEventListener("homefix:notifications:open", openNotifications);
  }, []);

  const markRead = async (notificationId) => {
    try {
      const response = await markNotificationRead(notificationId);
      setNotifications((current) => current.map((item) =>
        item.id === notificationId ? response.data : item
      ));
      setMessage("");
    } catch (error) {
      setMessage(error.response?.data?.message || "Unable to mark this notification as read.");
    }
  };

  const unread = notifications.filter((notification) => !notification.read).length;

  useEffect(() => {
    window.dispatchEvent(new CustomEvent("homefix:notifications:count", {
      detail: { unread },
    }));
  }, [unread]);

  return (
    <details className="notification-center" ref={detailsRef}>
      <summary aria-label={`${unread} unread notifications`}>
        <i className="bi bi-bell" aria-hidden="true"></i>
        <span>Notifications{unread ? ` (${unread})` : ""}</span>
      </summary>
      <div className="notification-list">
        {message && <p role="status">{message}</p>}
        {notifications.length === 0 && !message && <p>No notifications yet.</p>}
        {notifications.map((notification) => (
          <article key={notification.id} className={notification.read ? "" : "unread"}>
            <strong>{notification.title}</strong>
            <p>{notification.message}</p>
            {!notification.read && (
              <button type="button" onClick={() => markRead(notification.id)}>Mark as read</button>
            )}
          </article>
        ))}
      </div>
    </details>
  );
}
