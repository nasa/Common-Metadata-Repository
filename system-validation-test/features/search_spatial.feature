Feature: Find concepts by Spatial Search
  Spatial Search is a core feature of the CMR and several search mechanisms are supported

  Background:
    Given I use the authorization token from environment variable "CMR_TOKEN"
    And I am searching for "collections"
    And I want "json"

  @search @spatial
  Scenario: Collection Spatial Search using a single polygon
    Given I add a search param "polygon=10,10,30,10,30,20,10,20,10,10"
    When I submit a "GET" request
    Then the response status code is 200
    And the response "entry" count is at least 1  

  @search @spatial
  Scenario: Collection Spatial Search using multiple polygons
    Given I add a search param "polygon[]=10,10,30,10,30,20,10,20,10,10"
    And I add a search param "polygon[]=11,11,31,11,31,21,11,21,11,11"
    When I submit a "GET" request
    Then the response status code is 200
    And the response "entry" count is at least 1

  @search @spatial
  Scenario: Collection Spatial Search using a single bounding box
    Given I add a search param "bounding_box[]=-10,-5,10,5"
    When I submit a "GET" request
    Then the response status code is 200
    And the response "entry" count is at least 1

  @search @spatial
  Scenario: Collection Spatial Search using a multiple bounding boxes
    Given I add a search param "bounding_box[]=-10,-5,10,5"
    And I add a search param "bounding_box[]=-11,-6,11,6"
    And I add a search param "options[bounding_box][or]=true"
    When I submit a "GET" request
    Then the response status code is 200
    And the response "entry" count is at least 1
