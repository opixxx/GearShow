Feature: 카탈로그 아이템 일괄 등록
  관리자(ADMIN 권한 토큰)는 카탈로그 아이템을 일괄로 등록할 수 있으며, 각 항목은 독립된 트랜잭션으로 처리된다.

  @catalog @bulk @smoke
  Scenario: 축구화 1건 + 유니폼 1건을 일괄 등록하면 created=2 가 반환된다
    Given 관리자가 로그인되어 있다
    When 축구화 1건과 유니폼 1건을 일괄 등록 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "created" 필드는 "2"이다
    And 응답의 data의 "skippedDuplicate" 필드는 "0"이다
    And 응답의 data의 "failed" 필드는 "0"이다

  @catalog @bulk
  Scenario: 사전 등록된 모델 코드와 충돌하는 항목은 skippedDuplicate 로 분류된다
    Given 카카오 인가 코드 "valid-code-bulk-dup"로 가입한 사용자가 존재한다
    And 모델 코드 "BULK-DUP-001"인 축구화 카탈로그 아이템이 사전 등록되어 있다
    And 관리자가 로그인되어 있다
    When 모델 코드 "BULK-DUP-001"인 축구화 1건과 모델 코드 "BULK-NEW-001"인 축구화 1건을 일괄 등록 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "created" 필드는 "1"이다
    And 응답의 data의 "skippedDuplicate" 필드는 "1"이다
    And 응답의 data의 errors 첫 항목의 "errorCode" 필드는 "CATALOG_ITEM_DUPLICATE_MODEL_CODE"이다

  @catalog @bulk @edge-case
  Scenario: 풋살화 (BOOTS + StudType.TF) 1건을 일괄 등록한다
    Given 관리자가 로그인되어 있다
    When 풋살화 1건을 일괄 등록 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "created" 필드는 "1"이다

  @catalog @bulk @edge-case
  Scenario: items 가 빈 배열이면 400 INVALID_INPUT 이 반환된다
    Given 관리자가 로그인되어 있다
    When 빈 배열을 일괄 등록 요청한다
    Then 응답 상태 코드는 400이다
