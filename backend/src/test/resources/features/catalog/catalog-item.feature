Feature: 카탈로그 아이템
  인증된 사용자는 카탈로그 아이템을 등록/수정하고, 누구나 목록/상세를 조회할 수 있다.

  @catalog @smoke
  Scenario: 축구화 카탈로그 아이템을 등록하고 상세를 조회한다
    Given 카카오 인가 코드 "valid-code-catalog1"로 가입한 사용자가 존재한다
    When 축구화 카탈로그 아이템 등록을 요청한다
    Then 응답 상태 코드는 201이다
    And 응답의 data에 "catalogItemId" 필드가 존재한다
    When 등록된 카탈로그 아이템 상세를 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "category" 필드는 "BOOTS"이다
    And 응답의 data의 "brand" 필드는 "Nike"이다

  @catalog
  Scenario: 유니폼 카탈로그 아이템을 등록한다
    Given 카카오 인가 코드 "valid-code-catalog2"로 가입한 사용자가 존재한다
    When 유니폼 카탈로그 아이템 등록을 요청한다
    Then 응답 상태 코드는 201이다

  @catalog @smoke
  Scenario: 카탈로그 아이템 목록을 커서 페이징으로 조회한다
    Given 카카오 인가 코드 "valid-code-catalog3"로 가입한 사용자가 존재한다
    And 축구화 카탈로그 아이템을 등록한다
    When 카탈로그 아이템 목록을 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "data" 필드가 존재한다

  @catalog
  Scenario: 카탈로그 아이템을 수정한다
    Given 카카오 인가 코드 "valid-code-catalog4"로 가입한 사용자가 존재한다
    And 축구화 카탈로그 아이템을 등록한다
    When 등록된 카탈로그 아이템의 브랜드를 "Adidas"로 수정한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "brand" 필드는 "Adidas"이다

  @catalog @edge-case
  Scenario: 존재하지 않는 카탈로그 아이템을 조회하면 404 에러가 발생한다
    When 존재하지 않는 카탈로그 아이템 ID 999로 조회한다
    Then 응답 상태 코드는 404이다

  @catalog @edge-case
  Scenario: 인증 없이 카탈로그 아이템을 등록하면 401 에러가 발생한다
    When 인증 없이 축구화 카탈로그 아이템을 등록한다
    Then 응답 상태 코드는 401이다

  # ADR-016 한국어 alias 페이로드
  @catalog @adr-016
  Scenario: 한국어/영문 풀네임 + 사일로 한국어 alias 를 포함해 축구화를 등록한다
    Given 카카오 인가 코드 "valid-code-catalog-ko1"로 가입한 사용자가 존재한다
    When 한국어 alias 를 포함한 축구화 카탈로그 아이템 등록을 요청한다
    Then 응답 상태 코드는 201이다
    When 등록된 카탈로그 아이템 상세를 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "fullNameKo" 필드는 "나이키 머큐리얼 슈퍼플라이"이다
    And 응답의 data의 "fullNameEn" 필드는 "Nike Mercurial Superfly"이다
    And 응답의 data의 "bootsSpec.siloNameKo" 필드는 "머큐리얼 슈퍼플라이"이다

  @catalog @adr-016
  Scenario: 빈티지 유니폼 (kitType 미명시) 을 등록한다
    Given 카카오 인가 코드 "valid-code-catalog-vintage"로 가입한 사용자가 존재한다
    When 빈티지 유니폼 카탈로그 아이템 등록을 요청한다
    Then 응답 상태 코드는 201이다
    When 등록된 카탈로그 아이템 상세를 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "uniformSpec.clubNameKo" 필드는 "맨체스터 유나이티드"이다
    And 응답의 data의 "uniformSpec.season" 필드는 "1988/90"이다

  @catalog @adr-016
  Scenario: 한국어 풀네임을 PATCH 로 정정한다
    Given 카카오 인가 코드 "valid-code-catalog-patch"로 가입한 사용자가 존재한다
    And 한국어 alias 를 포함한 축구화 카탈로그 아이템을 등록한다
    When 등록된 카탈로그 아이템의 한국어 풀네임을 "나이키 프리미어 3 FG 화이트"로 정정한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "fullNameKo" 필드는 "나이키 프리미어 3 FG 화이트"이다
    And 응답의 data의 "fullNameEn" 필드는 "Nike Mercurial Superfly"이다

  # 목록 필터링 (category / keyword)
  @catalog @filter
  Scenario: 카탈로그 목록을 BOOTS 카테고리로 필터링하여 조회한다
    Given 카카오 인가 코드 "valid-code-catalog-filter-cat"로 가입한 사용자가 존재한다
    And 축구화 카탈로그 아이템을 등록한다
    When 카탈로그 아이템 목록을 카테고리 "BOOTS"로 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "data" 필드가 존재한다

  @catalog @filter
  Scenario: 카탈로그 목록을 키워드로 필터링하여 조회한다
    Given 카카오 인가 코드 "valid-code-catalog-filter-kw"로 가입한 사용자가 존재한다
    And 축구화 카탈로그 아이템을 등록한다
    When 카탈로그 아이템 목록을 키워드 "Nike"로 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "data" 필드가 존재한다

