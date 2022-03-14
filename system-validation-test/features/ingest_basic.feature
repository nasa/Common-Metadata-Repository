Feature: Basic Ingest API calls
  Ingest collections for various formats

  Background:
    Given I use the authorization token from environment variable "CMR_TOKEN"
    And the provider from environment variable "CMR_TEST_PROV" exists

    And set body to the following XML "<Collection><ShortName>TestCollection8021</ShortName><VersionId>Version01</VersionId><InsertTime>1999-12-31T19:00:00-05:00</InsertTime><LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate><LongName>CMR8021COLLECTION</LongName><DataSetId>LarcDatasetId01</DataSetId><Description>A minimal valid collection</Description><Orderable>true</Orderable><Visible>true</Visible></Collection>"

  @ingest
  Scenario: Ingest of a Collection
    Given I am ingesting a "Collection"
    And I add extension "providers/DEMO_PROV/collections/TestCollection8021"
    And I add header "Content-type=application/echo10+xml"
    When I submit a "PUT" request
    Then the response status code is in "200,201"

  @ingest
  Scenario: Searching for ingested Collection
    Given I am ingesting a "Collection"
    And I add extension "providers/DEMO_PROV/collections/TestCollection8021"
    And I add header "Content-type=application/echo10+xml"
    When I submit a "PUT" request
    And I wait "2.25" seconds for ingest to complete
    Given I am searching for "collections"
    And I clear the extension
    And I clear headers
    And I clear the body
    And I use the authorization token from environment variable "CMR_TOKEN"
    And I want "json"
    And I add a query param "short_name" of "TestCollection8021"
    When I submit a "GET" request
    Then the response status code is 200
    And the response body contains one of "data_center"

  @ingest
  Scenario: Deleting ingested Collection
    Given I am ingesting a "Collection"
    And I add extension "providers/DEMO_PROV/collections/TestCollection8021"
    And I add header "Content-type=application/echo10+xml"
    When I submit a "PUT" request
    And I wait "2.25" seconds for ingest to complete
    Given I am deleting on "ingest"
    And I clear the extension
    And I clear headers
    And I clear the body
    And I use the authorization token from environment variable "CMR_TOKEN"
    And I add extension "providers/DEMO_PROV/collections/TestCollection8021"
    When I submit a "DELETE" request
    Then the response status code is 200
    And I wait "2.25" seconds for deletion to complete
    Given I am searching for "collections"
    And I clear the extension
    And I clear headers
    And I clear the body
    And I use the authorization token from environment variable "CMR_TOKEN"
    And I want "json"
    And I add a query param "short_name" of "TestCollection8021"
    When I submit a "GET" request
    Then the response status code is 200
    And the response body is empty
