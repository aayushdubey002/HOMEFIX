import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  createAdminCategory,
  decideProviderVerification,
  fetchAdminProviderDocumentFile,
  fetchAdminProviderDocuments,
  fetchAdminBookings,
  fetchAdminCategories,
  fetchAdminDashboard,
  fetchAdminPayments,
  fetchAdminPricingConfiguration,
  fetchAdminProviders,
  fetchAdminReviews,
  fetchAdminTickets,
  fetchAdminUsers,
  fetchPaymentConfiguration,
  updateAdminCategory,
  updateAdminReviewVisibility,
  requestAdminRefund,
  updateAdminTicket,
  updateAdminUserStatus,
  reviewAdminProviderDocument,
  updateAdminPricingConfiguration,
} from "../services/apiService";
import {
  announceServiceCatalogUpdated,
  getServiceImage,
  inferServiceImageKey,
  resolveServiceImageUrl,
  serviceImageOptions,
} from "../services/serviceCatalog";
import "./AdminDashboard.css";

const sections = ["Overview", "Providers", "Users", "Bookings", "Services", "Pricing", "Support", "Reviews", "Payments"];
const sectionIcons = {
  Overview: "bi-grid-1x2",
  Providers: "bi-person-badge",
  Users: "bi-people",
  Bookings: "bi-calendar2-check",
  Services: "bi-tools",
  Pricing: "bi-sliders",
  Support: "bi-chat-left-text",
  Reviews: "bi-star",
  Payments: "bi-credit-card",
};
const sectionDescriptions = {
  Overview: "A clear snapshot of customers, service providers, and platform activity.",
  Providers: "Review provider applications and verify submitted documents.",
  Users: "Manage customer and provider accounts across HomeFix.",
  Bookings: "Review booking details and track current service status.",
  Services: "Manage service categories, images, and starting prices.",
  Pricing: "Configure the pricing rules used for new service estimates.",
  Support: "Review customer issues and resolve support requests.",
  Reviews: "Moderate customer feedback and manage review visibility.",
  Payments: "Review payment records, gateway attempts, and refunds.",
};

function errorMessage(error) {
  return error.response?.data?.message
    || error.message
    || "The requested operation could not be completed.";
}

