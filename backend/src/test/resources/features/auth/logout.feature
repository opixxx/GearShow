Feature: 로그아웃
  인증된 사용자는 로그아웃하여 Refresh Token을 무효화할 수 있다.

  @auth @smoke
  Scenario: 인증된 사용자가 로그아웃하면 성공한다
    Given 카카오 인가 코드 "valid-code-logout"로 가입한 사용자가 존재한다
    When 발급받은 Access Token으로 로그아웃을 요청한다
    Then 응답 상태 코드는 200이다

  @auth @edge-case
  Scenario: 인증 없이 로그아웃하면 401 에러가 발생한다
    When 인증 없이 로그아웃을 요청한다
    Then 응답 상태 코드는 401이다
