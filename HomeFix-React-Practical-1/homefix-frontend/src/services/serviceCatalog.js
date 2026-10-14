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
};

export function normalizeServiceCatalog(items) {
  if (!Array.isArray(items)) {
    throw new Error("The service catalog response was not a list.");
  }

  return items.map((item) => {
    if (
      !item?.slug
      || !item?.name
      || !item?.service
      || !Number.isFinite(Number(item.startingPrice))
    ) {
      throw new Error("A service catalog item is missing its name, service, or price.");
    }

    const presentation = servicePresentation[item.slug] || {};
    return {
      ...item,
      requestService: item.service,
      startingPrice: Number(item.startingPrice),
      description: item.description || "",
      shortDescription: presentation.shortDescription || item.description || "",
      icon: presentation.icon || "bi bi-house-gear",
    };
  });
}

export const findService = (slug, services) =>
  services.find((service) => service.slug === slug);
