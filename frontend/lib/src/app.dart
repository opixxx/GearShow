import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

import 'api.dart';
import 'models.dart';
import 'screens.dart';

/// 앱 실행 환경.
enum AppEnvironment {
  /// 개발 환경 — OAuth 없이 개발자 로그인으로 API 테스트 (웹 가능).
  dev,

  /// 운영 환경 — 카카오 SDK 소셜 로그인 (앱 전용).
  prod,
}

class AppController extends ChangeNotifier {
  AppController(this.api, {this.environment = AppEnvironment.prod});

  final GearShowApiClient api;
  final AppEnvironment environment;

  /// 개발 환경 여부.
  bool get isDev => environment == AppEnvironment.dev;

  /// 환경별 기본 API URL.
  ///
  /// 주의: prod URL 의 IP 는 EC2 의 Amazon 할당 public IP 이므로
  /// 인스턴스 재부팅 시 바뀔 수 있다. 장기적으로는 Elastic IP 또는 도메인 연결 필요.
  static const _defaultBaseUrls = {
    AppEnvironment.dev: 'http://localhost:8080',
    AppEnvironment.prod: 'http://54.180.92.87:8080',
  };

  late String baseUrl = _defaultBaseUrls[environment]!;
  AuthSession? session;
  UserProfile? profile;

  StompClient? _stompClient;
  final Map<int, StompUnsubscribe> _stompSubscriptions = {};
  void Function(ChatMessage)? onStompMessage;

  bool get isLoggedIn => session?.accessToken.isNotEmpty ?? false;

  int? get currentUserId => profile?.userId;

  /// 첫 로그인 사용자인지 확인한다 (닉네임이 "사용자_"로 시작하면 신규).
  bool get needsNicknameSetup =>
      profile != null && profile!.nickname.startsWith('사용자_');

  /// 닉네임 중복 여부를 확인한다.
  Future<bool> checkNicknameAvailable(String nickname) async {
    return api.checkNicknameAvailable(baseUrl: baseUrl, nickname: nickname);
  }

  void updateBaseUrl(String value) {
    baseUrl = value.trim().isEmpty ? baseUrl : value.trim();
    notifyListeners();
  }

  /// 개발용 로그인. OAuth 없이 테스트 사용자로 인증한다.
  /// DEV_USER_ID dart-define으로 다른 유저 ID를 지정할 수 있다.
  Future<void> devLogin() async {
    const devUserIdStr = String.fromEnvironment('DEV_USER_ID');
    final devUserId = devUserIdStr.isNotEmpty ? int.tryParse(devUserIdStr) : null;
    final nextSession = await api.devLogin(baseUrl: baseUrl, userId: devUserId);
    session = nextSession;
    profile = await api.getMyProfile(
      baseUrl: baseUrl,
      accessToken: nextSession.accessToken,
    );
    connectStomp();
    notifyListeners();
  }

  Future<void> login({
    required String provider,
    String? authorizationCode,
    String? accessToken,
  }) async {
    final nextSession = await api.login(
      baseUrl: baseUrl,
      provider: provider,
      authorizationCode: authorizationCode,
      accessToken: accessToken,
    );
    session = nextSession;
    profile = await api.getMyProfile(
      baseUrl: baseUrl,
      accessToken: nextSession.accessToken,
    );
    connectStomp();
    notifyListeners();
  }

  Future<void> refreshSession() async {
    final current = session;
    if (current == null) {
      throw const ApiException('로그인 세션이 없습니다.');
    }
    session = await api.refresh(
      baseUrl: baseUrl,
      refreshToken: current.refreshToken,
    );
    notifyListeners();
  }

  Future<void> loadMyProfile() async {
    final token = session?.accessToken;
    if (token == null || token.isEmpty) {
      throw const ApiException('로그인이 필요합니다.');
    }
    profile = await api.getMyProfile(baseUrl: baseUrl, accessToken: token);
    notifyListeners();
  }

