Feature: 토큰 갱신
  사용자는 Refresh Token으로 새로운 Access Token을 발급받을 수 있다.

  @auth @smoke
  Scenario: 유효한 Refresh Token으로 토큰을 갱신한다
    Given 카카오 인가 코드 "valid-code-refresh"로 가입한 사용자가 존재한다
    When 발급받은 Refresh Token으로 토큰 갱신을 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "accessToken" 필드가 존재한다
    And 응답의 data에 "refreshToken" 필드가 존재한다

  @auth @edge-case
  Scenario: 존재하지 않는 Refresh Token으로 갱신하면 401 에러가 발생한다
    When Refresh Token "nonexistent-token"으로 토큰 갱신을 요청한다
    Then 응답 상태 코드는 401이다
