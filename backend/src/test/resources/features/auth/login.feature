Feature: 소셜 로그인
  사용자는 카카오 소셜 로그인을 통해 서비스에 로그인할 수 있다.

  @auth @smoke
  Scenario: 신규 사용자가 카카오 로그인을 수행하면 자동 가입 후 토큰이 발급된다
    When 카카오 인가 코드 "valid-code"로 로그인을 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "accessToken" 필드가 존재한다
    And 응답의 data에 "refreshToken" 필드가 존재한다
    And 응답의 data의 "tokenType" 필드는 "Bearer"이다

  @auth
  Scenario: 기존 사용자가 카카오 로그인을 수행하면 토큰이 발급된다
    Given 카카오 인가 코드 "valid-code-existing"로 가입한 사용자가 존재한다
    When 카카오 인가 코드 "valid-code-existing"로 로그인을 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "accessToken" 필드가 존재한다

  @auth @edge-case
  Scenario: 유효하지 않은 인가 코드로 로그인하면 400 에러가 발생한다
    When 카카오 인가 코드 "invalid-code"로 로그인을 요청한다
    Then 응답 상태 코드는 400이다

  @auth @edge-case
  Scenario: 지원하지 않는 제공자로 로그인하면 400 에러가 발생한다
    When "naver" 인가 코드 "valid-code"로 로그인을 요청한다
    Then 응답 상태 코드는 400이다
