@api @users
Feature: Users resource
  As a consumer of the JSONPlaceholder API
  I want user profiles to expose their full nested contract

  @tc:API-010 @newfeature @priority:medium
  Scenario: Retrieve a user profile with nested address and company
    When I send a GET request to "/users/1"
    Then the response status code should be 200
    And the response should match the "user" contract
    And the response field "username" should be "Bret"
    And the response field "address.city" should be "Gwenborough"
    And the response field "company.name" should be "Romaguera-Crona"
