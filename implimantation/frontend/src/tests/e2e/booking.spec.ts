import { test, expect } from '@playwright/test';

test.describe('Booking Service E2E', () => {
  const tenantSlug = 'maple-health';
  const practitionerId = 'b8dc1926-d27b-48c7-b8dc-1926d27b0437';
  const patientEmail = 'patient@maple-health.com';
  const practitionerEmail = 'dr.bob@maple-health.com';
  const password = 'admin123';

  test.beforeAll(async ({ request }) => {
    // 1. Log in as the Practitioner via API to get a JWT token
    const loginResponse = await request.post('http://127.0.0.1:8080/api/v1/auth/login', {
      data: {
        email: practitionerEmail,
        password: password,
      }
    });
    expect(loginResponse.ok()).toBeTruthy();
    const loginData = await loginResponse.json();
    const token = loginData.accessToken;

    // 2. Determine today's day of the week (0 = Sunday, 1 = Monday, ..., 6 = Saturday)
    const todayDayOfWeek = new Date().getDay();

    // 3. Register availability template for today to generate slots
    // This ensures there are always slots available when the test runs
    const availabilityResponse = await request.post('http://127.0.0.1:8081/api/v1/booking/availability', {
      headers: {
        'Authorization': `Bearer ${token}`,
      },
      data: {
        practitionerId: practitionerId,
        dayOfWeek: todayDayOfWeek,
        startTime: '09:00:00',
        endTime: '17:00:00',
        slotDurationMinutes: 30,
      }
    });
    
    // It's fine if the template already exists (returns 4xx/5xx due to unique constraint),
    // we just want to make sure slots are populated in the database.
    console.log(`Availability seeding status: ${availabilityResponse.status()}`);
  });

  test('should allow a patient to book an appointment successfully', async ({ page }) => {
    // Forward browser logs and errors to terminal
    page.on('console', msg => console.log(`[BROWSER CONSOLE]: ${msg.text()}`));
    page.on('pageerror', err => console.log(`[BROWSER ERROR]: ${err.message}\n${err.stack}`));

    // 1. Log in as the Patient via the UI
    await page.goto('/login');
    await page.locator('input#email').fill(patientEmail);
    await page.locator('input#password').fill(password);
    await page.click('button[type="submit"]');

    // 2. Verify redirect to the Clinic Dashboard
    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/dashboard$`));

    // 3. Click the "Book Appointment" navigation tab
    await page.click('button:has-text("Book Appointment")');

    // 4. Select the Practitioner "Bob Jones"
    const practitionerCard = page.getByTestId('practitioner-card').filter({ hasText: 'Bob Jones' });
    await expect(practitionerCard).toBeVisible();
    await practitionerCard.click();

    // 5. Select the first available time slot
    const firstAvailableSlot = page.getByTestId('slot-button').first();
    await expect(firstAvailableSlot).toBeVisible();
    const slotTime = await firstAvailableSlot.textContent();
    console.log(`Selecting slot at: ${slotTime}`);
    await firstAvailableSlot.click();

    // 6. Fill in appointment details
    await page.selectOption('select#appointmentType', 'CONSULTATION');
    await page.locator('textarea#notes').fill('E2E test appointment booking');

    // 7. Confirm the booking
    await page.click('button:has-text("Confirm Booking")');

    // 8. Verify the success banner
    const successBanner = page.getByTestId('success-banner');
    await expect(successBanner).toBeVisible();
    await expect(successBanner).toContainText('Appointment booked successfully');

    // 9. Navigate to the "Appointments" tab
    await page.click('button:has-text("Appointments")');

    // 10. Verify that the booking is displayed in the appointments list
    const appointmentItem = page.getByTestId('appointment-card').first();
    await expect(appointmentItem).toBeVisible();
    await expect(appointmentItem).toContainText('CONSULTATION');
    await expect(appointmentItem).toContainText('E2E test appointment booking');
  });
});
