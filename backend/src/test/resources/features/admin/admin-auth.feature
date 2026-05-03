Feature: 관리자 인증 / RBAC 가드
  운영자는 별도 admin 계정(email + password)으로 로그인하여 ADMIN 권한 토큰을 받는다.
  /api/admin/** 경로는 ADMIN 토큰이 있을 때만 접근 가능하다.

  @admin @smoke
  Scenario: 부트스트랩된 관리자가 정상 로그인하면 200 + accessToken 을 받는다
    When 정상 관리자 로그인을 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "accessToken" 필드가 존재한다
    And 응답의 data에 "refreshToken" 필드가 존재한다

  @admin @edge-case
  Scenario: 잘못된 비밀번호 시 401 ADMIN_INVALID_CREDENTIALS
    When 관리자 로그인을 잘못된 비밀번호로 요청한다
    Then 응답 상태 코드는 401이다

  @admin @edge-case
  Scenario: 미존재 이메일 시에도 동일하게 401 (user enumeration 방지)
    When 관리자 로그인을 미존재 이메일로 요청한다
    Then 응답 상태 코드는 401이다
