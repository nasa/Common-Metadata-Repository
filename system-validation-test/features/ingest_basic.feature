Feature: Basic Ingest API calls
  Ingest collections for various formats

  Background:
    Given I use the authorization token from environment variable "CMR_TOKEN"

  @ingest
  Scenario: Ingest of a Collection on PROV1
    Given I am ingesting a "Collection" on "ingest"
    And I add extension "providers/PROV1/collections/TestColl01"
    And I add header "Content-type=application/echo10+xml"
    And I add body param "ShortName=TestColl01"
    And I add body param "VersionId=Version01"
    And I add body param "InsertTime=1999-12-31T19:00:00-05:00"
    And I add body param "LastUpdate=1999-12-31T19:00:00-05:00"
    And I add body param "LongName=TestCollection01"
    And I add body param "DataSetId=LarcDatasetId"
    And I add body param "Description=A minimal valid collection"
    And I add body param "Orderable=true"
    And I add body param "Visible=true"
    When I submit a "PUT" request
    Then the response status code is 200

  @ingest
  Scenario: Searching for ingested Collection
    Given I am searching for "collections"
    And I want "json"
    And I add a query param "short_name" of "TestColl01"
    When I submit a "GET" request
    Then the response status code is 200
    And the response body contains one of "data_center"

  @ingest
  Scenario: Deleting ingested Collection
    Given I am deleting on "ingest"
    And I add extension "providers/PROV1/collections/TestColl01"
    When I submit a "DELETE" request
    Then the response status code is 200
