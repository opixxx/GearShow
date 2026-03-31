Feature: Health Check
  The API should expose a health check endpoint to verify the application is running.

  Scenario: Health check returns UP status
    When I send a GET request to "/api/v1/health"
    Then the response status code should be 200
    And the response field "status" should be "UP"
