@android @mobile @login
Feature: Login on the Swag Labs Android app
  As a shopper using the mobile app
  I want to sign in with my account
  So that I can browse and buy products

  @tc:AND-001 @regression @sanity @priority:high
  Scenario: Standard user logs in successfully
    When I log in as the standard user
    Then I should see the products screen

  @tc:AND-002 @regression @priority:high
  Scenario: Locked-out user is refused
    When I log in as the locked user
    Then I should see the login error "Sorry, this user has been locked out."

  @tc:AND-003 @regression @priority:medium
  Scenario: Wrong password is rejected
    When I log in with username "standard_user" and password "not-the-password"
    Then I should see the login error "Username and password do not match any user in this service."
