@api @posts
Feature: Posts resource - read operations
  As a consumer of the JSONPlaceholder API
  I want the /posts read endpoints to honour their published contract
  So that client applications can rely on the response shape and status codes

  @tc:API-001 @regression @sanity @priority:high
  Scenario: Retrieve all posts
    When I send a GET request to "/posts"
    Then the response status code should be 200
    And the response header "Content-Type" should contain "application/json"
    And the response should match the "post-list" contract
    And the response should be a list of 100 items

  @tc:API-002 @regression @sanity @priority:high
  Scenario: Retrieve a single post by id
    When I send a GET request to "/posts/1"
    Then the response status code should be 200
    And the response should match the "post" contract
    And the response field "id" should be "1"
    And the response field "userId" should be "1"
    And the response time should be below 5000 ms

  @tc:API-003 @regression @priority:medium
  Scenario: Requesting a post that does not exist returns 404
    When I send a GET request to "/posts/9999"
    Then the response status code should be 404
    And the response body should be "{}"

  @tc:API-004 @regression @priority:medium
  Scenario: Filter posts by author
    Given the query parameter "userId" is "1"
    When I send a GET request to "/posts"
    Then the response status code should be 200
    And the response should match the "post-list" contract
    And the response should be a list of 10 items
    And every item field "userId" should be "1"

  @tc:API-005 @regression @priority:medium
  Scenario: Retrieve the comments of a post
    When I send a GET request to "/posts/1/comments"
    Then the response status code should be 200
    And the response should match the "comment-list" contract
    And every item field "postId" should be "1"
