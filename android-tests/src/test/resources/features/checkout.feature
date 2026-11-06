@android @mobile @checkout
Feature: Checkout
  As a logged-in shopper with items in my cart
  I want to place an order

  Background:
    Given I am logged in as the standard user
    And I add "Sauce Labs Backpack" to the cart
    And I open the cart

  @tc:AND-008 @regression @sanity @priority:high
  Scenario: Complete a purchase end to end
    When I check out with first name "Ada", last name "Lovelace" and postal code "10115"
    And I finish the order
    Then I should see the order confirmation "THANK YOU FOR YOU ORDER"

  @tc:AND-009 @newfeature @priority:medium
  Scenario: First name is required at checkout
    When I check out with first name "", last name "Lovelace" and postal code "10115"
    Then I should see the checkout error "First Name is required"
