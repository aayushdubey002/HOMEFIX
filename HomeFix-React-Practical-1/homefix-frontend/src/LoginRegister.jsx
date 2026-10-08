import React, { useState } from "react";
import { registerUser, loginUser } from "./services/apiService";

export default function LoginRegister({ onLoginSuccess }) {

  const [activeTab, setActiveTab] = useState("login");

  const [form, setForm] = useState({
    fullName: "",
    email: "",
    password: ""
  });

  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {

    const { name, value } = e.target;

    setForm((prev) => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e) => {

    e.preventDefault();

    setLoading(true);
    setStatus("");

    try {

      if (activeTab === "register") {

        const response = await registerUser({
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          password: form.password
        });

        setStatus(
          response?.data?.message ||
          "Registration successful"
        );

        setForm({
          fullName: "",
          email: "",
          password: ""
        });

        setActiveTab("login");

      } else {

        const response = await loginUser({
          email: form.email.trim(),
          password: form.password
        });

        const user = response?.data?.user;

        if (!user?.id) {

          setStatus(
            "Login successful, but user ID was not returned."
          );

          return;
        }

        const loggedInUser = {
          id: user.id,
          email: user.email,
          fullName: user.fullName || ""
        };

        localStorage.setItem(
          "homefixUser",
          JSON.stringify(loggedInUser)
        );

        setStatus("Login successful!");

        if (onLoginSuccess) {
          onLoginSuccess(loggedInUser);
        }

        setForm({
          fullName: "",
          email: "",
          password: ""
        });
      }

    } catch (error) {

      setStatus(
        error?.response?.data?.message ||
        "Request failed"
      );

    } finally {

      setLoading(false);
    }
  };

  return (
    <div className="auth-container">

      <div className="auth-tabs">

        <button
          type="button"
          className={
            activeTab === "login"
              ? "active"
              : ""
          }
          onClick={() => {
            setActiveTab("login");
            setStatus("");
          }}
        >
          Login
        </button>

        <button
          type="button"
          className={
            activeTab === "register"
              ? "active"
              : ""
          }
          onClick={() => {
            setActiveTab("register");
            setStatus("");
          }}
        >
          Register
        </button>

      </div>

      <form
        className="auth-form"
        onSubmit={handleSubmit}
      >

        {activeTab === "register" && (

          <input
            type="text"
            name="fullName"
            placeholder="Full Name"
            value={form.fullName}
            onChange={handleChange}
            required
          />

        )}

        <input
          type="email"
          name="email"
          placeholder="Email"
          value={form.email}
          onChange={handleChange}
          required
        />

        <input
          type="password"
          name="password"
          placeholder="Password"
          value={form.password}
          onChange={handleChange}
          required
          minLength={8}
        />

        <button
          type="submit"
          disabled={loading}
        >
          {loading
            ? "Please wait..."
            : activeTab === "login"
              ? "Login"
              : "Register"}
        </button>

        {status && (
          <div
            className="auth-status"
            role="status"
          >
            {status}
          </div>
        )}

      </form>

    </div>
  );
}