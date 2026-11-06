@android @mobile @catalog
Feature: Product catalog
  As a logged-in shopper
  I want to browse and sort the products

  Background:
    Given I am logged in as the standard user

  @tc:AND-004 @regression @sanity @priority:high
  Scenario: Product list shows name and price
    Then I should see the product "Sauce Labs Backpack" priced "$29.99"

  @tc:AND-005 @newfeature @priority:medium
  Scenario: Sort products by price from low to high
    When I sort products by "Price (low to high)"
    Then the first product should be "Sauce Labs Onesie"
