@api @posts
Feature: Posts resource - write operations
  As a consumer of the JSONPlaceholder API
  I want create, update and delete on /posts to behave as documented
  (JSONPlaceholder fakes persistence, so responses are verified, not stored state)

  @tc:API-006 @regression @sanity @priority:high
  Scenario: Create a new post
    When I send a POST request to "/posts" with body:
      """
      { "title": "Automation pilot", "body": "Created by the Enterprise Automation Framework", "userId": 1 }
      """
    Then the response status code should be 201
    And the response should match the "post" contract
    And the response field "id" should be "101"
    And the response field "title" should be "Automation pilot"

  @tc:API-007 @regression @priority:medium
  Scenario: Replace an existing post
    When I send a PUT request to "/posts/1" with body:
      """
      { "id": 1, "title": "Replaced title", "body": "Replaced body", "userId": 1 }
      """
    Then the response status code should be 200
    And the response should match the "post" contract
    And the response field "title" should be "Replaced title"
    And the response field "body" should be "Replaced body"

  @tc:API-008 @newfeature @priority:medium
  Scenario: Partially update a post title
    When I send a PATCH request to "/posts/1" with body:
      """
      { "title": "Patched title" }
      """
    Then the response status code should be 200
    And the response should match the "post" contract
    And the response field "title" should be "Patched title"
    And the response field "userId" should be "1"

  @tc:API-009 @regression @priority:low
  Scenario: Delete a post
    When I send a DELETE request to "/posts/1"
    Then the response status code should be 200
    And the response body should be "{}"
