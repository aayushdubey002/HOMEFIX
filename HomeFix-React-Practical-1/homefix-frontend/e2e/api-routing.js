const apiTarget = new URL(
  process.env.PLAYWRIGHT_API_BASE_URL || "https://homefix-api-humf.onrender.com/api"
);

export async function routeUpdatedApi(page) {
  await page.route("https://homefix-api-humf.onrender.com/api**", (route) => {
    const url = new URL(route.request().url());
    url.host = apiTarget.host;
    return route.continue({ url: url.toString() });
  });
}