export default function AdminDashboard() {
  const [section, setSection] = useState("Overview");
  const [data, setData] = useState({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [documentReviewNotes, setDocumentReviewNotes] = useState({});
  const [documentPreview, setDocumentPreview] = useState(null);
  const [userRoleFilter, setUserRoleFilter] = useState("CUSTOMER");
  const [pricingForm, setPricingForm] = useState({
    lessThanOneYearMultiplier: "1.00",
    oneToThreeYearsMultiplier: "1.00",
    threeToFiveYearsMultiplier: "1.00",
    fivePlusYearsMultiplier: "1.00",
    weekendSurchargePercent: "0.00",
    urgentSurchargePercent: "0.00",
    urgentWindowHours: "0",
    lateNightSurchargePercent: "0.00",
    lateNightStart: "00:00",
    lateNightEnd: "00:00",
    maximumProviderAdditionalCharge: "0.00",
    discountType: "NONE",
    discountValue: "0.00",
    discountMinimumSubtotal: "0.00",
    eligibleServiceCodes: "",
    taxPercent: "0.00",
  });
  const [paymentFilters, setPaymentFilters] = useState({
    query: "", status: "ALL", from: "", to: "",
  });
  const [refundDrafts, setRefundDrafts] = useState({});
  const [categoryImageFile, setCategoryImageFile] = useState(null);
  const [categoryImagePreview, setCategoryImagePreview] = useState("");
  const [categoryImageFiles, setCategoryImageFiles] = useState({});
  const [categoryImagePreviews, setCategoryImagePreviews] = useState({});
  const [categoryImageSelections, setCategoryImageSelections] = useState({});
  const previewUrls = useRef(new Set());
  const [categoryForm, setCategoryForm] = useState({
    name: "", slug: "", serviceCode: "", description: "", startingPrice: "",
    imageKey: "home-services", active: true,
  });

  const refresh = useCallback(async () => {
    setLoading(true);
    setNotice("");
    const requests = [
      ["dashboard", "Dashboard", fetchAdminDashboard],
      ["users", "Users", fetchAdminUsers],
      ["providers", "Providers", fetchAdminProviders],
      ["providerDocuments", "Provider documents", fetchAdminProviderDocuments],
      ["categories", "Services", fetchAdminCategories],
      ["bookings", "Bookings", fetchAdminBookings],
      ["tickets", "Support tickets", fetchAdminTickets],
      ["reviews", "Reviews", fetchAdminReviews],
      ["payments", "Payments", fetchAdminPayments],
      ["paymentConfiguration", "UPI settings", fetchPaymentConfiguration],
      ["pricingConfiguration", "Pricing settings", fetchAdminPricingConfiguration],
    ];

    try {
      const responses = await Promise.allSettled(
        requests.map(([, , request]) => request())
      );
      const loadedData = {};
      const failures = [];
      responses.forEach((response, index) => {
        const [key, label] = requests[index];
        if (response.status === "fulfilled") {
          loadedData[key] = response.value.data;
        } else {
          failures.push(`${label}: ${errorMessage(response.reason)}`);
        }
      });
      setData((current) => ({ ...current, ...loadedData }));
      if (failures.length > 0) {
        setNotice(`Some admin data could not be loaded. ${failures.join(" | ")}`);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (data.pricingConfiguration) {
      setPricingForm(Object.fromEntries(
        Object.entries(data.pricingConfiguration).map(([key, value]) => [
          key,
          key === "lateNightStart" || key === "lateNightEnd"
            ? String(value).slice(0, 5)
            : String(value),
        ])
      ));
    }
  }, [data.pricingConfiguration]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => () => {
    previewUrls.current.forEach((url) => URL.revokeObjectURL(url));
  }, []);

  useEffect(() => () => {
    if (documentPreview?.url) {
      URL.revokeObjectURL(documentPreview.url);
    }
  }, [documentPreview?.url]);

  const createImagePreview = (file) => {
    const url = URL.createObjectURL(file);
    previewUrls.current.add(url);
    return url;
  };

  const revokeImagePreview = (url) => {
    if (url) {
      URL.revokeObjectURL(url);
      previewUrls.current.delete(url);
    }
  };

  const isSupportedCategoryImage = (file) => {
    if (!file) return true;
    if (!["image/jpeg", "image/png"].includes(file.type)) {
      setNotice("Choose a JPEG or PNG service image.");
      return false;
    }
    if (file.size > 5 * 1024 * 1024) {
      setNotice("Service images must be 5 MB or smaller.");
      return false;
    }
    setNotice("");
    return true;
  };

  const perform = async (action, successMessage, onSuccess) => {
    setBusy(true);
    setNotice("");
    try {
      await action();
      await refresh();
      onSuccess?.();
      setNotice(successMessage);
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const downloadProviderDocument = async (providerDocument) => {
    await perform(async () => {
      const response = await fetchAdminProviderDocumentFile(providerDocument.id);
      const url = URL.createObjectURL(response.data);
      const link = document.createElement("a");
      link.href = url;
      link.download = providerDocument.originalFilename;
      link.click();
      URL.revokeObjectURL(url);
    }, "Provider document downloaded.");
  };

  const viewProviderDocument = async (providerDocument) => {
    setBusy(true);
    setNotice("");
    try {
      const response = await fetchAdminProviderDocumentFile(providerDocument.id);
      setDocumentPreview({
        url: URL.createObjectURL(response.data),
        filename: providerDocument.originalFilename,
        contentType: response.data.type || providerDocument.contentType,
      });
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const closeDocumentPreview = () => setDocumentPreview(null);

  const reviewProviderDocument = (providerDocument, status) => {
    const note = documentReviewNotes[providerDocument.id] || "";
    if (status === "REJECTED" && !note.trim()) {
      setNotice("Add a reason before rejecting a provider document.");
      return;
    }
    perform(
      () => reviewAdminProviderDocument(providerDocument.id, status, note.trim()),
      `Provider document ${status.toLowerCase()}.`,
    );
  };

  const savePricingConfiguration = async (event) => {
    event.preventDefault();
    await perform(
      () => updateAdminPricingConfiguration({
        ...pricingForm,
        lessThanOneYearMultiplier: Number(pricingForm.lessThanOneYearMultiplier),
        oneToThreeYearsMultiplier: Number(pricingForm.oneToThreeYearsMultiplier),
        threeToFiveYearsMultiplier: Number(pricingForm.threeToFiveYearsMultiplier),
        fivePlusYearsMultiplier: Number(pricingForm.fivePlusYearsMultiplier),
        weekendSurchargePercent: Number(pricingForm.weekendSurchargePercent),
        urgentSurchargePercent: Number(pricingForm.urgentSurchargePercent),
        urgentWindowHours: Number(pricingForm.urgentWindowHours),
        lateNightSurchargePercent: Number(pricingForm.lateNightSurchargePercent),
        maximumProviderAdditionalCharge: Number(pricingForm.maximumProviderAdditionalCharge),
        discountValue: Number(pricingForm.discountValue),
        discountMinimumSubtotal: Number(pricingForm.discountMinimumSubtotal),
        taxPercent: Number(pricingForm.taxPercent),
      }),
      "Pricing configuration saved.",
    );
  };

  const addCategory = async (event) => {
    event.preventDefault();
    await perform(async () => {
      await createAdminCategory({
        ...categoryForm,
        startingPrice: Number(categoryForm.startingPrice),
      }, categoryImageFile);
      revokeImagePreview(categoryImagePreview);
      setCategoryImageFile(null);
      setCategoryImagePreview("");
      setCategoryForm({
        name: "", slug: "", serviceCode: "", description: "", startingPrice: "",
        imageKey: "home-services", active: true,
      });
    }, "Service category added.", announceServiceCatalogUpdated);
  };

  const renderOverview = () => (
    <>
      <div className="admin-metric-grid">
        {[
          ["Customers", data.dashboard?.customers, "bi-people", "blue"],
          ["Providers", data.dashboard?.providers, "bi-person-workspace", "violet"],
          ["Approved providers", data.dashboard?.approvedProviders, "bi-patch-check", "green"],
          ["Pending verification", data.dashboard?.pendingVerification, "bi-shield-check", "amber"],
          ["Bookings", data.dashboard?.bookings, "bi-calendar2-check", "blue"],
          ["Pending bookings", data.dashboard?.pendingBookings, "bi-hourglass-split", "amber"],
          ["Open complaints", data.dashboard?.openComplaints, "bi-chat-left-dots", "rose"],
          ["Completed booking value", `₹${data.dashboard?.completedBookingValue ?? 0}`, "bi-currency-rupee", "green"],
        ].map(([label, value, icon, tone]) => (
          <article className="admin-metric" data-tone={tone} key={label}>
            <span className="admin-metric-icon" aria-hidden="true"><i className={`bi ${icon}`} /></span>
            <span className="admin-metric-label">{label}</span>
            <strong>{value ?? "—"}</strong>
          </article>
        ))}
      </div>
      <p className="admin-disclaimer">
        Counts and payment records are read from the application database. Gateway payment
        statuses are updated only after verified Razorpay responses or signed webhook events.
      </p>
    </>
  );

  const latestProviderDocuments = {};
  (data.providerDocuments || []).forEach((providerDocument) => {
    const providerDocuments = latestProviderDocuments[providerDocument.providerId] || {};
    if (!providerDocuments[providerDocument.documentType]) {
      providerDocuments[providerDocument.documentType] = providerDocument;
    }
    latestProviderDocuments[providerDocument.providerId] = providerDocuments;
  });
  const pendingProviders = (data.providers || []).filter(
    (provider) => provider.status === "PENDING"
  );

  const renderProviders = () => (
    <>
      <h3>Pending provider applications</h3>
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead><tr><th>Provider</th><th>Category</th><th>Verification</th><th>Documents</th><th>Decision</th></tr></thead>
          <tbody>{pendingProviders.map((provider) => {
            const latestDocuments = latestProviderDocuments[provider.id] || {};
            const requiredDocumentStatuses = [
              ["Identity", latestDocuments.IDENTITY_PROOF],
              ["Service address", latestDocuments.SERVICE_ADDRESS_PROOF],
            ];
            return (
            <tr key={provider.id}>
              <td>{provider.name}<small>{provider.email}</small></td>
              <td>{provider.serviceCategory || "—"}</td>
              <td>{provider.status}</td>
              <td>
                {requiredDocumentStatuses.map(([label, document]) => (
                  <small key={label}>
                    {label}: {!document
                      ? "NOT UPLOADED"
                      : document.status === "REJECTED"
                        ? "REJECTED · REUPLOAD REQUIRED"
                        : document.status}
                  </small>
                ))}
                {!provider.documentsReady && (
                  <small className="admin-document-requirement">Approve both required documents below before profile approval.</small>
                )}
              </td>
              <td className="admin-actions">
                <button disabled={busy || provider.status === "APPROVED" || !provider.documentsReady} onClick={() => perform(
                  () => decideProviderVerification(provider.id, "APPROVED"),
                  "Provider approved. They can now go online to receive matching requests.",
                )}>Approve profile</button>
                <button className="admin-secondary-button" disabled={busy || provider.status === "REJECTED"} onClick={() => perform(
                  () => decideProviderVerification(provider.id, "REJECTED", "Verification rejected by administrator."),
                  "Provider rejected.",
                )}>Reject profile</button>
              </td>
            </tr>
          );
          })}</tbody>
        </table>
        {pendingProviders.length === 0 && <p>No provider applications are waiting for review.</p>}
      </div>

      <h3 className="admin-provider-documents-title">Verification documents</h3>
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead><tr><th>Provider</th><th>Document</th><th>Uploaded</th><th>Review</th><th>Actions</th></tr></thead>
          <tbody>{(data.providerDocuments || []).map((providerDocument) => (
            <tr key={providerDocument.id}>
              <td>{providerDocument.providerName}<small>{providerDocument.providerEmail}</small></td>
              <td>{providerDocument.documentType.replaceAll("_", " ")}<small>{providerDocument.originalFilename} · {Math.ceil(providerDocument.sizeBytes / 1024)} KB</small></td>
              <td>{new Date(providerDocument.uploadedAt).toLocaleDateString()}</td>
              <td>{providerDocument.status}{providerDocument.reviewNote && <small>{providerDocument.reviewNote}</small>}</td>
              <td>
                <div className="admin-actions">
                  <button type="button" disabled={busy} onClick={() => viewProviderDocument(providerDocument)}>View</button>
                  <button className="admin-secondary-button" type="button" disabled={busy} onClick={() => downloadProviderDocument(providerDocument)}>Download</button>
                  {providerDocument.status === "PENDING" && (
                    <>
                      <button type="button" disabled={busy} onClick={() => reviewProviderDocument(providerDocument, "APPROVED")}>Approve</button>
                      <button className="admin-secondary-button" type="button" disabled={busy} onClick={() => reviewProviderDocument(providerDocument, "REJECTED")}>Reject</button>
                    </>
                  )}
                </div>
                {providerDocument.status === "PENDING" && (
                  <input
                    aria-label={`Rejection reason for ${providerDocument.originalFilename}`}
                    value={documentReviewNotes[providerDocument.id] || ""}
                    onChange={(event) => setDocumentReviewNotes((current) => ({
                      ...current,
                      [providerDocument.id]: event.target.value,
                    }))}
                    placeholder="Reason required to reject"
                    maxLength={1000}
                  />
                )}
              </td>
            </tr>
          ))}</tbody>
        </table>
        {!data.providerDocuments?.length && <p>No verification documents have been uploaded.</p>}
      </div>
    </>
  );

  const renderUsers = () => {
    const customers = (data.users || []).filter(
      (user) => user.role?.toUpperCase() === "CUSTOMER"
    );
    const providers = (data.users || []).filter(
      (user) => user.role?.toUpperCase() === "PROVIDER"
    );
    const providerProfiles = Object.fromEntries(
      (data.providers || []).map((provider) => [provider.id, provider])
    );
    const visibleUsers = userRoleFilter === "CUSTOMER" ? customers : providers;

    return (
      <>
        <div className="admin-user-role-switch" role="group" aria-label="Filter users by role">
          <button
            type="button"
            className={userRoleFilter === "CUSTOMER" ? "active" : ""}
            aria-pressed={userRoleFilter === "CUSTOMER"}
            onClick={() => setUserRoleFilter("CUSTOMER")}
          >
            <i className="bi bi-person" aria-hidden="true" />
            Customers <span>{customers.length}</span>
          </button>
          <button
            type="button"
            className={userRoleFilter === "PROVIDER" ? "active" : ""}
            aria-pressed={userRoleFilter === "PROVIDER"}
            onClick={() => setUserRoleFilter("PROVIDER")}
          >
            <i className="bi bi-person-workspace" aria-hidden="true" />
            Service Providers <span>{providers.length}</span>
          </button>
        </div>
        <div className="admin-table-wrap">
          <table className="admin-table">
            {userRoleFilter === "CUSTOMER" ? (
              <>
                <thead><tr><th>Customer</th><th>Contact</th><th>Account</th><th>Joined</th><th>Action</th></tr></thead>
                <tbody>{visibleUsers.map((user) => (
                  <tr key={user.id}>
                    <td>{user.name}</td>
                    <td>{user.email}<small>{user.phone || "Phone not provided"}</small></td>
                    <td>{user.active ? "Active" : "Suspended"}</td>
                    <td>{user.createdAt ? new Date(user.createdAt).toLocaleDateString() : "—"}</td>
                    <td><button disabled={busy} className="admin-secondary-button" onClick={() => perform(
                      () => updateAdminUserStatus(user.id, user.active ? "SUSPENDED" : "ACTIVE"),
                      "User account status updated.",
                    )}>{user.active ? "Suspend" : "Reactivate"}</button></td>
                  </tr>
                ))}</tbody>
              </>
            ) : (
              <>
                <thead><tr><th>Provider</th><th>Contact</th><th>Service profile</th><th>Verification</th><th>Account</th><th>Action</th></tr></thead>
                <tbody>{visibleUsers.map((user) => {
                  const provider = providerProfiles[user.id];
                  return (
                    <tr key={user.id}>
                      <td>{user.name}<small>{provider?.online ? "Online" : "Offline"}</small></td>
                      <td>{user.email}<small>{user.phone || "Phone not provided"}</small></td>
                      <td>
                        {provider?.serviceCategory || "Service not specified"}
                        <small>{provider?.experience || "Experience not provided"}</small>
                        <small>{provider?.serviceArea || "Service area not provided"}</small>
                      </td>
                      <td>{provider?.status || (user.providerApproved ? "APPROVED" : "PENDING")}
                        {!provider && <small>Provider profile details unavailable</small>}
                      </td>
                      <td>{user.active ? "Active" : "Suspended"}</td>
                      <td><button disabled={busy} className="admin-secondary-button" onClick={() => perform(
                        () => updateAdminUserStatus(user.id, user.active ? "SUSPENDED" : "ACTIVE"),
                        "User account status updated.",
                      )}>{user.active ? "Suspend" : "Reactivate"}</button></td>
                    </tr>
                  );
                })}</tbody>
              </>
            )}
          </table>
          {visibleUsers.length === 0 && (
            <p className="admin-empty-state">No {userRoleFilter === "CUSTOMER" ? "customer" : "service provider"} accounts found.</p>
          )}
        </div>
      </>
    );
  };

  const renderBookings = () => (
    <div className="admin-table-wrap">
      <table className="admin-table">
        <thead><tr><th>Booking</th><th>Customer / Provider</th><th>Service & schedule</th><th>Status</th><th>Price</th></tr></thead>
        <tbody>{(data.bookings || []).map((booking) => (
          <tr key={booking.id}>
            <td>{booking.bookingId}</td>
            <td>{booking.customerName}<small>{booking.providerName || "Unassigned"}</small></td>
            <td>{booking.service}<small>{booking.preferredDate || "Date not set"}</small></td>
            <td>{booking.status}</td><td>₹{booking.price ?? 0}</td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  );

  const renderCategories = () => (
    <>
      <form className="admin-category-form" onSubmit={addCategory}>
        <h3>Add service category</h3>
        <input required maxLength={100} placeholder="Name" value={categoryForm.name} onChange={(event) => setCategoryForm({ ...categoryForm, name: event.target.value })} />
        <input required maxLength={100} placeholder="Slug (e.g. ac-repair)" value={categoryForm.slug} onChange={(event) => setCategoryForm({ ...categoryForm, slug: event.target.value })} />
        <input required maxLength={50} placeholder="Service code" value={categoryForm.serviceCode} onChange={(event) => setCategoryForm({ ...categoryForm, serviceCode: event.target.value })} />
        <input required type="number" min="0" step="0.01" placeholder="Starting price (₹)" value={categoryForm.startingPrice} onChange={(event) => setCategoryForm({ ...categoryForm, startingPrice: event.target.value })} />
        <input maxLength={500} placeholder="Description" value={categoryForm.description} onChange={(event) => setCategoryForm({ ...categoryForm, description: event.target.value })} />
        <label className="admin-category-image-field">Service card image
          <select
            aria-label="Service card image"
            value={categoryForm.imageKey}
            onChange={(event) => {
              setCategoryForm({ ...categoryForm, imageKey: event.target.value });
              revokeImagePreview(categoryImagePreview);
              setCategoryImageFile(null);
              setCategoryImagePreview("");
              const upload = event.currentTarget.form.elements.namedItem("categoryImage");
              if (upload) upload.value = "";
            }}
          >
            {serviceImageOptions.map((option) => (
              <option key={option.key} value={option.key}>{option.label}</option>
            ))}
          </select>
        </label>
        <span className="admin-category-image-preview">
          <img src={categoryImagePreview || getServiceImage(categoryForm.imageKey).src} alt="" />
          {categoryImageFile ? categoryImageFile.name : "HomeFix image"}
        </span>
        <label className="admin-category-upload-field">Or upload your own image
          <input
            name="categoryImage"
            type="file"
            accept="image/jpeg,image/png,.jpg,.jpeg,.png"
            aria-label="Upload service image"
            onChange={(event) => {
              const file = event.target.files?.[0] || null;
              if (!isSupportedCategoryImage(file)) {
                event.target.value = "";
                return;
              }
              revokeImagePreview(categoryImagePreview);
              setCategoryImageFile(file);
              setCategoryImagePreview(file ? createImagePreview(file) : "");
            }}
          />
          <small>JPEG or PNG, up to 5 MB. This image is saved for this service.</small>
        </label>
        <button disabled={busy}>Add category</button>
      </form>
      <div className="admin-category-list">{(data.categories || []).map((category) => (
        <form className="admin-category-editor" key={category.id} onSubmit={(event) => {
          event.preventDefault();
          const values = new FormData(event.currentTarget);
          const imageFile = categoryImageFiles[category.id];
          perform(() => updateAdminCategory(category.id, {
            name: values.get("name"),
            slug: values.get("slug"),
            serviceCode: values.get("serviceCode"),
            description: values.get("description"),
            imageKey: values.get("imageKey"),
            imageUrl: values.get("imageUrl"),
            startingPrice: Number(values.get("startingPrice")),
            active: values.get("active") === "on",
          }, imageFile), "Service category updated.", () => {
            if (categoryImagePreviews[category.id]) {
              revokeImagePreview(categoryImagePreviews[category.id]);
            }
            setCategoryImageFiles((current) => {
              const next = { ...current };
              delete next[category.id];
              return next;
            });
            setCategoryImagePreviews((current) => {
              const next = { ...current };
              delete next[category.id];
              return next;
            });
            announceServiceCatalogUpdated();
          });
        }}>
          <strong>{category.name}</strong>
          <span className="admin-category-image-preview">
            <img
              src={categoryImagePreviews[category.id]
                || (!categoryImageSelections[category.id]
                  && resolveServiceImageUrl(category.imageUrl))
                || getServiceImage(categoryImageSelections[category.id]
                    || category.imageKey
                    || inferServiceImageKey({
                      slug: category.slug,
                      name: category.name,
                      service: category.serviceCode,
                    })).src}
              alt={`${category.name} service image preview`}
              onError={(event) => {
                event.currentTarget.src = getServiceImage(categoryImageSelections[category.id]
                  || category.imageKey
                  || inferServiceImageKey({
                    slug: category.slug,
                    name: category.name,
                    service: category.serviceCode,
                  })).src;
              }}
            />
            {categoryImageFiles[category.id]?.name || (category.imageUrl ? "Uploaded image" : "HomeFix image")}
          </span>
          <input name="name" aria-label="Category name" required defaultValue={category.name} />
          <input name="slug" aria-label="Category slug" required defaultValue={category.slug} />
          <input name="serviceCode" aria-label="Category service code" required defaultValue={category.serviceCode} />
          <input name="description" aria-label="Category description" defaultValue={category.description} />
          <label>Service card image
            <select
              name="imageKey"
              aria-label={`Service card image for ${category.name}`}
              value={categoryImageSelections[category.id]
                || category.imageKey
                || inferServiceImageKey({
                slug: category.slug,
                name: category.name,
                service: category.serviceCode,
              })}
              onChange={(event) => {
                const imageUrlField = event.currentTarget.form.elements.namedItem("imageUrl");
                if (imageUrlField) imageUrlField.value = "";
                const uploadField = event.currentTarget.form.elements.namedItem("categoryImage");
                if (uploadField) uploadField.value = "";
                if (categoryImagePreviews[category.id]) {
                  revokeImagePreview(categoryImagePreviews[category.id]);
                }
                setCategoryImageSelections((current) => ({
                  ...current,
                  [category.id]: event.target.value,
                }));
                setCategoryImageFiles((current) => {
                  const next = { ...current };
                  delete next[category.id];
                  return next;
                });
                setCategoryImagePreviews((current) => {
                  const next = { ...current };
                  delete next[category.id];
                  return next;
                });
              }}
            >
              {serviceImageOptions.map((option) => (
                <option key={option.key} value={option.key}>{option.label}</option>
              ))}
            </select>
          </label>
          <input type="hidden" name="imageUrl" defaultValue={category.imageUrl || ""} />
          {category.imageUrl && (
            <button
              className="admin-secondary-button"
              type="button"
              onClick={(event) => {
                const form = event.currentTarget.form;
                const imageUrlField = form.elements.namedItem("imageUrl");
                if (imageUrlField) imageUrlField.value = "";
                const uploadField = form.elements.namedItem("categoryImage");
                if (uploadField) uploadField.value = "";
                if (categoryImagePreviews[category.id]) {
                  revokeImagePreview(categoryImagePreviews[category.id]);
                }
                setCategoryImageFiles((current) => {
                  const next = { ...current };
                  delete next[category.id];
                  return next;
                });
                setCategoryImagePreviews((current) => {
                  const next = { ...current };
                  delete next[category.id];
                  return next;
                });
                setCategoryImageSelections((current) => ({
                  ...current,
                  [category.id]: category.imageKey
                    || inferServiceImageKey({
                      slug: category.slug,
                      name: category.name,
                      service: category.serviceCode,
                    }),
                }));
              }}
            >
              Use HomeFix image
            </button>
          )}
          <label className="admin-category-upload-field">Replace with your own image
            <input
              name="categoryImage"
              type="file"
              accept="image/jpeg,image/png,.jpg,.jpeg,.png"
              aria-label={`Upload service image for ${category.name}`}
              onChange={(event) => {
                const file = event.target.files?.[0] || null;
                if (!isSupportedCategoryImage(file)) {
                  event.target.value = "";
                  return;
                }
                if (categoryImagePreviews[category.id]) {
                  revokeImagePreview(categoryImagePreviews[category.id]);
                }
                const imageUrlField = event.currentTarget.form.elements.namedItem("imageUrl");
                if (imageUrlField) imageUrlField.value = category.imageUrl || "";
                setCategoryImageFiles((current) => ({ ...current, [category.id]: file }));
                setCategoryImagePreviews((current) => ({
                  ...current,
                  [category.id]: file ? createImagePreview(file) : "",
                }));
              }}
            />
            <small>JPEG or PNG, up to 5 MB.</small>
          </label>
          <input name="startingPrice" aria-label="Starting price" required type="number" min="0" step="0.01" defaultValue={category.startingPrice} />
          <label><input name="active" type="checkbox" defaultChecked={category.active} /> Active</label>
          <button disabled={busy}>Save category</button>
        </form>
      ))}</div>
    </>
  );

  const renderPricing = () => (
    <form className="admin-category-form admin-pricing-form" onSubmit={savePricingConfiguration}>
      <h3>Server-calculated pricing policy</h3>
      <p className="admin-disclaimer">
        New values affect estimates and proposals only. Existing bookings keep their saved price
        until the customer explicitly approves a provider proposal. Rates are neutral by default.
      </p>
      <h4>Experience multipliers</h4>
      {[
        ["lessThanOneYearMultiplier", "Less than 1 year"],
        ["oneToThreeYearsMultiplier", "1-3 years"],
        ["threeToFiveYearsMultiplier", "3-5 years"],
        ["fivePlusYearsMultiplier", "5+ years"],
      ].map(([field, label]) => (
        <label key={field}>{label} multiplier
          <input type="number" min="0.01" max="10" step="0.01" required
            value={pricingForm[field]}
            onChange={(event) => setPricingForm({ ...pricingForm, [field]: event.target.value })} />
        </label>
      ))}
      <h4>Time-based surcharges</h4>
      <label>Weekend surcharge (%)
        <input type="number" min="0" max="1000" step="0.01" required
          value={pricingForm.weekendSurchargePercent}
          onChange={(event) => setPricingForm({ ...pricingForm, weekendSurchargePercent: event.target.value })} />
      </label>
      <label>Urgent surcharge (%)
        <input type="number" min="0" max="1000" step="0.01" required
          value={pricingForm.urgentSurchargePercent}
          onChange={(event) => setPricingForm({ ...pricingForm, urgentSurchargePercent: event.target.value })} />
      </label>
      <label>Urgent window (hours; 0 disables)
        <input type="number" min="0" max="168" step="1" required
          value={pricingForm.urgentWindowHours}
          onChange={(event) => setPricingForm({ ...pricingForm, urgentWindowHours: event.target.value })} />
      </label>
      <label>Late-night surcharge (%)
        <input type="number" min="0" max="1000" step="0.01" required
          value={pricingForm.lateNightSurchargePercent}
          onChange={(event) => setPricingForm({ ...pricingForm, lateNightSurchargePercent: event.target.value })} />
      </label>
      <label>Late-night start
        <input type="time" required value={pricingForm.lateNightStart}
          onChange={(event) => setPricingForm({ ...pricingForm, lateNightStart: event.target.value })} />
      </label>
      <label>Late-night end (same as start disables)
        <input type="time" required value={pricingForm.lateNightEnd}
          onChange={(event) => setPricingForm({ ...pricingForm, lateNightEnd: event.target.value })} />
      </label>
      <h4>Provider additional-charge cap</h4>
      <label>Maximum additional amount per proposal (₹; 0 disables)
        <input type="number" min="0" max="99999999.99" step="0.01" required
          value={pricingForm.maximumProviderAdditionalCharge}
          onChange={(event) => setPricingForm({
            ...pricingForm, maximumProviderAdditionalCharge: event.target.value,
          })} />
      </label>
      <h4>Discount rules</h4>
      <label>Discount type
        <select value={pricingForm.discountType}
          onChange={(event) => setPricingForm({ ...pricingForm, discountType: event.target.value })}>
          <option value="NONE">None</option>
          <option value="FIXED">Fixed amount</option>
          <option value="PERCENTAGE">Percentage</option>
        </select>
      </label>
      <label>Discount value ({pricingForm.discountType === "PERCENTAGE" ? "%" : "₹"})
        <input type="number" min="0"
          max={pricingForm.discountType === "PERCENTAGE" ? "100" : "99999999.99"}
          step="0.01" required value={pricingForm.discountValue}
          onChange={(event) => setPricingForm({ ...pricingForm, discountValue: event.target.value })} />
      </label>
      <label>Minimum subtotal for discount (₹)
        <input type="number" min="0" step="0.01" required
          value={pricingForm.discountMinimumSubtotal}
          onChange={(event) => setPricingForm({
            ...pricingForm, discountMinimumSubtotal: event.target.value,
          })} />
      </label>
      <label>Eligible service codes (comma-separated; blank means all)
        <input maxLength="500" value={pricingForm.eligibleServiceCodes}
          onChange={(event) => setPricingForm({
            ...pricingForm, eligibleServiceCodes: event.target.value,
          })} />
      </label>
      <h4>Tax</h4>
      <label>Tax rate (%; 0 disables)
        <input type="number" min="0" max="100" step="0.01" required
          value={pricingForm.taxPercent}
          onChange={(event) => setPricingForm({ ...pricingForm, taxPercent: event.target.value })} />
      </label>
      <button type="submit" disabled={busy}>Save pricing policy</button>
    </form>
  );

  const renderSupport = () => (
    <div className="admin-table-wrap">
      <table className="admin-table"><thead><tr><th>Ticket</th><th>Customer</th><th>Subject</th><th>Status</th><th>Action</th></tr></thead>
        <tbody>{(data.tickets || []).map((ticket) => (
          <tr key={ticket.id}><td>{ticket.id}</td><td>{ticket.customerName}</td><td>{ticket.subject}<small>{ticket.description}</small></td>
            <td>{ticket.status}</td><td><button disabled={busy || ticket.status === "RESOLVED"} onClick={() => perform(
              () => updateAdminTicket(ticket.id, "RESOLVED", "Reviewed by HomeFix support."),
              "Support ticket resolved.",
            )}>Resolve</button></td></tr>
        ))}</tbody>
      </table>
    </div>
  );

  const renderReviews = () => (
    <div className="admin-table-wrap">
      <table className="admin-table"><thead><tr><th>Booking</th><th>Customer / Provider</th><th>Rating</th><th>Review</th><th>Visibility</th></tr></thead>
        <tbody>{(data.reviews || []).map((review) => (
          <tr key={review.id}><td>{review.bookingId}</td><td>{review.customerName}<small>{review.providerName}</small></td>
            <td>{review.rating}/5</td><td>{review.comment}</td><td><button className="admin-secondary-button" disabled={busy} onClick={() => perform(
              () => updateAdminReviewVisibility(review.id, review.visible ? "HIDDEN" : "VISIBLE"),
              "Review visibility updated.",
            )}>{review.visible ? "Hide" : "Show"}</button></td></tr>
        ))}</tbody>
      </table>
    </div>
  );

  const renderPayments = () => {
    const query = paymentFilters.query.trim().toLowerCase();
    const payments = (data.payments || []).filter((payment) => {
      const matchesQuery = !query
        || payment.bookingId?.toLowerCase().includes(query)
        || payment.customer?.toLowerCase().includes(query);
      const date = String(payment.updatedAt || "").slice(0, 10);
      return matchesQuery
        && (paymentFilters.status === "ALL" || payment.status === paymentFilters.status)
        && (!paymentFilters.from || date >= paymentFilters.from)
        && (!paymentFilters.to || date <= paymentFilters.to);
    });
    const submitRefund = (transaction) => {
      const draft = refundDrafts[transaction.transactionId] || {};
      if (!draft.amount || !draft.reason?.trim()) {
        setNotice("Enter a refund amount and reason before submitting.");
        return;
      }
      if (!window.confirm(
        `Request a Razorpay refund of ₹${draft.amount} for transaction #${transaction.transactionId}?`,
      )) return;
      perform(
        () => requestAdminRefund(
          transaction.transactionId,
          { amount: Number(draft.amount), reason: draft.reason.trim() },
          window.crypto.randomUUID(),
        ),
        "Refund request submitted to Razorpay. The status will update after gateway confirmation.",
      );
    };
    return <>
      <section className="admin-category-form admin-upi-settings" aria-label="Razorpay gateway configuration">
        <h3>Razorpay UPI gateway</h3>
        <strong role="status">
          {data.paymentConfiguration?.gatewayEnabled
            ? "Razorpay test mode configured"
            : "Gateway not configured — Razorpay checkout and refunds disabled"}
        </strong>
        <p className="admin-disclaimer">
          {data.paymentConfiguration?.configurationMessage
            || "Configure all Razorpay test credentials in the backend environment."}
          {" "}Set RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, and RAZORPAY_WEBHOOK_SECRET on the backend.
          {" "}Live Razorpay keys are rejected by this deployment. Secret values are never displayed.
        </p>
      </section>
      <div className="admin-payment-filters" aria-label="Payment filters">
        <label>Search booking or customer
          <input value={paymentFilters.query}
            onChange={(event) => setPaymentFilters({ ...paymentFilters, query: event.target.value })} />
        </label>
        <label>Status
          <select value={paymentFilters.status}
            onChange={(event) => setPaymentFilters({ ...paymentFilters, status: event.target.value })}>
            <option value="ALL">All statuses</option>
            {["UNPAID", "PENDING", "PAID", "FAILED", "PARTIALLY_REFUNDED", "REFUNDED"]
              .map((status) => <option key={status}>{status}</option>)}
          </select>
        </label>
        <label>Updated from <input type="date" value={paymentFilters.from}
          onChange={(event) => setPaymentFilters({ ...paymentFilters, from: event.target.value })} /></label>
        <label>Updated to <input type="date" value={paymentFilters.to}
          onChange={(event) => setPaymentFilters({ ...paymentFilters, to: event.target.value })} /></label>
      </div>
      <div className="admin-table-wrap"><table className="admin-table">
        <thead><tr><th>Booking</th><th>Customer</th><th>Provider</th><th>Service</th><th>Method</th><th>Payable / collected</th><th>Status</th><th>Receipt reference</th><th>Gateway attempts and refunds</th></tr></thead>
        <tbody>{payments.map((payment) => (
          <tr key={payment.id}><td>{payment.bookingId}</td><td>{payment.customer}</td><td>{payment.provider}</td><td>{payment.service}</td>
            <td>{payment.method === "CASH" && payment.cashReceivedAmount
              ? <>Payable ₹{payment.amount}<small>Cash received ₹{payment.cashReceivedAmount}</small></>
              : <>₹{payment.amount}</>}</td><td>{payment.status}<small>{payment.note}</small></td>
            <td>{payment.reference || "—"}</td>
            <td>{payment.transactions?.length
              ? <ul className="admin-payment-attempts">{payment.transactions.map((transaction) => (
                <li key={transaction.transactionId}>
                  <strong>{transaction.status}</strong> · {transaction.gatewayPaymentId || transaction.gatewayOrderId || "No gateway reference"}
                  <small>Attempt #{transaction.transactionId} · {transaction.currency} {transaction.amount}</small>
                  {transaction.failureReason && <small>{transaction.failureReason}</small>}
                  {transaction.refunds?.map((refund) => (
                    <small key={refund.refundId}>
                      Refund #{refund.refundId}: ₹{refund.amount} · {refund.status} · {refund.reason}
                      {refund.gatewayRefundId && ` · ${refund.gatewayRefundId}`}
                    </small>
                  ))}
                  {["CAPTURED", "PARTIALLY_REFUNDED"].includes(transaction.status) && (
                    <div className="admin-refund-form">
                      <label>Refund amount
                        <input type="number" min="0.01" max={transaction.amount} step="0.01"
                          aria-label={`Refund amount for transaction ${transaction.transactionId}`}
                          value={refundDrafts[transaction.transactionId]?.amount || ""}
                          onChange={(event) => setRefundDrafts({
                            ...refundDrafts,
                            [transaction.transactionId]: {
                              ...refundDrafts[transaction.transactionId],
                              amount: event.target.value,
                            },
                          })} />
                      </label>
                      <label>Reason
                        <input maxLength="500"
                          aria-label={`Refund reason for transaction ${transaction.transactionId}`}
                          value={refundDrafts[transaction.transactionId]?.reason || ""}
                          onChange={(event) => setRefundDrafts({
                            ...refundDrafts,
                            [transaction.transactionId]: {
                              ...refundDrafts[transaction.transactionId],
                              reason: event.target.value,
                            },
                          })} />
                      </label>
                      <button type="button" disabled={busy || !data.paymentConfiguration?.gatewayEnabled}
                        onClick={() => submitRefund(transaction)}>
                        Request test refund
                      </button>
                    </div>
                  )}
                </li>
              ))}</ul>
              : "No gateway attempts recorded"}</td>
          </tr>
        ))}</tbody>
      </table></div>
    </>
  };

  const content = {
    Overview: renderOverview,
    Providers: renderProviders,
    Users: renderUsers,
    Bookings: renderBookings,
    Services: renderCategories,
    Pricing: renderPricing,
    Support: renderSupport,
    Reviews: renderReviews,
    Payments: renderPayments,
  }[section];

  return (
    <main className="admin-dashboard">
      <section className="admin-heading">
        <div className="admin-heading-copy">
          <span className="admin-eyebrow"><i className="bi bi-shield-lock" aria-hidden="true" /> HOMEFIX ADMINISTRATION</span>
          <h1>Administrator dashboard</h1>
          <p>Manage your service platform with clarity and confidence.</p>
        </div>
        <div className="admin-heading-actions">
          <button className="admin-refresh-button" type="button" onClick={refresh} disabled={loading || busy}>
            <i className={`bi bi-arrow-clockwise${loading ? " admin-is-spinning" : ""}`} aria-hidden="true" />
            {loading ? "Refreshing" : "Refresh data"}
          </button>
        </div>
      </section>
      <nav className="admin-tabs" aria-label="Administration sections">
        {sections.map((name) => (
          <button
            key={name}
            type="button"
            className={section === name ? "active" : ""}
            aria-current={section === name ? "page" : undefined}
            onClick={() => setSection(name)}
          >
            <i className={`bi ${sectionIcons[name]}`} aria-hidden="true" />
            <span>{name}</span>
          </button>
        ))}
      </nav>
      {notice && <p className="admin-notice" role="status">{notice}</p>}
      <section className="admin-panel" aria-live="polite">
        <div className="admin-panel-heading">
          <div>
            <span className="admin-panel-kicker">WORKSPACE</span>
            <h2>{section}</h2>
            <p>{sectionDescriptions[section]}</p>
          </div>
          {loading && <span className="admin-loading-label" role="status"><i className="bi bi-arrow-repeat" aria-hidden="true" /> Loading records</span>}
        </div>
        {!loading && content?.()}
      </section>
      {documentPreview && (
        <div className="admin-document-preview-backdrop" onMouseDown={(event) => {
          if (event.target === event.currentTarget) closeDocumentPreview();
        }}>
          <section
            className="admin-document-preview"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-document-preview-title"
          >
            <header className="admin-document-preview-header">
              <div>
                <span className="admin-panel-kicker">PROVIDER VERIFICATION</span>
                <h2 id="admin-document-preview-title">{documentPreview.filename}</h2>
              </div>
              <button
                type="button"
                className="admin-document-preview-close"
                aria-label="Close document preview"
                onClick={closeDocumentPreview}
              >
                <i className="bi bi-x-lg" aria-hidden="true" />
              </button>
            </header>
            <div className="admin-document-preview-content">
              {documentPreview.contentType?.startsWith("image/") ? (
                <img src={documentPreview.url} alt={documentPreview.filename} />
              ) : documentPreview.contentType === "application/pdf" ? (
                <iframe
                  src={documentPreview.url}
                  title={`Preview of ${documentPreview.filename}`}
                  sandbox="allow-scripts"
                />
              ) : (
                <p>This file type cannot be previewed in the browser. Use Download to view it.</p>
              )}
            </div>
          </section>
        </div>
      )}
    </main>
  );
}
