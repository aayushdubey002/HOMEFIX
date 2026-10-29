export const serviceImageOptions = [
  { key: "home-services", label: "Home services", src: "/images/services/home-interior.jpg", icon: "bi bi-house-gear" },
  { key: "plumbing", label: "Plumbing", src: "/images/services/plumbing.jpg", icon: "bi bi-wrench-adjustable" },
  { key: "electrical", label: "Electrical", src: "/images/services/electrical.jpg", icon: "bi bi-lightning-charge-fill" },
  { key: "ac-repair", label: "AC repair", src: "/images/services/ac-repair.svg", icon: "bi bi-snow" },
  { key: "carpentry", label: "Carpentry", src: "/images/services/carpentry.jpg", icon: "bi bi-hammer" },
  { key: "cleaning", label: "Cleaning", src: "/images/services/cleaning.jpg", icon: "bi bi-stars" },
  { key: "painting", label: "Painting", src: "/images/services/painting.jpg", icon: "bi bi-brush" },
  { key: "appliance-repair", label: "Appliance repair", src: "/images/services/appliance-repair.jpg", icon: "bi bi-tools" },
];

const servicePresentation = {
  electrician: {
    icon: "bi bi-lightning-charge-fill",
    shortDescription: "Wiring, lights & electrical repairs",
  },
  plumber: {
    icon: "bi bi-wrench-adjustable",
    shortDescription: "Pipes, taps & leaks",
  },
  "ac-repair": {
    icon: "bi bi-snow",
    shortDescription: "Cooling, repair & maintenance",
  },
  cleaning: {
    icon: "bi bi-stars",
    shortDescription: "A fresh, tidy home",
  },
  carpenter: {
    icon: "bi bi-hammer",
    shortDescription: "Furniture & fixtures",
  },
  "appliance-repair": {
    icon: "bi bi-tools",
    shortDescription: "Appliance troubleshooting & repair",
  },
  painting: {
    icon: "bi bi-brush",
    shortDescription: "Interior and exterior painting",
  },
};

const serviceImageByKey = new Map(serviceImageOptions.map((option) => [option.key, option]));

export const getServiceImage = (imageKey) =>
  serviceImageByKey.get(imageKey) || serviceImageByKey.get("home-services");

export function resolveServiceImageUrl(imageUrl) {
  if (typeof imageUrl !== "string"
      || !/^\/api\/services\/catalog\/images\/[0-9a-f-]{36}\.(png|jpg)$/i.test(imageUrl)) {
    return "";
  }
  const apiBase = import.meta.env.VITE_API_BASE_URL || "https://homefix-api-humf.onrender.com/api";
  const browserOrigin = typeof window === "undefined" ? "http://localhost" : window.location.origin;
  return new URL(imageUrl, new URL(apiBase, browserOrigin).origin).toString();
}

export function announceServiceCatalogUpdated() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event("homefix:service-catalog:updated"));

  if (typeof BroadcastChannel === "function") {
    const channel = new BroadcastChannel("homefix-service-catalog");
    channel.postMessage("updated");
    channel.close();
  } else {
    window.localStorage.setItem("homefix-service-catalog-updated", String(Date.now()));
  }
}

export function subscribeToServiceCatalogUpdates(onUpdate) {
  if (typeof window === "undefined") return () => {};
  const handleUpdate = () => onUpdate();
  window.addEventListener("homefix:service-catalog:updated", handleUpdate);

  let channel;
  let handleStorageUpdate;
  if (typeof BroadcastChannel === "function") {
    channel = new BroadcastChannel("homefix-service-catalog");
    channel.onmessage = handleUpdate;
  } else {
    handleStorageUpdate = (event) => {
      if (event.key === "homefix-service-catalog-updated") handleUpdate();
    };
    window.addEventListener("storage", handleStorageUpdate);
  }

  return () => {
    window.removeEventListener("homefix:service-catalog:updated", handleUpdate);
    if (handleStorageUpdate) window.removeEventListener("storage", handleStorageUpdate);
    channel?.close();
  };
}

export function inferServiceImageKey(item = {}) {
  const identity = `${item.slug || ""} ${item.name || ""} ${item.service || ""}`.toLowerCase();
  if (/plumb/.test(identity)) return "plumbing";
  if (/electric/.test(identity)) return "electrical";
  if (/air.?condition|(^|\W)ac(\W|$)|hvac/.test(identity)) return "ac-repair";
  if (/carpent|woodwork/.test(identity)) return "carpentry";
  if (/clean/.test(identity)) return "cleaning";
  if (/paint/.test(identity)) return "painting";
  if (/appliance/.test(identity)) return "appliance-repair";
  return "home-services";
}

export function normalizeServiceCatalog(items) {
  if (!Array.isArray(items)) {
    throw new Error("The service catalog response was not a list.");
  }

  return items.map((item) => {
    if (
      typeof item?.slug !== "string"
      || !item.slug
      || typeof item.name !== "string"
      || !item.name
      || typeof item.service !== "string"
      || !item.service
      || !Number.isFinite(Number(item.startingPrice))
    ) {
      throw new Error("A service catalog item is missing its name, service, or price.");
    }

    const imageKey = serviceImageByKey.has(item.imageKey)
      ? item.imageKey
      : inferServiceImageKey(item);
    const imageOption = getServiceImage(imageKey);
    const presentation = servicePresentation[item.slug] || {};
    const categoryCode = String(item.service).trim().toUpperCase().replace(/[ -]+/g, "_");
    return {
      ...item,
      requestService: item.service,
      categoryName: item.name || categoryCode.replace(/_/g, " "),
      startingPrice: Number(item.startingPrice),
      description: item.description || "",
      shortDescription: presentation.shortDescription || item.description || `${item.name} for your home`,
      icon: presentation.icon || imageOption.icon,
      imageKey,
      image: resolveServiceImageUrl(item.imageUrl) || imageOption.src,
    };
  });
}

export const findService = (slug, services) =>
  services.find((service) => service.slug === slug);
