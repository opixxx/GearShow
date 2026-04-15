Feature: 채팅 REST MVP
  1:1 채팅방 생성·조회·메시지 송수신·읽음·soft delete 를 REST로 수행한다.

  Background:
    Given 카카오 인가 코드 "valid-code"로 가입한 사용자가 존재한다
    And 축구화 카탈로그 아이템을 등록한다
    And 이미지 1개로 쇼케이스가 등록되어 있다
    And 카카오 인가 코드 "valid-code-buyer"로 가입한 구매자가 존재한다

  @smoke @chat
  Scenario: 구매자가 쇼케이스 채팅방을 새로 생성한다
    When 구매자가 쇼케이스 채팅방 생성을 요청한다
    Then 응답 상태 코드는 201이다
    And 응답의 data에 "chatRoomId" 필드가 존재한다

  @chat
  Scenario: 동일 구매자가 재요청하면 기존 채팅방이 반환된다
    Given 구매자가 쇼케이스 채팅방을 이미 생성했다
    When 구매자가 쇼케이스 채팅방 생성을 요청한다
    Then 응답 상태 코드는 200이다
    And 응답의 data에 "chatRoomId" 필드가 존재한다

  @chat @edge-case
  Scenario: 판매자가 자기 쇼케이스에 채팅방을 만들면 400이다
    When 판매자가 자기 쇼케이스 채팅방 생성을 요청한다
    Then 응답 상태 코드는 400이다
    And 응답의 code는 "CHAT_ROOM_OWN_SHOWCASE"이다

  @chat @edge-case
  Scenario: 인증 없이 채팅방 생성을 요청하면 401이다
    When 인증 없이 쇼케이스 채팅방 생성을 요청한다
    Then 응답 상태 코드는 401이다

  @chat
  Scenario: 본인 메시지를 soft delete 하면 목록에서 플레이스홀더로 조회된다
    Given 구매자가 쇼케이스 채팅방을 이미 생성했다
    And 구매자가 "안녕하세요" 메시지를 전송했다
    When 구매자가 방금 보낸 메시지를 삭제한다
    Then 응답 상태 코드는 200이다
    When 구매자가 채팅방 메시지 목록을 조회한다
    Then 응답 상태 코드는 200이다
    And 메시지 목록 첫 번째 항목의 content는 "삭제된 메시지입니다"이다

  @chat @edge-case
  Scenario: 2,000자를 초과한 메시지는 400이다
    Given 구매자가 쇼케이스 채팅방을 이미 생성했다
    When 구매자가 2001자 메시지를 전송한다
    Then 응답 상태 코드는 400이다