  Future<void> saveProfile({
    required String nickname,
    XFile? profileImage,
  }) async {
    final token = session?.accessToken;
    if (token == null || token.isEmpty) {
      throw const ApiException('로그인이 필요합니다.');
    }
    final updated = await api.updateMyProfile(
      baseUrl: baseUrl,
      accessToken: token,
      nickname: nickname.trim().isEmpty ? null : nickname.trim(),
      profileImage: profileImage,
    );
    profile = UserProfile(
      userId: updated.userId,
      nickname: updated.nickname,
      profileImageUrl: updated.profileImageUrl,
      phoneNumber: profile?.phoneNumber,
      isPhoneVerified: profile?.isPhoneVerified ?? false,
      userStatus: profile?.userStatus ?? 'ACTIVE',
    );
    notifyListeners();
  }

  Future<void> logout() async {
    disconnectStomp();
    final token = session?.accessToken;
    if (token != null && token.isNotEmpty) {
      await api.logout(baseUrl: baseUrl, accessToken: token);
    }
    session = null;
    profile = null;
    notifyListeners();
  }

  void connectStomp() {
    final token = session?.accessToken;
    if (token == null || token.isEmpty) return;

    disconnectStomp();
    final wsUrl = '${baseUrl.replaceFirst('http', 'http')}/ws';
    _stompClient = StompClient(
      config: StompConfig.sockJS(
        url: wsUrl,
        stompConnectHeaders: {'Authorization': 'Bearer $token'},
        onConnect: (_) => notifyListeners(),
        onDisconnect: (_) => notifyListeners(),
        onWebSocketError: (error) => debugPrint('WebSocket 에러: $error'),
      ),
    );
    _stompClient!.activate();
  }

  void disconnectStomp() {
    for (final unsub in _stompSubscriptions.values) {
      unsub(unsubscribeHeaders: {});
    }
    _stompSubscriptions.clear();
    _stompClient?.deactivate();
    _stompClient = null;
  }

  bool get isStompConnected => _stompClient?.connected ?? false;

  void subscribeChatRoom(int chatRoomId) {
    if (_stompClient == null || !_stompClient!.connected) return;
    if (_stompSubscriptions.containsKey(chatRoomId)) return;

    final unsub = _stompClient!.subscribe(
      destination: '/topic/chat-rooms/$chatRoomId',
      callback: (frame) {
        if (frame.body == null) return;
        final json = jsonDecode(frame.body!) as Map<String, dynamic>;
        final payload = json['payload'] as Map<String, dynamic>?;
        if (payload != null) {
          onStompMessage?.call(ChatMessage.fromStompPayload(payload));
        }
      },
    );
    _stompSubscriptions[chatRoomId] = unsub;
  }

  void unsubscribeChatRoom(int chatRoomId) {
    _stompSubscriptions.remove(chatRoomId)?.call(unsubscribeHeaders: {});
  }

  void sendStompMessage(int chatRoomId, String content, String clientMessageId) {
    if (_stompClient == null || !_stompClient!.connected) return;
    _stompClient!.send(
      destination: '/app/chat-rooms/$chatRoomId/send',
      body: jsonEncode({
        'messageType': 'TEXT',
        'content': content,
        'clientMessageId': clientMessageId,
      }),
    );
  }
}

class GearShowApp extends StatefulWidget {
  const GearShowApp({super.key, this.environment = AppEnvironment.prod});

  final AppEnvironment environment;

  @override
  State<GearShowApp> createState() => _GearShowAppState();
}

class _GearShowAppState extends State<GearShowApp> {
  late final AppController controller;

  @override
  void initState() {
    super.initState();
    controller = AppController(
      GearShowApiClient(),
      environment: widget.environment,
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        return MaterialApp(
          title: 'GearShow',
          debugShowCheckedModeBanner: false,
          theme: _buildTheme(),
          onGenerateRoute: (settings) => _buildRoute(settings, controller),
          initialRoute: '/',
        );
      },
    );
  }
}

