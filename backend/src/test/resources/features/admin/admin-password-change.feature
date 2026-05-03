Feature: 관리자 비밀번호 변경
  인증된 admin 은 자신의 비밀번호를 회전할 수 있다.
  부트스트랩된 임시 비밀번호를 영구 비밀번호로 교체하여 ADMIN_PASSWORD 환경변수를 제거하는 운영 절차를 지원한다.

  @admin @password @smoke
  Scenario: 정상 비밀번호 변경 후 새 비밀번호로 재로그인한다
    Given 관리자가 로그인되어 있다
    When 관리자가 새 비밀번호 "new-strong-pw-1234"로 변경 요청한다
    Then 응답 상태 코드는 200이다
    When 새 비밀번호 "new-strong-pw-1234"로 관리자 로그인을 요청한다
    Then 응답 상태 코드는 200이다

  @admin @password
  Scenario: 현재 비밀번호가 틀리면 401 ADMIN_INVALID_CREDENTIALS
    Given 관리자가 로그인되어 있다
    When 관리자가 잘못된 현재 비밀번호로 변경 요청한다
    Then 응답 상태 코드는 401이다

  @admin @password @edge-case
  Scenario: 새 비밀번호가 8자 미만이면 400 INVALID_INPUT
    Given 관리자가 로그인되어 있다
    When 관리자가 새 비밀번호 "abc12"로 변경 요청한다
    Then 응답 상태 코드는 400이다

  @admin @password @edge-case
  Scenario: 새 비밀번호가 현재와 동일하면 400 ADMIN_PASSWORD_SAME_AS_CURRENT
    Given 관리자가 로그인되어 있다
    When 관리자가 현재와 동일한 비밀번호로 변경 요청한다
    Then 응답 상태 코드는 400이다
