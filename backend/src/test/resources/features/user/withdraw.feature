Feature: 회원 탈퇴
  인증된 사용자는 자신의 계정을 탈퇴할 수 있다.

  @user @smoke
  Scenario: 인증된 사용자가 회원 탈퇴한다
    Given 카카오 인가 코드 "valid-code-withdraw"로 가입한 사용자가 존재한다
    When 발급받은 Access Token으로 회원 탈퇴를 요청한다
    Then 응답 상태 코드는 200이다

  @user @edge-case
  Scenario: 인증 없이 회원 탈퇴하면 401 에러가 발생한다
    When 인증 없이 회원 탈퇴를 요청한다
    Then 응답 상태 코드는 401이다
