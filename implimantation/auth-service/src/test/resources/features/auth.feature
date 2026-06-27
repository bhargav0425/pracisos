Feature: User Management and Invites

  Scenario: Clinic owner invites a new practitioner
    Given a clinic owner is logged in
    When the clinic owner invites a user with email "alice@example.com", first name "Alice", last name "Smith", and role "PRACTITIONER"
    Then the invitation should be successful
    And the new user "Alice Smith" should be present in the clinic's user list
