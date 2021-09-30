Feature: Service Health Checks
  CMR provides healthcheck endpoints for the various exposed components.
  These should return a 200 status and information about the service when successful.

  @quick
  Scenario: Check status endpoints
    Given I am checking the <service> service status endpoint
    When I submit a "GET" request
    Then the response status code is 200
    And the response Content-Type is "application/json"
  Examples:
    | service          |
    | "search"         |
    | "access-control" |
    | "ingest"         |
