Feature: ACL

  Background:
    Given I am not logged in

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
  Scenario: Searching for s3-buckets without any discriminator yields an error
    Given I am searching for "s3-buckets"
    When I submit a "GET" request
    Then the response status code is 400
