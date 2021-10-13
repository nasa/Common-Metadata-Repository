Feature: Service Health Checks
  CMR provides healthcheck endpoints for the various exposed components.
  These should return a 200 status and information about the service when successful.

  @quick
  Scenario: Check status endpoints
    When I send a "GET" request to <health-check-endpoint>
    Then the response status code is 200
    And the response Content-Type is <content-type>
    Examples:
      | health-check-endpoint                | content-type       |
      | "/search/health"                     | "application/json" |
      | "/access-control/health"             | "application/json" |
      | "/ingest/health"                     | "application/json" |
      | "/legacy-services/rest/availability" | "application/xml"  |