Route<dynamic> _buildRoute(RouteSettings settings, AppController controller) {
  switch (settings.name) {
    case '/':
      return MaterialPageRoute<void>(
        builder: (_) => SplashScreen(controller: controller),
        settings: settings,
      );
    case '/login':
      return MaterialPageRoute<void>(
        builder: (_) => LoginScreen(controller: controller),
        settings: settings,
      );
    case '/nickname':
      return MaterialPageRoute<void>(
        builder: (_) => NicknameSetupScreen(controller: controller),
        settings: settings,
      );
    case '/shell':
      final initialIndex = settings.arguments is int ? settings.arguments as int : 0;
      return MaterialPageRoute<void>(
        builder: (_) => MainShell(
          controller: controller,
          initialIndex: initialIndex,
        ),
        settings: settings,
      );
    case '/catalog/search':
      return MaterialPageRoute<void>(
        builder: (_) => CatalogScreen(controller: controller),
        settings: settings,
      );
    case '/catalog/detail':
      final args = settings.arguments! as CatalogDetailArgs;
      return MaterialPageRoute<void>(
        builder: (_) => CatalogDetailScreen(
          controller: controller,
          args: args,
        ),
        settings: settings,
      );
    case '/showcase/detail':
      final args = settings.arguments! as ShowcaseDetailArgs;
      return MaterialPageRoute<void>(
        builder: (_) => ShowcaseDetailScreen(
          controller: controller,
          args: args,
        ),
        settings: settings,
      );
    case '/showcase/viewer':
      final args = settings.arguments! as Viewer3dArgs;
      return MaterialPageRoute<void>(
        builder: (_) => Viewer3dScreen(args: args),
        settings: settings,
      );
    case '/create/info':
      final args = settings.arguments! as CreateInfoArgs;
      return MaterialPageRoute<void>(
        builder: (_) => ShowcaseCreateInfoScreen(
          controller: controller,
          args: args,
        ),
        settings: settings,
      );
    case '/create/images':
      final args = settings.arguments! as CreateImagesArgs;
      return MaterialPageRoute<void>(
        builder: (_) => ShowcaseCreateImagesScreen(
          controller: controller,
          args: args,
        ),
        settings: settings,
      );
    case '/mypage/profile':
      return MaterialPageRoute<void>(
        builder: (_) => ProfileEditScreen(controller: controller),
        settings: settings,
      );
    case '/mypage/showcases':
      return MaterialPageRoute<void>(
        builder: (_) => MyShowcasesScreen(controller: controller),
        settings: settings,
      );
    case '/chat/room':
      final args = settings.arguments! as ChatRoomArgs;
      return MaterialPageRoute<void>(
        builder: (_) => ChatRoomScreen(controller: controller, args: args),
        settings: settings,
      );
    default:
      return MaterialPageRoute<void>(
        builder: (_) => UnsupportedFeatureScreen(
          title: '화면을 찾을 수 없습니다',
          description: settings.name ?? '',
        ),
        settings: settings,
      );
  }
}

ThemeData _buildTheme() {
  const seed = Color(0xFF12B981);
  final scheme = ColorScheme.fromSeed(
    brightness: Brightness.dark,
    seedColor: seed,
    primary: const Color(0xFF19C37D),
    secondary: const Color(0xFF22D3EE),
    surface: const Color(0xFF111111),
  );

  return ThemeData(
    colorScheme: scheme,
    scaffoldBackgroundColor: Colors.black,
    canvasColor: Colors.black,
    useMaterial3: true,
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.black,
      foregroundColor: Colors.white,
      elevation: 0,
      centerTitle: false,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: const Color(0xFF171717),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: const BorderSide(color: Color(0xFF3F3F46)),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: const BorderSide(color: Color(0xFF3F3F46)),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: const BorderSide(color: Color(0xFF19C37D)),
      ),
    ),
    cardTheme: CardThemeData(
      color: const Color(0xFF111111),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    ),
  );
}
