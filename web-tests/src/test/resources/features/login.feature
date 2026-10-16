@web @login
Feature: Login
  As a shopper on the Sauce Demo store
  I want to sign in with my account
  So that I can browse and buy products

  Background:
    Given I am on the login page

  @tc:WEB-001 @regression @sanity @priority:high
  Scenario: Standard user logs in successfully
    When I log in as the standard user
    Then I should land on the products page

  @tc:WEB-002 @regression @priority:high
  Scenario: Locked-out user is refused
    When I log in as the locked user
    Then I should see the login error "Epic sadface: Sorry, this user has been locked out."

  @tc:WEB-003 @regression @priority:medium
  Scenario: Wrong password is rejected
    When I log in with username "standard_user" and password "not-the-password"
    Then I should see the login error "Epic sadface: Username and password do not match any user in this service"

  @tc:WEB-004 @regression @priority:medium
  Scenario: Username is mandatory
    When I log in with username "" and password "secret_sauce"
    Then I should see the login error "Epic sadface: Username is required"
