@android @mobile @menu
Feature: Side menu
  As a logged-in shopper
  I want to use the side menu

  Background:
    Given I am logged in as the standard user

  @tc:AND-010 @regression @sanity @priority:medium
  Scenario: Logout from the menu returns to the login screen
    When I log out
    Then I should see the login screen
