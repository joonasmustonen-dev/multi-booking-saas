import { expect, type Page, test } from "@playwright/test";

const customerName = "E2E Customer";

function nextMonday(): string {
    const date = new Date();
    const daysSinceMonday = (date.getUTCDay() + 6) % 7;
    date.setUTCDate(date.getUTCDate() + 7 - daysSinceMonday);
    return date.toISOString().slice(0, 10);
}

function addDays(date: string, days: number): string {
    const result = new Date(`${date}T12:00:00Z`);
    result.setUTCDate(result.getUTCDate() + days);
    return result.toISOString().slice(0, 10);
}

async function login(page: Page) {
    await page.goto("/app");
    await expect(page).toHaveURL(/localhost:8081\/realms\/booking/);
    await page.locator("#username").fill("e2e-admin");
    await page.locator("#password").fill("playwright-only-password");
    const workspaceResponse = page.waitForResponse(
        response => response.url().endsWith("/api/account/workspaces"),
        { timeout: 15_000 }
    );
    const dashboardResponse = page.waitForResponse(
        response => response.url().endsWith("/api/v1/dashboard/summary"),
        { timeout: 15_000 }
    );
    await page.locator("#kc-login").click();
    await expect(page).toHaveURL(/^http:\/\/localhost:5173\/app\/?(?:#.*)?$/);
    const workspaceResult = await workspaceResponse;
    expect(workspaceResult.status()).toBe(200);
    expect(await workspaceResult.json()).toEqual([
        expect.objectContaining({
            slug: "tenant-a",
            role: "TENANT_ADMIN"
        })
    ]);
    expect((await dashboardResponse).status()).toBe(200);
    await expect(page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ })).toBeVisible();
}

async function chooseSearchOption(
    page: Page,
    label: string,
    search: string,
    option: RegExp
) {
    const input = page.getByRole("combobox", { name: label });
    await input.click();
    await input.fill(search);
    await page.getByRole("option", { name: option }).click();
}

async function openSeededAppointment(page: Page) {
    await page.goto("/appointments");
    await page.getByRole("button", { name: "Next" }).click();
    await page.getByRole("button", { name: "Agenda" }).click();
    const booking = page
        .getByRole("button", { name: new RegExp(customerName) })
        .first();
    await expect(booking).toBeVisible();
    await booking.click();
    await expect(page.getByRole("dialog", { name: customerName })).toBeVisible();
}

test.describe.serial("authenticated booking lifecycle", () => {
    const bookingDate = nextMonday();
    const rescheduledDate = addDays(bookingDate, 1);

    test("logs in through the real Keycloak authorization-code flow", async ({ page }) => {
        await login(page);
    });

    test("creates an appointment from browser availability", async ({ page }) => {
        await login(page);
        await page.goto("/appointments");
        await page.getByRole("button", { name: "New appointment" }).click();

        await chooseSearchOption(
            page,
            "Booking customer",
            "E2E Customer",
            /E2E Customer/
        );
        await chooseSearchOption(
            page,
            "Booking service",
            "E2E Resource Booking",
            /E2E Resource Booking/
        );
        await chooseSearchOption(
            page,
            "Booking resource",
            "E2E Resource",
            /E2E Resource/
        );
        await page.locator('input[type="date"]').fill(bookingDate);

        const combination = page.getByRole("combobox", {
            name: "Available booking combination"
        });
        await expect(combination).toBeVisible();
        await combination.click();
        await page.getByRole("option").first().click();

        const responsePromise = page.waitForResponse(response =>
            response.url().endsWith("/api/v1/appointments") &&
            response.request().method() === "POST"
        );
        await page.getByRole("button", { name: "Create appointment" }).click();
        const response = await responsePromise;
        expect(response.status()).toBe(201);
        expect((await response.json()).startAt).toContain(bookingDate);

        await page.getByRole("button", { name: "Next" }).click();
        await page.getByRole("button", { name: "Agenda" }).click();
        await expect(
            page
                .getByRole("button", { name: new RegExp(customerName) })
                .first()
        ).toBeVisible();
    });

    test("reschedules the appointment through available combinations", async ({ page }) => {
        await login(page);
        await openSeededAppointment(page);
        await page.getByRole("button", { name: "Reschedule" }).click();
        await expect(page.getByRole("heading", { name: "Reschedule appointment" })).toBeVisible();
        await page.locator('input[type="date"]').fill(rescheduledDate);

        const combination = page.getByRole("combobox", {
            name: "Available booking combination"
        });
        await expect(combination).toBeVisible();
        await combination.click();
        await page.getByRole("option").first().click();

        const responsePromise = page.waitForResponse(response =>
            response.url().includes("/reschedule") &&
            response.request().method() === "POST"
        );
        await page.getByRole("button", { name: "Reschedule", exact: true }).click();
        const response = await responsePromise;
        expect(response.ok()).toBeTruthy();
        expect((await response.json()).startAt).toContain(rescheduledDate);
    });

    test("cancels the appointment and shows its terminal status", async ({ page }) => {
        await login(page);
        await openSeededAppointment(page);
        page.once("dialog", dialog => dialog.accept());

        const responsePromise = page.waitForResponse(response =>
            response.url().endsWith("/cancel") &&
            response.request().method() === "POST"
        );
        await page.getByRole("button", { name: "Cancel booking" }).click();
        const response = await responsePromise;
        expect(response.ok()).toBeTruthy();
        expect((await response.json()).status).toBe("CANCELLED");
        await expect(
            page
                .getByRole("button", {
                    name: new RegExp(`${customerName}.*Cancelled`, "i")
                })
                .first()
        ).toBeVisible();
    });
});
