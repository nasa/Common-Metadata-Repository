Feature: CMR limits access to certain concepts and data to users with proper credentials

  @search
  Scenario: Using invalid Echo-Token header
    Given I am searching for "collections"
    And I set header "Echo-Token=invalid"
    When I submit a "GET" request
    Then the response status code is 401

  @search
  Scenario: Using Authorization header
    Given I am searching for "collections"
    And I use the authorization token from environment variable "CMR_TOKEN"
    When I submit a "GET" request
    Then the response status code is 200

  @search
  Scenario: Using an invalid Authorization header value
    Given I am searching for "collections"
    And I set header "Authorization=invalid"
    When I submit a "GET" request
    Then the response status code is 401
    And the response body contains "Token does not exist"
