Feature: 카탈로그 아이템
  인증된 사용자는 카탈로그 아이템을 등록하고, 누구나 상세를 조회할 수 있다.

  @catalog @smoke
  Scenario: 축구화 카탈로그 아이템을 등록하고 상세를 조회한다
    Given 카카오 인가 코드 "valid-code-catalog1"로 가입한 사용자가 존재한다
    When 축구화 카탈로그 아이템을 등록한다
    Then 응답 상태 코드는 201이다
    And 응답의 data에 "catalogItemId" 필드가 존재한다
    When 등록된 카탈로그 아이템 상세를 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "category" 필드는 "BOOTS"이다
    And 응답의 data의 "brand" 필드는 "Nike"이다

  @catalog
  Scenario: 유니폼 카탈로그 아이템을 등록한다
    Given 카카오 인가 코드 "valid-code-catalog2"로 가입한 사용자가 존재한다
    When 유니폼 카탈로그 아이템을 등록한다
    Then 응답 상태 코드는 201이다

  @catalog @edge-case
  Scenario: 존재하지 않는 카탈로그 아이템을 조회하면 404 에러가 발생한다
    When 존재하지 않는 카탈로그 아이템 ID 999로 조회한다
    Then 응답 상태 코드는 404이다

  @catalog @edge-case
  Scenario: 인증 없이 카탈로그 아이템을 등록하면 401 에러가 발생한다
    When 인증 없이 축구화 카탈로그 아이템을 등록한다
    Then 응답 상태 코드는 401이다
