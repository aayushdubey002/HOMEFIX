const apiTarget = new URL(
  process.env.PLAYWRIGHT_API_BASE_URL || "http://localhost:8083/api"
);

export async function routeUpdatedApi(page) {
  await page.route("http://localhost:8083/api/**", (route) => {
    const url = new URL(route.request().url());
    url.host = apiTarget.host;
    return route.continue({ url: url.toString() });
  });
}
