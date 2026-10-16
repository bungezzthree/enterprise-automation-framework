@web @catalog
Feature: Product catalog
  As a logged-in shopper
  I want to browse and sort the product catalog

  Background:
    Given I am logged in as the standard user

  @tc:WEB-005 @regression @sanity @priority:high
  Scenario: Catalog lists all products with name and price
    Then I should see 6 products
    And every product should have a name and a price

  @tc:WEB-006 @newfeature @priority:medium
  Scenario: Sort products by price from low to high
    When I sort products by "Price (low to high)"
    Then product prices should be in ascending order
    And the first product should be "Sauce Labs Onesie"
