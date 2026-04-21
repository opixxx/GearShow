import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

import 'models.dart';

/// 채팅 실시간(STOMP) 전용 컨트롤러.
///
/// `AppController` 가 세션 · STOMP · 네비게이션 모두를 소유하던 God Object 구조를
/// 분리해, 실시간 메시징 책임만 여기로 집중시킨다. 화면별로 `messages` 브로드캐스트
/// 스트림을 구독해 자신의 채팅방 메시지만 필터링해 사용한다.
class ChatRealtimeController extends ChangeNotifier {
  StompClient? _client;

  /// 수신 메시지를 모든 구독자에게 전달하는 브로드캐스트 스트림.
  final StreamController<ChatMessage> _messageController =
      StreamController<ChatMessage>.broadcast();
  Stream<ChatMessage> get messages => _messageController.stream;

  /// 연결 전에 들어온 구독 요청. 연결 성공 시 일괄 처리한다.
  final Set<int> _pendingSubscriptions = <int>{};

  /// 활성 구독. 동일 방 중복 구독 방지 + dispose 시 일괄 해제용.
  final Map<int, StompUnsubscribe> _active = <int, StompUnsubscribe>{};

  bool get isConnected => _client?.connected ?? false;

  /// STOMP 클라이언트를 연결한다. 이미 연결된 상태면 기존 연결을 정리하고 재연결한다.
  void connect({required String baseUrl, required String accessToken}) {
    disconnect();
    final wsUrl = '$baseUrl/ws';
    _client = StompClient(
      config: StompConfig.sockJS(
        url: wsUrl,
        stompConnectHeaders: {'Authorization': 'Bearer $accessToken'},
        onConnect: (_) => _onConnect(),
        onDisconnect: (_) => notifyListeners(),
        onWebSocketError: (error) => debugPrint('WebSocket 에러: $error'),
      ),
    );
    _client!.activate();
  }

  /// 모든 구독을 해제하고 클라이언트를 비활성화한다.
  void disconnect() {
    for (final unsub in _active.values) {
      unsub(unsubscribeHeaders: {});
    }
    _active.clear();
    _pendingSubscriptions.clear();
    _client?.deactivate();
    _client = null;
  }

  /// 채팅방 토픽을 구독한다.
  ///
  /// 연결 전 호출되어도 `_pendingSubscriptions` 에 큐잉되어 연결 성공 시 자동 재시도된다 —
  /// 로그인 직후 빠른 화면 진입으로 발생하는 race 를 제거한다.
  void subscribeChatRoom(int chatRoomId) {
    if (_active.containsKey(chatRoomId)) return;
    if (_client == null || !_client!.connected) {
      _pendingSubscriptions.add(chatRoomId);
      return;
    }
    _doSubscribe(chatRoomId);
  }

  void unsubscribeChatRoom(int chatRoomId) {
    _active.remove(chatRoomId)?.call(unsubscribeHeaders: {});
    _pendingSubscriptions.remove(chatRoomId);
  }

  /// STOMP 로 메시지를 전송한다. 연결 되어있지 않으면 {@code false} 반환 (호출측이 HTTP fallback).
  bool sendMessage(int chatRoomId, String content, String clientMessageId) {
    if (_client == null || !_client!.connected) return false;
    _client!.send(
      destination: '/app/chat-rooms/$chatRoomId/send',
      body: jsonEncode({
        'messageType': 'TEXT',
        'content': content,
        'clientMessageId': clientMessageId,
      }),
    );
    return true;
  }

  @override
  void dispose() {
    disconnect();
    _messageController.close();
    super.dispose();
  }

  // ── internals ───────────────────────────────────────────────

  void _onConnect() {
    // 연결 성공 시 대기 중 구독 전부 복구.
    final pending = List<int>.from(_pendingSubscriptions);
    _pendingSubscriptions.clear();
    for (final id in pending) {
      _doSubscribe(id);
    }
    notifyListeners();
  }

  void _doSubscribe(int chatRoomId) {
    final unsub = _client!.subscribe(
      destination: '/topic/chat-rooms/$chatRoomId',
      callback: (frame) {
        if (frame.body == null) return;
        final decoded = jsonDecode(frame.body!) as Map<String, dynamic>;
        final payload = decoded['payload'] as Map<String, dynamic>?;
        if (payload == null) return;
        if (_messageController.isClosed) return;
        _messageController.add(ChatMessage.fromStompPayload(payload));
      },
    );
    _active[chatRoomId] = unsub;
  }
}
