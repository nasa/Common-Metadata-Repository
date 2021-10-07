Feature: Basic Search API Calls
  Search for various types of concepts with CMR for different formats
  Check basic search restrictions

  Background:
    Given I use the authorization token from environment variable "CMR_TOKEN"

  @search
  Scenario: Concept Search
    Given I am searching for "<concept-type>"
    When I submit a "GET" request
    Then the response status code is 200
    Examples:
      | concept-type |
      | collections  |
      | tools        |
      | services     |

  @search
  Scenario: Collection search using basic operators that are commonly used
    Given I am searching for "collections"
    And I want "json"
    When I submit a "GET" request
    And I add search param "science_keyword=ice"
    And I add search param "include_granule_count=true"
    Then the response status code is 200
    And the response "entry" count is at least 1

  @search
  Scenario: Concept Search but anonymous
    Given I am not logged in
    And I am searching for "<concept-type>"
    When I submit a "GET" request
    Then the response status code is 200
    Examples:
      | concept-type |
      | collections  |
      | tools        |
      | services     |

  @search
  Scenario: Granule searches are not allowed with no parameters
    Given I am searching for "granules"
    When I submit a "GET" request
    Then the response status code is 400
    And the response body contains "The CMR does not allow querying across granules in all collections."

  @search
  Scenario: Granule with parameters are allowed
    Given I am searching for "granules"
    And I add a query param "collection_concept_id" of "C1711961296-LPCLOUD"
    When I submit a "GET" request
    Then the response status code is 200

  @search
  Scenario: Searching collections by format returns the correct mime-type
    Given I am searching for "collections"
    And I use extension <extension>
    When I submit a "GET" request
    Then the response Content-Type is <mime-type>
    Examples:
      | extension   | mime-type                                          |
      | ".atom"     | "application/atom+xml"                             |
      | ".dif"      | "application/dif+xml"                              |
      | ".dif10"    | "application/dif10+xml"                            |
      | ".echo10"   | "application/echo10+xml"                           |
      | ".iso"      | "application/iso19115+xml"                         |
      | ".iso19115" | "application/iso19115+xml"                         |
      | ".json"     | "application/json"                                 |
      | ".native"   | "application/metadata+xml"                         |
      | ".umm_json" | "application/vnd.nasa.cmr.umm_results+json"        |
      | ".umm-json" | "application/vnd.nasa.cmr.legacy_umm_results+json" |
      | ".xml"      | "application/xml"                                  |

  @search
  Scenario: Expected headers are returned
    Given I am searching for "collections"
    When I submit a "GET" request
    Then the response header contains an entry for "<header>"
    Examples:
      |header         |
      |CMR-Took       |
      |CMR-request-id |
      |CMR-Hits       |

  @search
  Scenario: Paging using page_num defaults to page 1 when not specified
    Given I am searching for "collections"
    And I want "json"
    When I submit a "GET" request
    And I save the results as "first_page"
    And I add query param "page_num=1"
    And I submit another "GET" request
    And I save the results as "page_1"
    Then saved value "first_page" is equal to saved value "page_1"

  @search
  Scenario: Paging using page_num and changing pages changes results
    Given I am searching for "collections"
    And I want "json"
    When I submit a "GET" request
    And I save the results as "first_page"
    And I set query param "page_num=2"
    And I submit a "GET" request
    And I save the results as "page_2"
    And I set query param "page_num=1"
    And I submit a "GET" request
    And I save the results as "page_1"
    Then saved value "first_page" does not equal saved value "page_2"
    And saved value "first_page" is equal to saved value "page_1"
