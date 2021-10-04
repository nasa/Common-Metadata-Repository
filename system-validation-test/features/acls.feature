Feature: ACL

  Background:
    Given I use the authorization token from environment variable "CMR_TOKEN"

  @acls
  Scenario: Searching for ACLs
    Given I am searching for "acls"
    When I submit a "GET" request
    Then the response status code is 200

  @acls
  Scenario: Searching for groups
    Given I am searching for "groups"
    When I submit a "GET" request
    Then the response status code is 200

  @acls
  Scenario: Searching for permissions without any discriminator yields an error
    Given I am searching for "permissions"
    When I submit a "GET" request
    Then the response status code is 400

  @acls
  Scenario: Searching for permissions with user_id and concept_id
    Given I am searching for "collections"
    And I want "json"
    And I submit a "GET" request
    And I save the "first" result "id" as "my collection ID"

    When I am searching for "permissions"
    And I reset the extension
    And I clear the query
    And I add query param "user_id" using environment variable "CMR_USER"
    And I add query param "concept_id" using saved value "my collection ID"
    And I submit a "GET" request
    Then the response status code is 200
    And the response body contains one of "read, update, delete, order"
    
  @acls
  Scenario: Searching for s3-buckets without any discriminator yields an error
    Given I am searching for "s3-buckets"
    When I submit a "GET" request
    Then the response status code is 400

  @acls
  Scenario: Searching for s3-buckets
    Given I am searching for "s3-buckets"
    And I add query param "user_id" using environment variable "CMR_USER"
    When I submit a "GET" request
    Then the response status code is 200
