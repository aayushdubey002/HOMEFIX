import React, { useEffect, useState } from "react";

import ServiceStats from "./components/ServiceStats";
import BookingTable from "./components/BookingTable";
import ServiceRequestForm from "./components/ServiceRequestForm";
import AddServiceModal from "./components/AddServiceModal";
import LoginRegister from "./LoginRegister";

import {
  fetchStats,
  fetchBookings,
  postServiceRequest,
  postNewService
} from "./services/apiService";

function App() {

  const [user, setUser] = useState(null);

  const [stats, setStats] = useState({
    totalCustomers: 0,
    activeProviders: 0,
    totalBookings: 0,
    pendingRequests: 0
  });

  const [bookings, setBookings] = useState([]);

  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);

  const [msg, setMsg] = useState("");

  useEffect(() => {

    const savedUser =
      localStorage.getItem("homefixUser");

    if (savedUser) {

      try {

        const parsedUser =
          JSON.parse(savedUser);

        setUser(parsedUser);

      } catch (error) {

        localStorage.removeItem("homefixUser");

      }
    }

  }, []);

  const loadData = async () => {

    if (!user?.id) {
      return;
    }

    setLoading(true);

    try {

      const [
        resStats,
        resBookings
      ] = await Promise.all([
        fetchStats(user.id),
        fetchBookings(user.id)
      ]);

      setStats(
        resStats.data
      );

      setBookings(
        resBookings.data || []
      );

    } catch (error) {

      console.error(
        "Failed to load dashboard:",
        error
      );

      setStats({
        totalCustomers: 0,
        activeProviders: 0,
        totalBookings: 0,
        pendingRequests: 0
      });

      setBookings([]);

      setMsg(
        "Could not load dashboard data."
      );

    } finally {

      setLoading(false);
    }
  };

  useEffect(() => {

    if (user?.id) {
      loadData();
    }

  }, [user]);

  const handleLoginSuccess = (loggedInUser) => {

    setUser(loggedInUser);

    setMsg(
      `Welcome ${loggedInUser.fullName || loggedInUser.email}!`
    );
  };

  const handleLogout = () => {

    localStorage.removeItem(
      "homefixUser"
    );

    setUser(null);

    setBookings([]);

    setStats({
      totalCustomers: 0,
      activeProviders: 0,
      totalBookings: 0,
      pendingRequests: 0
    });

    setMsg("");
  };

  const handleServiceRequest = async (
    requestData
  ) => {

    if (!user?.id) {

      setMsg(
        "Please login first."
      );

      return;
    }

    try {

      await postServiceRequest(
        requestData,
        user.id
      );

      setMsg(
        `Service request created for ${requestData.customerName}`
      );

      await loadData();

    } catch (error) {

      console.error(
        "Service request failed:",
        error
      );

      setMsg(
        "Could not create service request."
      );
    }
  };

  const handleSaveService = async (
    newService
  ) => {

    try {

      await postNewService(
        newService
      );

      setMsg(
        "Service provider added successfully!"
      );

      setModalOpen(false);

      await loadData();

    } catch (error) {

      console.error(
        "Provider creation failed:",
        error
      );

      setMsg(
        "Could not add service provider."
      );
    }
  };

  return (
    <div className="d-flex flex-column min-vh-100">

      <nav className="navbar navbar-expand-lg navbar-dark bg-dark px-3 shadow-sm">

        <div className="container">

          <a
            className="navbar-brand fw-bold"
            href="#"
          >
            <i className="bi bi-house-check-fill text-warning me-2 brand-icon"></i>

            HomeFix
          </a>

          {user && (
            <div className="d-flex align-items-center gap-2">

              <span className="text-white small">
                Welcome,{" "}
                <strong>
                  {user.fullName || user.email}
                </strong>
              </span>

              <button
                className="btn btn-outline-light btn-sm"
                onClick={handleLogout}
              >
                Logout
              </button>

              <button
                className="btn btn-warning btn-sm"
                onClick={() => setModalOpen(true)}
              >
                <i className="bi bi-person-plus me-1"></i>
                Add Provider
              </button>

            </div>
          )}

        </div>

      </nav>

      <main className="container my-4 flex-grow-1">

        {!user ? (

          <>
            <div className="mb-4">

              <h3 className="hero-title mb-1">
                HomeFix Service Dashboard
              </h3>

              <p className="small-muted mb-0">
                Please login or register to access your dashboard.
              </p>

            </div>

            <LoginRegister
              onLoginSuccess={
                handleLoginSuccess
              }
            />
          </>

        ) : (

          <>

            <div className="mb-4">

              <h3 className="hero-title mb-1">
                HomeFix Service Dashboard
              </h3>

              <p className="small-muted mb-0">
                Book trusted home services and manage your service requests.
              </p>

            </div>

            {msg && (

              <div
                className="alert alert-info alert-dismissible fade show small py-2"
                role="alert"
              >

                <i className="bi bi-info-circle-fill me-2"></i>

                {msg}

                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setMsg("")}
                ></button>

              </div>

            )}

            <ServiceStats
              stats={stats}
            />

            <div className="row g-3">

              <div className="col-lg-8">

                <BookingTable
                  list={bookings}
                  isLoading={loading}
                  refreshData={loadData}
                />

              </div>

              <div className="col-lg-4">

                <ServiceRequestForm
                  handleRequest={
                    handleServiceRequest
                  }
                />

              </div>

            </div>

          </>

        )}

      </main>

      {user && (

        <AddServiceModal
          show={modalOpen}
          onHide={() => setModalOpen(false)}
          saveService={handleSaveService}
        />

      )}

      <footer className="py-3 bg-dark text-center text-white-50 mt-auto small">

        <div className="container">
          HomeFix — React.js Dynamic Front-End Practical
        </div>

      </footer>

    </div>
  );
}

export default App;