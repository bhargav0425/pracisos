Feature: Authentication and Authorization

  Background:
    Given the auth service is running
    And a tenant "maple-health" exists

  Scenario: SYSTEM_ADMIN creates a new tenant
    Given I am logged in as SYSTEM_ADMIN
    When I submit a tenant creation request with slug "downtown-dental" and name "Downtown Dental"
    Then the response status should be 201
    And the tenant slug should be "downtown-dental"

  Scenario: CLINIC_OWNER invites a practitioner
    Given I am logged in as CLINIC_OWNER for tenant "maple-health"
    When I invite a user with email "dr.bob@maple-health.com" and role "PRACTITIONER"
    Then the response status should be 201
    And the user role should be "PRACTITIONER"

  Scenario: Patient logs in successfully
    Given a patient "john@example.com" exists for tenant "maple-health"
    When I login with email "john@example.com" and password "password123"
    Then the response status should be 200
    And I should receive an access token

  Scenario: Patient cannot access admin endpoints
    Given I am logged in as PATIENT for tenant "maple-health"
    When I attempt to create a tenant with slug "hacker-clinic"
    Then the response status should be 403

  Scenario: Cross-tenant access is blocked
    Given I am logged in as CLINIC_OWNER for tenant "maple-health"
    When I attempt to list users for tenant "downtown-dental"
    Then the response status should be 403
