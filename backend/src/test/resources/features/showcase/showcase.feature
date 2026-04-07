Feature: 쇼케이스
  사용자가 장비 쇼케이스를 등록, 조회, 수정, 삭제할 수 있다.

  Background:
    Given 카카오 인가 코드 "valid-code"로 가입한 사용자가 존재한다
    And 축구화 카탈로그 아이템을 등록한다

  # ── 등록 (분기: 일반 vs 3D 모델 포함) ──

  @smoke @showcase
  Scenario: 일반 이미지만으로 쇼케이스를 등록한다
    When 일반 이미지만으로 쇼케이스를 등록한다
    Then 응답 상태 코드는 201이다
    And 응답의 data에 "showcaseId" 필드가 존재한다
    And 응답의 data의 "model3dStatus" 필드는 null이다

  @smoke @showcase @model3d
  Scenario: 3D 모델 소스 이미지와 함께 쇼케이스를 등록한다
    When 3D 모델 소스 이미지 4장과 함께 쇼케이스를 등록한다
    Then 응답 상태 코드는 201이다
    And 응답의 data에 "showcaseId" 필드가 존재한다
    And 응답의 data의 "model3dStatus" 필드는 "REQUESTED"이다

  @edge-case @showcase @model3d
  Scenario: 3D 모델 소스 이미지가 4장 미만이면 400 에러가 발생한다
    When 3D 모델 소스 이미지 2장과 함께 쇼케이스를 등록한다
    Then 응답 상태 코드는 400이다

  @smoke @showcase
  Scenario: 축구화 스펙을 포함하여 쇼케이스를 등록하고 상세에서 확인한다
    When 축구화 스펙과 함께 쇼케이스를 등록한다
    Then 응답 상태 코드는 201이다
    When 등록된 쇼케이스 상세를 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "category" 필드는 "BOOTS"이다
    And 응답의 data의 "brand" 필드는 "Nike"이다
    And 응답의 data에 "bootsSpec" 필드가 존재한다

  @showcase
  Scenario: 카탈로그 없이 직접 입력으로 쇼케이스를 등록한다
    When 카탈로그 없이 직접 입력으로 쇼케이스를 등록한다
    Then 응답 상태 코드는 201이다
    When 등록된 쇼케이스 상세를 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "brand" 필드는 "Adidas"이다

  # ── 상세 조회 ──

  @smoke @showcase
  Scenario: 쇼케이스 상세를 조회한다
    Given 이미지 1개로 쇼케이스가 등록되어 있다
    When 등록된 쇼케이스 상세를 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data의 "title" 필드는 "머큐리얼 슈퍼플라이 착용 후기"이다
    And 응답의 data의 "conditionGrade" 필드는 "A"이다

  # ── 수정 ──

  @showcase
  Scenario: 쇼케이스를 수정한다
    Given 이미지 1개로 쇼케이스가 등록되어 있다
    When 등록된 쇼케이스의 제목을 "수정된 제목"으로 수정한다
    Then 응답 상태 코드는 200이다
    When 등록된 쇼케이스 상세를 조회한다
    Then 응답의 data의 "title" 필드는 "수정된 제목"이다

  # ── 삭제 ──

  @showcase
  Scenario: 쇼케이스를 삭제한다
    Given 이미지 1개로 쇼케이스가 등록되어 있다
    When 등록된 쇼케이스를 삭제한다
    Then 응답 상태 코드는 200이다

  # ── 목록 조회 ──

  @showcase
  Scenario: 쇼케이스 목록을 조회한다
    Given 이미지 1개로 쇼케이스가 등록되어 있다
    When 쇼케이스 목록을 조회한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "data" 필드가 존재한다

  @showcase
  Scenario: 내 쇼케이스 목록을 조회한다
    Given 이미지 1개로 쇼케이스가 등록되어 있다
    When 내 쇼케이스 목록을 조회한다
    Then 응답 상태 코드는 200이다

  # ── 이미지 관리 ──

  @showcase
  Scenario: 쇼케이스에 이미지를 추가한다
    Given 이미지 1개로 쇼케이스가 등록되어 있다
    When 등록된 쇼케이스에 이미지 2개를 추가한다
    Then 응답 상태 코드는 201이다
    And 응답의 data에 "addedImageIds" 필드가 존재한다

  @showcase
  Scenario: 쇼케이스 이미지 정렬 순서를 변경한다
    Given 이미지 1개로 쇼케이스가 등록되어 있다
    When 등록된 쇼케이스의 이미지 정렬 순서를 변경한다
    Then 응답 상태 코드는 200이다

  # ── 3D 모델 ──

  @showcase @model3d
  Scenario: 3D 모델 생성을 재요청하고 상태를 조회한다
    Given 이미지 1개로 쇼케이스가 등록되어 있다
    When 등록된 쇼케이스에 3D 모델 생성을 요청한다
    Then 응답 상태 코드는 202이다
    When 등록된 쇼케이스의 3D 모델 상태를 조회한다
    Then 응답 상태 코드는 200이다

  # ── Presigned URL 발급 ──

  @smoke @showcase @presigned-url
  Scenario: 쇼케이스 등록 전 SHOWCASE_IMAGE 유형의 Presigned URL을 발급받는다
    When SHOWCASE_IMAGE 유형으로 Presigned URL 2개를 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 Presigned URL 목록이 2개 반환된다
    And 반환된 각 항목에 "presignedUrl" 필드가 존재한다
    And 반환된 각 항목에 "s3Key" 필드가 존재한다

  @smoke @showcase @presigned-url @model3d
  Scenario: 쇼케이스 등록 전 MODEL_SOURCE 유형의 Presigned URL을 발급받는다
    When MODEL_SOURCE 유형으로 Presigned URL 4개를 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 Presigned URL 목록이 4개 반환된다
    And 반환된 s3Key 는 "showcases/model-source/" 경로를 포함한다

  @smoke @showcase @presigned-url
  Scenario: 기존 쇼케이스에 이미지를 추가하기 위한 Presigned URL을 발급받는다
    Given 이미지 1개로 쇼케이스가 등록되어 있다
    When 등록된 쇼케이스의 이미지 추가용 Presigned URL 1개를 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 Presigned URL 목록이 1개 반환된다

  @edge-case @showcase @presigned-url
  Scenario: 파일 목록이 비어있으면 Presigned URL 발급 시 400 에러가 발생한다
    When 빈 파일 목록으로 Presigned URL을 요청한다
    Then 응답 상태 코드는 400이다

  # ── 에러 케이스 ──

  @edge-case @showcase
  Scenario: 존재하지 않는 쇼케이스를 조회하면 404 에러가 발생한다
    When 존재하지 않는 쇼케이스 ID 99999로 조회한다
    Then 응답 상태 코드는 404이다

  @edge-case @showcase
  Scenario: 인증 없이 쇼케이스를 등록하면 401 에러가 발생한다
    When 인증 없이 쇼케이스를 등록한다
    Then 응답 상태 코드는 401이다
