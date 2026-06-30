import { test, expect } from '@playwright/test';

test.describe('Pracisos Complete Multi-Tenant E2E Flow', () => {
  const clinicSlug = `e2e-health-${Date.now()}`;
  const clinicName = 'E2E Health Clinic';
  const ownerEmail = `owner@${clinicSlug}.com`;
  const practitionerEmail = `dr.bob@${clinicSlug}.com`;
  const patientEmail = `alice@${clinicSlug}.com`;
  const defaultPassword = 'admin123';

  test('should execute the complete lifecycle from tenant creation to booking', async ({ page }) => {
    // ------------------------------------------------------------------------
    // STEP 1: Log in as SYSTEM_ADMIN and create the Tenant + Owner
    // ------------------------------------------------------------------------
    await page.goto('/login');
    await page.locator('input#email').fill('admin@pracisos.com');
    await page.locator('input#password').fill('admin123');
    await page.click('button[type="submit"]');

    // Verify redirect to Admin Dashboard
    await expect(page).toHaveURL('/admin/dashboard');

    // Fill in the Tenant creation form
    await page.locator('input#name').fill(clinicName);
    // Explicitly set the slug to our unique slug
    await page.locator('input#slug').fill(clinicSlug);
    await page.locator('input#ownerFirstName').fill('Jane');
    await page.locator('input#ownerLastName').fill('Doe');
    await page.locator('input#ownerEmail').fill(ownerEmail);
    await page.locator('input#ownerPassword').fill(defaultPassword);

    // Submit and verify success
    await page.click('form button[type="submit"]');
    await expect(page.locator('text=registered successfully')).toBeVisible();

    // Sign out
    await page.click('text=Sign Out');
    await expect(page).toHaveURL('/login');

    // ------------------------------------------------------------------------
    // STEP 2: Log in as the new CLINIC_OWNER and invite Practitioner & Patient
    // ------------------------------------------------------------------------
    await page.locator('input#email').fill(ownerEmail);
    await page.locator('input#password').fill(defaultPassword);
    await page.click('button[type="submit"]');

    // Verify redirect to Clinic Dashboard
    await expect(page).toHaveURL(new RegExp(`/${clinicSlug}/dashboard`));

    // Invite Practitioner via UI
    await page.locator('input#email').fill(practitionerEmail);
    await page.locator('input#firstName').fill('Bob');
    await page.locator('input#lastName').fill('Smith');
    await page.locator('select#role').selectOption('PRACTITIONER');
    await page.click('form button[type="submit"]');
    await expect(page.locator('text=Successfully invited')).toBeVisible();

    // Log in as owner via API to get token for patient invitation
    const ownerLoginRes = await page.request.post('/api/v1/auth/login', {
      data: {
        email: ownerEmail,
        password: defaultPassword
      }
    });
    expect(ownerLoginRes.ok()).toBeTruthy();
    const ownerLoginData = await ownerLoginRes.json();
    const ownerToken = ownerLoginData.accessToken;

    // Invite Patient via API (since the UI only has Practitioner/Receptionist options)
    const invitePatientRes = await page.request.post('/api/v1/auth/users', {
      headers: {
        'Authorization': `Bearer ${ownerToken}`
      },
      data: {
        email: patientEmail,
        firstName: 'Alice',
        lastName: 'Brown',
        role: 'PATIENT'
      }
    });
    if (!invitePatientRes.ok()) {
      console.error('INVITE PATIENT FAILED WITH STATUS:', invitePatientRes.status());
      console.error('INVITE PATIENT FAILED WITH BODY:', await invitePatientRes.text());
    }
    expect(invitePatientRes.ok()).toBeTruthy();

    // Sign out of the owner session in the browser
    await page.click('text=Sign Out');
    await expect(page).toHaveURL('/login');

    // ------------------------------------------------------------------------
    // STEP 3: Generate Slots for the Practitioner via API
    // ------------------------------------------------------------------------
    // We log in as the practitioner via API to get their token and userId
    const loginResponse = await page.request.post('/api/v1/auth/login', {
      data: {
        email: practitionerEmail,
        password: defaultPassword
      }
    });
    expect(loginResponse.ok()).toBeTruthy();
    const loginData = await loginResponse.json();
    const token = loginData.accessToken;
    const practitionerId = loginData.userId;

    // Create an availability template for today's day of the week
    const todayDayOfWeek = new Date().getDay(); // 0 = Sun, 1 = Mon, ..., 6 = Sat
    const availabilityResponse = await page.request.post('/api/v1/booking/availability', {
      headers: {
        'Authorization': `Bearer ${token}`
      },
      data: {
        practitionerId: practitionerId,
        dayOfWeek: todayDayOfWeek,
        startTime: '09:00:00',
        endTime: '17:00:00',
        slotDurationMinutes: 30
      }
    });
    if (!availabilityResponse.ok()) {
      console.error('AVAILABILITY CREATION FAILED WITH STATUS:', availabilityResponse.status());
      console.error('AVAILABILITY CREATION FAILED WITH BODY:', await availabilityResponse.text());
    }
    expect(availabilityResponse.ok()).toBeTruthy();

    // Give Kafka and DB a moment to synchronize and generate the slots
    await page.waitForTimeout(2000);

    // ------------------------------------------------------------------------
    // STEP 4: Log in as the PATIENT and book an appointment
    // ------------------------------------------------------------------------
    await page.locator('input#email').fill(patientEmail);
    await page.locator('input#password').fill(defaultPassword);
    await page.click('button[type="submit"]');

    // Verify redirect to Clinic Dashboard
    await expect(page).toHaveURL(new RegExp(`/${clinicSlug}/dashboard`));

    // Navigate to "Book Appointment" tab
    await page.click('text=Book Appointment');

    // Select the practitioner card
    const practitionerCard = page.locator('[data-testid="practitioner-card"]').filter({ hasText: 'Bob Smith' });
    await expect(practitionerCard).toBeVisible();
    await practitionerCard.click();

    // Select the first available slot
    const slotButton = page.locator('[data-testid="slot-button"]').first();
    await expect(slotButton).toBeVisible();
    await slotButton.click();

    // Fill in booking details and submit
    await page.locator('textarea#notes').fill('Routine checkup');
    await page.click('button:has-text("Confirm Booking")');

    // Verify success banner
    await expect(page.locator('[data-testid="success-banner"]')).toBeVisible();

    // Go to "Appointments" tab and verify the booking
    await page.click('text=Appointments');
    const appointmentCard = page.locator('[data-testid="appointment-card"]').first();
    await expect(appointmentCard).toBeVisible();
    await expect(appointmentCard).toContainText('CONSULTATION');
  });
});
