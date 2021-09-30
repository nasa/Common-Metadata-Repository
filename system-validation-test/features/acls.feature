Feature: ACL

  Background:
    Given I am not logged in

  @acls
  Scenario:
    Given I am searching for "acls"
    When I submit a "GET" request
    Then the response status code is 200
