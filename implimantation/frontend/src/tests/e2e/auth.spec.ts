import { test, expect } from '@playwright/test';

test.describe('Authentication & Tenant Management E2E', () => {

  test('should show error message on invalid login credentials', async ({ page }) => {
    // 1. Navigate to the login page
    await page.goto('/login');

    // 2. Fill in incorrect credentials
    await page.locator('input#email').fill('invalid-user@example.com');
    await page.locator('input#password').fill('wrong-password');

    // 3. Click the Sign In button
    await page.click('button[type="submit"]');

    // 4. Verify that the error message is displayed
    const errorMessage = page.locator('div.bg-red-50');
    await expect(errorMessage).toBeVisible();
    await expect(errorMessage).toContainText('Invalid credentials');
  });

  test('should log in successfully as SYSTEM_ADMIN and create a new tenant', async ({ page }) => {
    // 1. Navigate to the login page
    await page.goto('/login');

    // 2. Fill in the seeded system admin credentials
    await page.locator('input#email').fill('admin@pracisos.com');
    await page.locator('input#password').fill('admin123');

    // 3. Click the Sign In button
    await page.click('button[type="submit"]');

    // 4. Verify redirection to the Admin Dashboard
    await expect(page).toHaveURL(/\/admin\/dashboard$/);

    // 5. Verify the dashboard header
    const header = page.locator('h1');
    await expect(header).toContainText('Platform Admin');

    // 6. Generate a random tenant name and slug to ensure test idempotency
    const uniqueId = Math.floor(Math.random() * 100000);
    const clinicName = `Maple Dental ${uniqueId}`;
    const clinicSlug = `maple-dental-${uniqueId}`;

    // 7. Fill in the Tenant creation form
    await page.locator('input#name').fill(clinicName);
    // The slug input is auto-populated via HMR event, but we can verify/fill it explicitly
    await page.locator('input#slug').fill(clinicSlug);

    // 8. Submit the Tenant creation form
    await page.click('form button[type="submit"]');

    // 9. Verify that the success alert is displayed with correct tenant details
    const successAlert = page.locator('div.bg-emerald-50');
    await expect(successAlert).toBeVisible();
    await expect(successAlert).toContainText(`registered successfully with slug: ${clinicSlug}`);
  });
});
