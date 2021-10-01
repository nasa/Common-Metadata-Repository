Feature: CMR limits access to certain concepts and data to users with proper credentials

  @search
  Scenario: Using Authorization header
    Given I am searching for "collections"
    And I set header "Authorization" using environment variable "CMR_USER_TOKEN"
    When I submit a "GET" request
    Then the response status code is 200

  @search @only
  Scenario: Using an invalid Authorization header value
    Given I am searching for "collections"
    And I set header "Authorization=bad"
    When I submit a "GET" request
    Then the response status code is 401
    And the response body contains "Token does not exist"
