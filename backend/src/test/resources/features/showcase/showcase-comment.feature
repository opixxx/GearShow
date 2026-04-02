Feature: 쇼케이스 댓글
  사용자가 쇼케이스에 댓글을 작성, 조회, 수정, 삭제할 수 있다.

  Background:
    Given 카카오 인가 코드 "valid-code"로 가입한 사용자가 존재한다
    And 축구화 카탈로그 아이템을 등록한다
    And 이미지 1개로 쇼케이스가 등록되어 있다

  @smoke @showcase
  Scenario: 댓글을 작성하고 목록을 조회한다
    When 쇼케이스에 "사이즈 정사이즈인가요?" 댓글을 작성한다
    Then 응답 상태 코드는 201이다
    And 응답의 data에 "showcaseCommentId" 필드가 존재한다
    When 쇼케이스 댓글 목록을 조회한다
    Then 응답 상태 코드는 200이다

  @showcase
  Scenario: 댓글을 수정한다
    Given 쇼케이스에 "원본 댓글" 댓글이 등록되어 있다
    When 등록된 댓글을 "수정된 댓글"로 수정한다
    Then 응답 상태 코드는 200이다

  @showcase
  Scenario: 댓글을 삭제한다
    Given 쇼케이스에 "삭제할 댓글" 댓글이 등록되어 있다
    When 등록된 댓글을 삭제한다
    Then 응답 상태 코드는 200이다

  @edge-case @showcase
  Scenario: 인증 없이 댓글을 작성하면 401 에러가 발생한다
    When 인증 없이 댓글을 작성한다
    Then 응답 상태 코드는 401이다
