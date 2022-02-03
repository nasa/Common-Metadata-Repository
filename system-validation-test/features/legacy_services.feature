Feature: Legacy Services comprises a set of SOAP and RESTful endpoints

  @quick
  Scenario: Legacy Services
    When I send a "GET" request to "/legacy-services/apis.html"
    Then the response status code is 200
    And the response Content-Type is "text/html"
