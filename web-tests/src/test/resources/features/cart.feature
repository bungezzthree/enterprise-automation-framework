@web @cart
Feature: Shopping cart
  As a logged-in shopper
  I want to add and remove products in my cart

  Background:
    Given I am logged in as the standard user

  @tc:WEB-007 @regression @sanity @priority:high
  Scenario: Add a product to the cart
    When I add "Sauce Labs Backpack" to the cart
    Then the cart badge should show "1"
    When I open the cart
    Then the cart should contain "Sauce Labs Backpack"

  @tc:WEB-008 @regression @priority:medium
  Scenario: Remove a product from the cart
    When I add "Sauce Labs Bike Light" to the cart
    And I remove "Sauce Labs Bike Light" from the cart
    Then the cart badge should not be displayed
