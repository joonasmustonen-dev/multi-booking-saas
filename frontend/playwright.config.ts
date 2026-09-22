import { defineConfig, devices } from "@playwright/test";

const frontendPort = process.env.E2E_FRONTEND_PORT ?? "5173";
const frontendUrl = `http://localhost:${frontendPort}`;

export default defineConfig({
    testDir: "./e2e",
    timeout: 60_000,
    fullyParallel: false,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 1 : 0,
    workers: 1,
    reporter: process.env.CI
        ? [["line"], ["html", { open: "never" }]]
        : [["list"], ["html", { open: "never" }]],
    use: {
        baseURL: frontendUrl,
        trace: "retain-on-failure",
        screenshot: "only-on-failure",
        video: "retain-on-failure"
    },
    expect: {
        timeout: 10_000
    },
    projects: [
        {
            name: "chromium",
            use: { ...devices["Desktop Chrome"] }
        }
    ],
    webServer: {
        command: `npm run dev -- --host 0.0.0.0 --port ${frontendPort} --strictPort`,
        url: frontendUrl,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000
    }
});
