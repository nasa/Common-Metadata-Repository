Feature: CMR Search Parameters
  CMR supports many parameters to support complex queries to retrieve concepts

  Background:
    Given I use the authorization token from environment variable "CMR_TOKEN"
    And I want "json"

  @search
  Scenario: Searching for collections using has_granules=false
    Given I am searching for "collections"
    When I add query param "has_granules=<has_granules>"
    And I submit a "GET" request
    And I save the "first" result "id" as "my collection ID"
    And I clear the query
    And I am searching for "granules"
    And I add query term "collection_concept_id" using saved value "my collection ID"
    And I submit a "GET" request
    Then the response header "CMR-Hits" <result>
    Examples:
      | has_granules | result     |
      | false        | is "0"     |
      | true         | is not "0" |

  @Search
  Scenario: I am mimicking the EDSC search
    Given I am searching for "collections"
    When I add query param "has_granules_or_cwic=true"
    And I submit a "GET" request
    Then the response status code is 200
