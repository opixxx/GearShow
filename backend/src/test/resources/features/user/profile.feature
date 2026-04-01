Feature: 사용자 프로필
  인증된 사용자는 자신의 프로필을 조회/수정할 수 있고, 다른 사용자의 공개 프로필도 조회할 수 있다.

  @user @smoke
  Scenario: 인증된 사용자가 자신의 프로필을 조회한다
    Given 카카오 인가 코드 "valid-code-profile"로 가입한 사용자가 존재한다
    When 발급받은 Access Token으로 내 프로필을 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "nickname" 필드가 존재한다
    And 응답의 data의 "userStatus" 필드는 "ACTIVE"이다

  @user
  Scenario: 다른 사용자의 공개 프로필을 조회한다
    Given 카카오 인가 코드 "valid-code-other"로 가입한 사용자가 존재한다
    When 사용자 ID로 공개 프로필을 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "nickname" 필드가 존재한다

  @user
  Scenario: 인증된 사용자가 닉네임을 수정한다
    Given 카카오 인가 코드 "valid-code-update"로 가입한 사용자가 존재한다
    When 닉네임을 "새닉네임"으로 수정한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "nickname" 필드는 "새닉네임"이다

  @user @edge-case
  Scenario: 존재하지 않는 사용자의 프로필을 조회하면 404 에러가 발생한다
    When 존재하지 않는 사용자 ID 999로 프로필을 조회한다
    Then 응답 상태 코드는 404이다

  @user @edge-case
  Scenario: 인증 없이 내 프로필을 조회하면 401 에러가 발생한다
    When 인증 없이 내 프로필을 조회한다
    Then 응답 상태 코드는 401이다
