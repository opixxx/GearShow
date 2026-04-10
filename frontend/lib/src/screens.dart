import 'dart:async';
import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:image_picker/image_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';
import 'package:model_viewer_plus/model_viewer_plus.dart';
import 'package:webview_flutter/webview_flutter.dart';

import 'app.dart';
import 'models.dart';

class CatalogDetailArgs {
  const CatalogDetailArgs(this.item);

  final CatalogItemSummary item;
}

class ShowcaseDetailArgs {
  const ShowcaseDetailArgs(this.showcaseId);

  final int showcaseId;
}

class Viewer3dArgs {
  const Viewer3dArgs({
    required this.title,
    required this.model,
  });

  final String title;
  final ShowcaseModel3d model;
}

class CreateInfoArgs {
  const CreateInfoArgs({this.catalogItem});

  /// 카탈로그에서 선택한 경우 값이 있고, 직접 입력인 경우 null
  final CatalogItemSummary? catalogItem;
}

class CreateImagesArgs {
  const CreateImagesArgs(this.draft);

  final ShowcaseDraft draft;
}

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    Timer(const Duration(seconds: 2), () {
      if (!mounted) {
        return;
      }
      Navigator.of(context).pushReplacementNamed('/login');
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF16A34A), Color(0xFF0F766E), Color(0xFF0891B2)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: const Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('⚽', style: TextStyle(fontSize: 72)),
              SizedBox(height: 12),
              Text(
                'GearShow',
                style: TextStyle(
                  fontSize: 36,
                  fontWeight: FontWeight.w800,
                  color: Colors.white,
                ),
              ),
              SizedBox(height: 8),
              Text(
                '축구 장비 쇼케이스 & 거래',
                style: TextStyle(color: Colors.white70),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  late final TextEditingController _baseUrlController;
  final TextEditingController _codeController = TextEditingController();
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _baseUrlController = TextEditingController(text: widget.controller.baseUrl);
  }

  @override
  void dispose() {
    _baseUrlController.dispose();
    _codeController.dispose();
    super.dispose();
  }

  bool get _isDev => widget.controller.isDev;

  bool get _supportsNativeKakaoLogin =>
      !kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.android ||
          defaultTargetPlatform == TargetPlatform.iOS);

  bool get _kakaoConfigured =>
      const String.fromEnvironment('KAKAO_NATIVE_APP_KEY').isNotEmpty;

  /// 로그인 후 닉네임 설정이 필요하면 닉네임 화면으로, 아니면 홈으로 이동한다.
  void _navigateAfterLogin() {
    if (widget.controller.needsNicknameSetup) {
      Navigator.of(context).pushReplacementNamed('/nickname');
    } else {
      Navigator.of(context).pushReplacementNamed('/shell');
    }
  }

  /// 개발용 로그인. OAuth 없이 테스트 사용자로 인증한다.
  Future<void> _devLogin() async {
    setState(() => _loading = true);
    widget.controller.updateBaseUrl(_baseUrlController.text);
    try {
      await widget.controller.devLogin();
      if (!mounted) return;
      _navigateAfterLogin();
    } on ApiException catch (error) {
      _showSnack(context, error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _login(String provider) async {
    if (_codeController.text.trim().isEmpty) {
      _showSnack(context, '백엔드 로그인 API에 전달할 인가 코드를 입력하세요.');
      return;
    }

    setState(() => _loading = true);
    widget.controller.updateBaseUrl(_baseUrlController.text);
    try {
      await widget.controller.login(
        provider: provider,
        authorizationCode: _codeController.text.trim(),
      );
      if (!mounted) {
        return;
      }
      _navigateAfterLogin();
    } on ApiException catch (error) {
      _showSnack(context, error.message);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _loginWithKakaoSdk() async {
    if (!_supportsNativeKakaoLogin) {
      _showSnack(context, '카카오 SDK 로그인은 Android/iOS에서 테스트할 수 있습니다.');
      return;
    }
    if (!_kakaoConfigured) {
      _showSnack(context, 'KAKAO_NATIVE_APP_KEY가 설정되지 않았습니다.');
      return;
    }

    setState(() => _loading = true);
    widget.controller.updateBaseUrl(_baseUrlController.text);
    try {
      debugPrint('[GearShow] 카카오 SDK 로그인 시작');
      OAuthToken token;
      final installed = await isKakaoTalkInstalled();
      debugPrint('[GearShow] 카카오톡 설치 여부: $installed');
      if (installed) {
        try {
          debugPrint('[GearShow] 카카오톡으로 로그인 시도');
          token = await UserApi.instance.loginWithKakaoTalk();
        } catch (error) {
          debugPrint('[GearShow] 카카오톡 로그인 실패: $error');
          if (error is PlatformException && error.code == 'CANCELED') {
            rethrow;
          }
          debugPrint('[GearShow] 카카오 계정으로 폴백');
          token = await UserApi.instance.loginWithKakaoAccount();
        }
      } else {
        debugPrint('[GearShow] 카카오 계정으로 로그인 시도');
        token = await UserApi.instance.loginWithKakaoAccount();
      }

      debugPrint('[GearShow] 카카오 토큰 획득 완료, 백엔드 로그인 호출');
      await widget.controller.login(
        provider: 'kakao',
        accessToken: token.accessToken,
      );
      debugPrint('[GearShow] 백엔드 로그인 성공');
      if (!mounted) {
        return;
      }
      _navigateAfterLogin();
    } catch (error) {
      debugPrint('[GearShow] 로그인 에러: $error');
      if (!mounted) {
        return;
      }
      _showSnack(context, _errorText(error));
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Colors.black, Color(0xFF18181B)],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: MediaQuery.of(context).size.height - 48,
              ),
              child: Column(
                children: [
                  const SizedBox(height: 24),
                  const Text('⚽', style: TextStyle(fontSize: 56)),
                  const SizedBox(height: 12),
                  const Text(
                    'GearShow',
                    style: TextStyle(
                      fontSize: 34,
                      fontWeight: FontWeight.w800,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    '축구 장비 쇼케이스 & 거래 플랫폼',
                    style: TextStyle(color: Color(0xFFA1A1AA)),
                  ),
                  const SizedBox(height: 48),
                  TextField(
                    controller: _baseUrlController,
                    style: const TextStyle(color: Colors.white),
                    decoration: const InputDecoration(
                      labelText: 'Backend Base URL',
                      hintText: 'http://localhost:8080',
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (_isDev) ...[
                    // ── 개발 환경: 개발자 로그인 ──
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: const Color(0xFF1A1A2E),
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(color: const Color(0xFF3B82F6)),
                      ),
                      child: const Text(
                        'DEV 모드: OAuth 없이 테스트 사용자로 로그인합니다.\n'
                        '백엔드를 --spring.profiles.active=dev 로 실행하세요.',
                        style: TextStyle(color: Color(0xFF93C5FD), fontSize: 12),
                      ),
                    ),
                    const SizedBox(height: 16),
                    _SocialButton(
                      backgroundColor: const Color(0xFF3B82F6),
                      foregroundColor: Colors.white,
                      icon: '',
                      label: '개발자 로그인',
                      loading: _loading,
                      onPressed: _devLogin,
                    ),
                  ] else ...[
                    // ── 운영 환경: 카카오/애플 소셜 로그인 ──
                    TextField(
                      controller: _codeController,
                      style: const TextStyle(color: Colors.white),
                      decoration: const InputDecoration(
                        labelText: 'Apple 테스트용 인가 코드',
                        hintText: '현재 Apple은 인가 코드 수동 입력 방식',
                      ),
                      minLines: 2,
                      maxLines: 3,
                    ),
                    const SizedBox(height: 12),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: const Color(0xFF111111),
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(color: const Color(0xFF27272A)),
                      ),
                      child: const Text(
                        '카카오는 SDK 로그인으로 연결됩니다. Android/iOS에서는 카카오톡 또는 카카오 계정 로그인 후 '
                        'SDK 액세스 토큰을 backend에 전달해 앱 JWT를 발급받습니다.',
                        style: TextStyle(color: Color(0xFFA1A1AA), fontSize: 12),
                      ),
                    ),
                    if (!_supportsNativeKakaoLogin) ...[
                      const SizedBox(height: 10),
                      const Text(
                        '현재 실행 중인 macOS/web 환경에서는 카카오 SDK 자동 로그인을 테스트할 수 없습니다.',
                        style: TextStyle(color: Color(0xFFFB7185), fontSize: 12),
                        textAlign: TextAlign.center,
                      ),
                    ],
                    const SizedBox(height: 16),
                    _SocialButton(
                      backgroundColor: const Color(0xFFFEE500),
                      foregroundColor: Colors.black,
                      icon: '💬',
                      label: '카카오로 시작하기',
                      loading: _loading,
                      onPressed: _loginWithKakaoSdk,
                    ),
                    const SizedBox(height: 12),
                    _SocialButton(
                      backgroundColor: const Color(0xFF09090B),
                      foregroundColor: Colors.white,
                      icon: '',
                      label: 'Apple로 시작하기',
                      loading: _loading,
                      borderColor: const Color(0xFF3F3F46),
                      onPressed: () => _login('apple'),
                    ),
                    const SizedBox(height: 12),
                    const _DisabledSocialButton(label: 'Google은 현재 backend 미지원'),
                  ],
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class MainShell extends StatefulWidget {
  const MainShell({
    super.key,
    required this.controller,
    this.initialIndex = 0,
  });

  final AppController controller;
  final int initialIndex;

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  late int _index;
  late final List<Widget> _screens;

  @override
  void initState() {
    super.initState();
    _index = widget.initialIndex;
    _screens = [
      HomeScreen(controller: widget.controller),
      ShowcaseCreateScreen(controller: widget.controller),
      const UnsupportedFeatureScreen(
        title: '채팅',
        description: '와이어프레임은 유지했지만 현재 backend에는 chat API가 없습니다.',
      ),
      MyPageScreen(controller: widget.controller),
    ];
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _index, children: _screens),
      bottomNavigationBar: NavigationBar(
        backgroundColor: const Color(0xFF111111),
        indicatorColor: const Color(0xFF164E3F),
        selectedIndex: _index,
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: '홈'),
          NavigationDestination(icon: Icon(Icons.add_circle_outline), selectedIcon: Icon(Icons.add_circle), label: '등록'),
          NavigationDestination(icon: Icon(Icons.chat_bubble_outline), selectedIcon: Icon(Icons.chat_bubble), label: '채팅'),
          NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: 'MY'),
        ],
      ),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  String _activeFilter = '전체';

  Future<PageInfo<ShowcaseSummary>> _loadShowcases() {
    String? category;
    bool? isForSale;
    if (_activeFilter == '축구화') {
      category = 'BOOTS';
    } else if (_activeFilter == '유니폼') {
      category = 'UNIFORM';
    } else if (_activeFilter == '판매중') {
      isForSale = true;
    }

    return widget.controller.api.listShowcases(
      baseUrl: widget.controller.baseUrl,
      category: category,
      isForSale: isForSale,
      size: 24,
    );
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: const BoxDecoration(
              color: Color(0xFF111111),
              border: Border(bottom: BorderSide(color: Color(0xFF27272A))),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Text(
                      'GearShow',
                      style: TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.w800,
                        color: Colors.white,
                      ),
                    ),
                    const Spacer(),
                    IconButton(
                      onPressed: () => setState(() {}),
                      icon: const Icon(Icons.refresh, color: Color(0xFFA1A1AA)),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: ['전체', '축구화', '유니폼', '판매중']
                      .map(
                        (filter) => ChoiceChip(
                          label: Text(filter),
                          selected: _activeFilter == filter,
                          onSelected: (_) => setState(() => _activeFilter = filter),
                          selectedColor: const Color(0xFF10B981),
                          backgroundColor: const Color(0xFF27272A),
                          labelStyle: TextStyle(
                            color: _activeFilter == filter ? Colors.white : const Color(0xFFA1A1AA),
                          ),
                        ),
                      )
                      .toList(),
                ),
              ],
            ),
          ),
          Expanded(
            child: FutureBuilder<PageInfo<ShowcaseSummary>>(
              future: _loadShowcases(),
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return _ErrorState(
                    message: _errorText(snapshot.error),
                    onRetry: () => setState(() {}),
                  );
                }
                final items = snapshot.data?.items ?? const <ShowcaseSummary>[];
                if (items.isEmpty) {
                  return const _EmptyState(
                    icon: Icons.sports_soccer,
                    message: '표시할 쇼케이스가 없습니다.',
                  );
                }
                return ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final item = items[index];
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: _ShowcaseCard(
                        item: item,
                        onTap: () async {
                          final deleted = await Navigator.of(context).pushNamed(
                            '/showcase/detail',
                            arguments: ShowcaseDetailArgs(item.showcaseId),
                          );
                          if (deleted == true && mounted) setState(() {});
                        },
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class CatalogScreen extends StatefulWidget {
  const CatalogScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<CatalogScreen> createState() => _CatalogScreenState();
}

class _CatalogScreenState extends State<CatalogScreen> {
  final TextEditingController _searchController = TextEditingController();
  String _category = 'BOOTS';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<PageInfo<CatalogItemSummary>> _loadCatalogs() {
    return widget.controller.api.listCatalogs(
      baseUrl: widget.controller.baseUrl,
      category: _category,
      keyword: _searchController.text.trim().isEmpty ? null : _searchController.text.trim(),
      size: 30,
    );
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: const BoxDecoration(
              color: Color(0xFF111111),
              border: Border(bottom: BorderSide(color: Color(0xFF27272A))),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '카탈로그',
                  style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: Colors.white),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _searchController,
                  style: const TextStyle(color: Colors.white),
                  decoration: InputDecoration(
                    hintText: '브랜드, 모델명 검색',
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: IconButton(
                      onPressed: () => setState(() {}),
                      icon: const Icon(Icons.arrow_forward),
                    ),
                  ),
                  onSubmitted: (_) => setState(() {}),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: _TabButton(
                        label: '축구화',
                        selected: _category == 'BOOTS',
                        onTap: () => setState(() => _category = 'BOOTS'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _TabButton(
                        label: '유니폼',
                        selected: _category == 'UNIFORM',
                        onTap: () => setState(() => _category = 'UNIFORM'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          Expanded(
            child: FutureBuilder<PageInfo<CatalogItemSummary>>(
              future: _loadCatalogs(),
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return _ErrorState(
                    message: _errorText(snapshot.error),
                    onRetry: () => setState(() {}),
                  );
                }
                final items = snapshot.data?.items ?? const <CatalogItemSummary>[];
                if (items.isEmpty) {
                  return const _EmptyState(
                    icon: Icons.search_off,
                    message: '검색 결과가 없습니다.',
                  );
                }
                return ListView.separated(
                  itemCount: items.length,
                  separatorBuilder: (_, _) => const Divider(height: 1, color: Color(0xFF27272A)),
                  itemBuilder: (context, index) {
                    final item = items[index];
                    return ListTile(
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                      leading: _ImageFrame(
                        imageUrl: item.officialImageUrl,
                        size: 64,
                        emoji: item.category == 'BOOTS' ? '🥾' : '👕',
                      ),
                      title: Text(item.brand, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                      subtitle: Text(
                        '${item.brand} · ${item.modelCode}',
                        style: const TextStyle(color: Color(0xFFA1A1AA)),
                      ),
                      onTap: () => Navigator.of(context).pushNamed(
                        '/catalog/detail',
                        arguments: CatalogDetailArgs(item),
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class CatalogDetailScreen extends StatefulWidget {
  const CatalogDetailScreen({
    super.key,
    required this.controller,
    required this.args,
  });

  final AppController controller;
  final CatalogDetailArgs args;

  @override
  State<CatalogDetailScreen> createState() => _CatalogDetailScreenState();
}

class _CatalogDetailScreenState extends State<CatalogDetailScreen> {
  Future<({CatalogItemDetail detail, List<ShowcaseSummary> related})> _load() async {
    final detail = await widget.controller.api.getCatalogDetail(
      baseUrl: widget.controller.baseUrl,
      catalogItemId: widget.args.item.catalogItemId,
    );
    final relatedPage = await widget.controller.api.listShowcases(
      baseUrl: widget.controller.baseUrl,
      keyword: detail.brand,
      size: 6,
    );
    return (detail: detail, related: relatedPage.items);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('카탈로그 상세')),
      body: FutureBuilder<({CatalogItemDetail detail, List<ShowcaseSummary> related})>(
        future: _load(),
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return _ErrorState(
              message: _errorText(snapshot.error),
              onRetry: () => setState(() {}),
            );
          }
          final detail = snapshot.data!.detail;
          final related = snapshot.data!.related;
          final specTiles = _catalogSpecEntries(detail);
          return Column(
            children: [
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                  children: [
                    const SizedBox(height: 8),
                    Center(
                      child: _ImageFrame(
                        imageUrl: detail.officialImageUrl,
                        size: MediaQuery.of(context).size.width - 32,
                        emoji: detail.category == 'BOOTS' ? '🥾' : '👕',
                        borderRadius: 24,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      detail.brand,
                      style: const TextStyle(color: Color(0xFF34D399), fontSize: 13, fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      detail.brand,
                      style: const TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '모델코드: ${detail.modelCode}',
                      style: const TextStyle(color: Color(0xFFA1A1AA)),
                    ),
                    const SizedBox(height: 20),
                    _SectionCard(
                      title: '스펙 정보',
                      child: Wrap(
                        runSpacing: 14,
                        spacing: 20,
                        children: specTiles
                            .map((entry) => SizedBox(
                                  width: (MediaQuery.of(context).size.width - 92) / 2,
                                  child: _LabeledValue(label: entry.$1, value: entry.$2),
                                ))
                            .toList(),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        Text(
                          '이 장비의 쇼케이스 (${related.length})',
                          style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(width: 8),
                        const Expanded(
                          child: Text(
                            'backend에 catalogId 직접 조회 API가 없어 brand 기반으로 매칭했습니다.',
                            style: TextStyle(color: Color(0xFF71717A), fontSize: 11),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    if (related.isEmpty)
                      const _EmptyState(
                        icon: Icons.inventory_2_outlined,
                        message: '연결 가능한 쇼케이스가 아직 없습니다.',
                      )
                    else
                      ...related.map(
                        (showcase) => Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: InkWell(
                            borderRadius: BorderRadius.circular(18),
                            onTap: () async {
                              final result = await Navigator.of(context).pushNamed(
                                '/showcase/detail',
                                arguments: ShowcaseDetailArgs(showcase.showcaseId),
                              );
                              if (result == true && mounted) setState(() {});
                            },
                            child: Ink(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: const Color(0xFF111111),
                                borderRadius: BorderRadius.circular(18),
                                border: Border.all(color: const Color(0xFF27272A)),
                              ),
                              child: Row(
                                children: [
                                  _ImageFrame(
                                    imageUrl: showcase.primaryImageUrl,
                                    size: 72,
                                    emoji: detail.category == 'BOOTS' ? '🥾' : '👕',
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        _GradePill(grade: showcase.conditionGrade),
                                        const SizedBox(height: 6),
                                        Text(
                                          showcase.title,
                                          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              _BottomActionBar(
                label: '이 장비로 쇼케이스 등록하기',
                onPressed: () => Navigator.of(context).pushNamed(
                  '/create/info',
                  arguments: CreateInfoArgs(catalogItem: widget.args.item),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class ShowcaseDetailScreen extends StatefulWidget {
  const ShowcaseDetailScreen({
    super.key,
    required this.controller,
    required this.args,
  });

  final AppController controller;
  final ShowcaseDetailArgs args;

  @override
  State<ShowcaseDetailScreen> createState() => _ShowcaseDetailScreenState();
}

class _ShowcaseDetailScreenState extends State<ShowcaseDetailScreen> {
  final TextEditingController _commentController = TextEditingController();
  final PageController _pageController = PageController();
  int _pageIndex = 0;
  bool _submittingComment = false;
  late Future<({ShowcaseDetail detail, CatalogItemDetail? catalog, List<ShowcaseComment> comments, UserProfile? ownerProfile})> _loadFuture;

  @override
  void initState() {
    super.initState();
    _loadFuture = _load();
  }

  @override
  void dispose() {
    _commentController.dispose();
    _pageController.dispose();
    super.dispose();
  }

  Future<({ShowcaseDetail detail, CatalogItemDetail? catalog, List<ShowcaseComment> comments, UserProfile? ownerProfile})> _load() async {
    final detail = await widget.controller.api.getShowcaseDetail(
      baseUrl: widget.controller.baseUrl,
      showcaseId: widget.args.showcaseId,
    );
    CatalogItemDetail? catalog;
    if (detail.catalogItemId > 0) {
      catalog = await widget.controller.api.getCatalogDetail(
        baseUrl: widget.controller.baseUrl,
        catalogItemId: detail.catalogItemId,
      );
    }
    final comments = await widget.controller.api.listComments(
      baseUrl: widget.controller.baseUrl,
      showcaseId: detail.showcaseId,
      size: 30,
    );
    UserProfile? ownerProfile;
    try {
      ownerProfile = await widget.controller.api.getUserProfile(
        baseUrl: widget.controller.baseUrl,
        userId: detail.ownerId,
      );
    } catch (_) {}
    return (detail: detail, catalog: catalog, comments: comments.items, ownerProfile: ownerProfile);
  }

  Future<void> _submitComment() async {
    final token = widget.controller.session?.accessToken;
    if (token == null || token.isEmpty) {
      _showSnack(context, '댓글 작성은 로그인 후 가능합니다.');
      return;
    }
    if (_commentController.text.trim().isEmpty) {
      _showSnack(context, '댓글 내용을 입력하세요.');
      return;
    }

    setState(() => _submittingComment = true);
    try {
      await widget.controller.api.createComment(
        baseUrl: widget.controller.baseUrl,
        accessToken: token,
        showcaseId: widget.args.showcaseId,
        content: _commentController.text.trim(),
      );
      _commentController.clear();
      if (mounted) {
        setState(() {
          _loadFuture = _load();
        });
      }
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      _showSnack(context, error.message);
    } finally {
      if (mounted) {
        setState(() => _submittingComment = false);
      }
    }
  }

  /// 쇼케이스 수정 바텀시트를 표시한다.
  Future<void> _showEditSheet(ShowcaseDetail detail) async {
    final result = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF1A1A1A),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => _ShowcaseEditSheet(
        controller: widget.controller,
        detail: detail,
      ),
    );
    if (result == true && mounted) {
      setState(() {
        _loadFuture = _load();
      });
    }
  }

  /// 쇼케이스 삭제 확인 다이얼로그를 표시한다.
  Future<void> _confirmDelete() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('쇼케이스 삭제'),
        content: const Text('정말 삭제하시겠습니까?\n삭제된 쇼케이스는 복구할 수 없습니다.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('취소'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('삭제', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      try {
        await widget.controller.api.deleteShowcase(
          baseUrl: widget.controller.baseUrl,
          accessToken: widget.controller.session!.accessToken,
          showcaseId: widget.args.showcaseId,
        );
        if (!mounted) return;
        _showSnack(context, '쇼케이스가 삭제되었습니다.');
        Navigator.of(context).pop(true);
      } on ApiException catch (error) {
        _showSnack(context, error.message);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<({ShowcaseDetail detail, CatalogItemDetail? catalog, List<ShowcaseComment> comments, UserProfile? ownerProfile})>(
      future: _loadFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return Scaffold(appBar: AppBar(), body: const Center(child: CircularProgressIndicator()));
        }
        if (snapshot.hasError) {
          return Scaffold(
            appBar: AppBar(),
            body: _ErrorState(
              message: _errorText(snapshot.error),
              onRetry: () => setState(() {
                _loadFuture = _load();
              }),
            ),
          );
        }
        final detail = snapshot.data!.detail;
        final catalog = snapshot.data!.catalog;
        final comments = snapshot.data!.comments;
        final ownerProfile = snapshot.data!.ownerProfile;
        final images = detail.images;
        final bool isOwner = widget.controller.profile?.userId == detail.ownerId;

        return Scaffold(
          appBar: AppBar(
            actions: isOwner
                ? [
                    PopupMenuButton<String>(
                      icon: const Icon(Icons.more_vert),
                      onSelected: (value) {
                        if (value == 'edit') {
                          _showEditSheet(detail);
                        } else if (value == 'delete') {
                          _confirmDelete();
                        }
                      },
                      itemBuilder: (_) => const [
                        PopupMenuItem(value: 'edit', child: Text('수정')),
                        PopupMenuItem(value: 'delete', child: Text('삭제', style: TextStyle(color: Colors.red))),
                      ],
                    ),
                  ]
                : null,
          ),
          body: Column(
            children: [
              Expanded(
                child: ListView(
                  padding: EdgeInsets.zero,
                  children: [
                    SizedBox(
                      height: 300,
                      child: Stack(
                        children: [
                          PageView.builder(
                            controller: _pageController,
                            itemCount: images.isEmpty ? 1 : images.length,
                            onPageChanged: (value) => setState(() => _pageIndex = value),
                            itemBuilder: (context, index) {
                              final image = images.isEmpty ? null : images[index];
                              return _ImageFrame(
                                imageUrl: image?.imageUrl,
                                size: double.infinity,
                                emoji: (catalog?.category ?? detail.category) == 'BOOTS' ? '🥾' : '👕',
                                borderRadius: 0,
                              );
                            },
                          ),
                          if (detail.model3d != null)
                            Positioned(
                              top: 16,
                              right: 16,
                              child: FilledButton.icon(
                                style: FilledButton.styleFrom(
                                  backgroundColor: const Color(0xFF0EA5E9),
                                  foregroundColor: Colors.white,
                                ),
                                onPressed: () => Navigator.of(context).pushNamed(
                                  '/showcase/viewer',
                                  arguments: Viewer3dArgs(
                                    title: detail.title,
                                    model: detail.model3d!,
                                  ),
                                ),
                                icon: const Icon(Icons.auto_awesome),
                                label: const Text('3D 보기'),
                              ),
                            ),
                          if (images.length > 1)
                            Positioned(
                              bottom: 16,
                              left: 0,
                              right: 0,
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: List.generate(
                                  images.length,
                                  (index) => AnimatedContainer(
                                    duration: const Duration(milliseconds: 180),
                                    width: index == _pageIndex ? 22 : 8,
                                    height: 8,
                                    margin: const EdgeInsets.symmetric(horizontal: 4),
                                    decoration: BoxDecoration(
                                      color: index == _pageIndex ? Colors.white : Colors.white30,
                                      borderRadius: BorderRadius.circular(99),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              CircleAvatar(
                                radius: 20,
                                backgroundColor: const Color(0xFF0F766E),
                                backgroundImage: ownerProfile?.profileImageUrl != null && ownerProfile!.profileImageUrl!.isNotEmpty
                                    ? NetworkImage(ownerProfile.profileImageUrl!)
                                    : null,
                                child: ownerProfile?.profileImageUrl == null || ownerProfile!.profileImageUrl!.isEmpty
                                    ? Text(
                                        _profileAvatar(ownerProfile),
                                        style: const TextStyle(fontSize: 18),
                                      )
                                    : null,
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  ownerProfile?.nickname ?? '판매자 #${detail.ownerId}',
                                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
                                ),
                              ),
                              OutlinedButton(
                                onPressed: () => _showSnack(context, '채팅 API는 현재 backend에 없습니다.'),
                                child: const Text('채팅하기'),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Text(
                            detail.title,
                            style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800),
                          ),
                          const SizedBox(height: 8),
                          Wrap(
                            spacing: 6,
                            children: [
                              _GradePill(grade: detail.conditionGrade),
                              _StatusPill(text: detail.isForSale ? '판매중' : '보관중', color: detail.isForSale ? const Color(0xFFDC2626) : const Color(0xFF52525B)),
                              _StatusPill(text: detail.showcaseStatus, color: const Color(0xFF2563EB)),
                            ],
                          ),
                          const SizedBox(height: 12),
                          _SectionCard(
                            title: '장비 정보',
                            child: Wrap(
                              spacing: 16,
                              runSpacing: 10,
                              children: [
                                SizedBox(width: 100, child: _LabeledValue(label: '카테고리', value: _categoryLabel(detail.category))),
                                SizedBox(width: 100, child: _LabeledValue(label: '브랜드', value: detail.brand)),
                                // 축구화 전용 필드
                                if (detail.category == 'BOOTS') ...[
                                  SizedBox(width: 100, child: _LabeledValue(label: '사일로', value: detail.spec?.siloName ?? '-')),
                                  SizedBox(width: 100, child: _LabeledValue(label: '스터드', value: detail.spec?.studType ?? '-')),
                                ],
                                // 유니폼 전용 필드
                                if (detail.category == 'UNIFORM') ...[
                                  SizedBox(width: 100, child: _LabeledValue(label: '클럽', value: detail.spec?.clubName ?? '-')),
                                  SizedBox(width: 100, child: _LabeledValue(label: '시즌', value: detail.spec?.season ?? '-')),
                                  SizedBox(width: 100, child: _LabeledValue(label: '킷 타입', value: detail.spec?.kitType ?? '-')),
                                ],
                                SizedBox(width: 100, child: _LabeledValue(label: '사이즈', value: detail.userSize?.isNotEmpty == true ? detail.userSize! : '-')),
                                SizedBox(width: 100, child: _LabeledValue(label: '착용횟수', value: '${detail.wearCount}회')),
                              ],
                            ),
                          ),
                          if (detail.description?.isNotEmpty == true) ...[
                            const SizedBox(height: 10),
                            Text(
                              detail.description!,
                              style: const TextStyle(color: Color(0xFFD4D4D8), height: 1.6, fontSize: 14),
                            ),
                          ],
                          const SizedBox(height: 10),
                          _SectionCard(
                            title: '댓글 ${comments.length}',
                            child: Column(
                              children: [
                                for (final comment in comments)
                                  Padding(
                                    padding: const EdgeInsets.only(bottom: 14),
                                    child: Row(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        CircleAvatar(
                                          radius: 16,
                                          backgroundColor: const Color(0xFF27272A),
                                          child: Text(_ownerEmoji(comment.authorId)),
                                        ),
                                        const SizedBox(width: 10),
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment: CrossAxisAlignment.start,
                                            children: [
                                              Text(
                                                '사용자 #${comment.authorId}',
                                                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
                                              ),
                                              const SizedBox(height: 4),
                                              Text(
                                                comment.content,
                                                style: const TextStyle(color: Color(0xFFD4D4D8)),
                                              ),
                                            ],
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                TextField(
                                  controller: _commentController,
                                  style: const TextStyle(color: Colors.white),
                                  decoration: InputDecoration(
                                    hintText: '댓글을 입력하세요...',
                                    suffixIcon: TextButton(
                                      onPressed: _submittingComment ? null : _submitComment,
                                      child: _submittingComment
                                          ? const SizedBox.square(
                                              dimension: 16,
                                              child: CircularProgressIndicator(strokeWidth: 2),
                                            )
                                          : const Text('등록'),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              _BottomActionBar(
                label: detail.isForSale ? '채팅으로 문의하기' : '판매중이 아닌 장비입니다',
                enabled: detail.isForSale,
                onPressed: () => _showSnack(context, '거래/채팅 backend API가 아직 없습니다.'),
              ),
            ],
          ),
        );
      },
    );
  }
}

/// 쇼케이스 수정 바텀시트.
class _ShowcaseEditSheet extends StatefulWidget {
  const _ShowcaseEditSheet({
    required this.controller,
    required this.detail,
  });

  final AppController controller;
  final ShowcaseDetail detail;

  @override
  State<_ShowcaseEditSheet> createState() => _ShowcaseEditSheetState();
}

class _ShowcaseEditSheetState extends State<_ShowcaseEditSheet> {
  late final TextEditingController _titleController;
  late final TextEditingController _descriptionController;
  late final TextEditingController _modelCodeController;
  late List<ShowcaseImage> _images;
  late bool _isForSale;
  bool _saving = false;
  bool _imageChanged = false;

  @override
  void initState() {
    super.initState();
    final d = widget.detail;
    _titleController = TextEditingController(text: d.title);
    _descriptionController = TextEditingController(text: d.description ?? '');
    _modelCodeController = TextEditingController(text: d.modelCode ?? '');
    _images = List.from(d.images);
    _isForSale = d.isForSale;
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    _modelCodeController.dispose();
    super.dispose();
  }

  /// 갤러리에서 이미지를 선택하여 추가한다.
  Future<void> _addImage() async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(source: ImageSource.gallery, imageQuality: 80);
    if (picked == null) return;

    try {
      // 1. Presigned URL 발급
      final presigned = await widget.controller.api.addShowcaseImage(
        baseUrl: widget.controller.baseUrl,
        accessToken: widget.controller.session!.accessToken,
        showcaseId: widget.detail.showcaseId,
        imageFile: picked,
      );
      if (!mounted) return;
      // 상세 다시 로드하여 이미지 목록 갱신
      final detail = await widget.controller.api.getShowcaseDetail(
        baseUrl: widget.controller.baseUrl,
        showcaseId: widget.detail.showcaseId,
      );
      if (!mounted) return;
      setState(() {
        _images = List.from(detail.images);
        _imageChanged = true;
      });
    } on ApiException catch (error) {
      _showSnack(context, error.message);
    }
  }

  /// 이미지를 삭제한다.
  Future<void> _deleteImage(ShowcaseImage image) async {
    if (_images.length <= 1) {
      _showSnack(context, '최소 1개의 이미지가 필요합니다.');
      return;
    }
    try {
      await widget.controller.api.deleteShowcaseImage(
        baseUrl: widget.controller.baseUrl,
        accessToken: widget.controller.session!.accessToken,
        showcaseId: widget.detail.showcaseId,
        imageId: image.showcaseImageId,
      );
      setState(() {
        _images.remove(image);
        _imageChanged = true;
      });
    } on ApiException catch (error) {
      _showSnack(context, error.message);
    }
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      await widget.controller.api.updateShowcase(
        baseUrl: widget.controller.baseUrl,
        accessToken: widget.controller.session!.accessToken,
        showcaseId: widget.detail.showcaseId,
        title: _titleController.text.trim(),
        description: _descriptionController.text.trim(),
        isForSale: _isForSale,
      );
      if (!mounted) return;
      _showSnack(context, '쇼케이스가 수정되었습니다.');
      Navigator.of(context).pop(true);
    } on ApiException catch (error) {
      _showSnack(context, error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 16, right: 16, top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 헤더
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('쇼케이스 수정', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
                IconButton(
                  icon: const Icon(Icons.close, color: Colors.white),
                  onPressed: () => Navigator.of(context).pop(_imageChanged ? true : null),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // 이미지 관리
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('이미지 (${_images.length}장)', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 14)),
                GestureDetector(
                  onTap: _addImage,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      border: Border.all(color: const Color(0xFF19C37D)),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.add_photo_alternate_outlined, size: 16, color: Color(0xFF19C37D)),
                        SizedBox(width: 4),
                        Text('추가', style: TextStyle(color: Color(0xFF19C37D), fontSize: 12, fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: 80,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: _images.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (_, i) {
                  final image = _images[i];
                  return Stack(
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.network(image.imageUrl, width: 80, height: 80, fit: BoxFit.cover),
                      ),
                      if (image.isPrimary)
                        Positioned(left: 4, top: 4, child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                          decoration: BoxDecoration(color: const Color(0xFF19C37D), borderRadius: BorderRadius.circular(4)),
                          child: const Text('대표', style: TextStyle(color: Colors.white, fontSize: 9)),
                        )),
                      Positioned(right: 0, top: 0, child: GestureDetector(
                        onTap: () => _deleteImage(image),
                        child: Container(
                          padding: const EdgeInsets.all(2),
                          decoration: const BoxDecoration(color: Colors.black54, shape: BoxShape.circle),
                          child: const Icon(Icons.close, size: 14, color: Colors.white),
                        ),
                      )),
                    ],
                  );
                },
              ),
            ),
            const SizedBox(height: 16),

            // 제목
            TextField(
              controller: _titleController,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(labelText: '제목'),
              maxLength: 100,
            ),
            const SizedBox(height: 12),

            // 설명
            TextField(
              controller: _descriptionController,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(labelText: '설명'),
              maxLines: 3,
            ),
            const SizedBox(height: 12),

            // 모델 코드
            TextField(
              controller: _modelCodeController,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(labelText: '모델 코드'),
            ),
            const SizedBox(height: 12),

            // 판매 여부
            SwitchListTile(
              title: const Text('판매 중', style: TextStyle(color: Colors.white)),
              value: _isForSale,
              onChanged: (v) => setState(() => _isForSale = v),
              activeColor: const Color(0xFF19C37D),
              contentPadding: EdgeInsets.zero,
            ),
            const SizedBox(height: 12),

            // 저장 버튼
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: _saving ? null : _save,
                child: Text(_saving ? '저장 중...' : '저장하기'),
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}

class Viewer3dScreen extends StatefulWidget {
  const Viewer3dScreen({super.key, required this.args});

  final Viewer3dArgs args;

  @override
  State<Viewer3dScreen> createState() => _Viewer3dScreenState();
}

class _Viewer3dScreenState extends State<Viewer3dScreen> {
  late final WebViewController _webViewController;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    final model = widget.args.model;
    final hasModel = model.modelFileUrl?.isNotEmpty == true
        && model.modelStatus == 'COMPLETED';

    if (hasModel) {
      final modelUrl = model.modelFileUrl!;
      // Google model-viewer 웹 컴포넌트를 직접 HTML로 로드
      final html = '''
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<script type="module" src="https://unpkg.com/@google/model-viewer/dist/model-viewer.min.js"></script>
<style>
  body { margin: 0; padding: 0; background: #0a0a0a; overflow: hidden; }
  model-viewer { width: 100vw; height: 100vh; }
</style>
</head>
<body>
<model-viewer
  src="$modelUrl"
  camera-controls
  auto-rotate
  shadow-intensity="1"
  style="background-color: #0a0a0a;"
></model-viewer>
</body>
</html>
''';

      _webViewController = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setNavigationDelegate(NavigationDelegate(
          onPageFinished: (_) => setState(() => _loading = false),
        ))
        ..loadHtmlString(html);
    }
  }

  @override
  Widget build(BuildContext context) {
    final model = widget.args.model;
    final hasModel = model.modelFileUrl?.isNotEmpty == true
        && model.modelStatus == 'COMPLETED';

    return Scaffold(
      backgroundColor: const Color(0xFF0A0A0A),
      appBar: AppBar(
        title: Text('${widget.args.title} 3D'),
        actions: [
          if (hasModel)
            Padding(
              padding: const EdgeInsets.only(right: 12),
              child: Chip(
                label: const Text('COMPLETED', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700)),
                backgroundColor: const Color(0xFF14532D),
                labelStyle: const TextStyle(color: Color(0xFF4ADE80)),
                side: BorderSide.none,
              ),
            ),
        ],
      ),
      body: hasModel
          ? Stack(
              children: [
                WebViewWidget(controller: _webViewController),
                if (_loading)
                  const Center(child: CircularProgressIndicator()),
              ],
            )
          : Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (model.previewImageUrl?.isNotEmpty == true) ...[
                    ClipRRect(
                      borderRadius: BorderRadius.circular(16),
                      child: Image.network(
                        model.previewImageUrl!,
                        width: 200,
                        height: 200,
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) => const Icon(Icons.view_in_ar, size: 80, color: Color(0xFF475569)),
                      ),
                    ),
                    const SizedBox(height: 24),
                  ] else
                    const Icon(Icons.view_in_ar, size: 80, color: Color(0xFF475569)),
                  const SizedBox(height: 16),
                  Text(
                    _statusText(model.modelStatus),
                    style: const TextStyle(color: Color(0xFFA1A1AA), fontSize: 16),
                  ),
                ],
              ),
            ),
    );
  }

  String _statusText(String status) {
    return switch (status) {
      'REQUESTED' => '3D 모델 생성 요청됨',
      'GENERATING' => '3D 모델 생성 중...',
      'FAILED' => '3D 모델 생성 실패',
      _ => '상태: $status',
    };
  }
}

class ShowcaseCreateScreen extends StatefulWidget {
  const ShowcaseCreateScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<ShowcaseCreateScreen> createState() => _ShowcaseCreateScreenState();
}

class _ShowcaseCreateScreenState extends State<ShowcaseCreateScreen> {
  Future<PageInfo<CatalogItemSummary>> _loadRecent() {
    return widget.controller.api.listCatalogs(
      baseUrl: widget.controller.baseUrl,
      size: 6,
    );
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: FutureBuilder<PageInfo<CatalogItemSummary>>(
        future: _loadRecent(),
        builder: (context, snapshot) {
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const Text(
                '쇼케이스 등록',
                style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: Colors.white),
              ),
              const SizedBox(height: 8),
              const Text(
                '등록할 장비를 선택해주세요',
                style: TextStyle(color: Color(0xFFA1A1AA)),
              ),
              const SizedBox(height: 20),
              InkWell(
                borderRadius: BorderRadius.circular(20),
                onTap: () => Navigator.of(context).pushNamed('/catalog/search'),
                child: Ink(
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    color: const Color(0xFF111111),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: const Color(0xFF3F3F46)),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.search, color: Color(0xFF71717A)),
                      SizedBox(width: 12),
                      Text('카탈로그에서 검색', style: TextStyle(color: Color(0xFFA1A1AA))),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 12),
              InkWell(
                borderRadius: BorderRadius.circular(20),
                onTap: () => Navigator.of(context).pushNamed(
                  '/create/info',
                  arguments: const CreateInfoArgs(),
                ),
                child: Ink(
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    color: const Color(0xFF111111),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: const Color(0xFF19C37D)),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.edit_outlined, color: Color(0xFF19C37D)),
                      SizedBox(width: 12),
                      Text('카탈로그에 없는 제품 직접 입력', style: TextStyle(color: Color(0xFF19C37D))),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                '최근 카탈로그',
                style: TextStyle(color: Color(0xFFA1A1AA), fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 12),
              if (snapshot.connectionState != ConnectionState.done)
                const Padding(
                  padding: EdgeInsets.only(top: 24),
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (snapshot.hasError)
                _ErrorState(
                  message: _errorText(snapshot.error),
                  onRetry: () => setState(() {}),
                )
              else
                ...snapshot.data!.items.map(
                  (item) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(18),
                      onTap: () => Navigator.of(context).pushNamed(
                        '/create/info',
                        arguments: CreateInfoArgs(catalogItem: item),
                      ),
                      child: Ink(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: const Color(0xFF111111),
                          borderRadius: BorderRadius.circular(18),
                        ),
                        child: Row(
                          children: [
                            _ImageFrame(
                              imageUrl: item.officialImageUrl,
                              size: 64,
                              emoji: item.category == 'BOOTS' ? '🥾' : '👕',
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    item.brand,
                                    style: const TextStyle(color: Color(0xFF34D399), fontSize: 12, fontWeight: FontWeight.w700),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(item.brand, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class ShowcaseCreateInfoScreen extends StatefulWidget {
  const ShowcaseCreateInfoScreen({
    super.key,
    required this.controller,
    required this.args,
  });

  final AppController controller;
  final CreateInfoArgs args;

  @override
  State<ShowcaseCreateInfoScreen> createState() => _ShowcaseCreateInfoScreenState();
}

class _ShowcaseCreateInfoScreenState extends State<ShowcaseCreateInfoScreen> {
  final _titleController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _wearCountController = TextEditingController(text: '0');
  final _brandController = TextEditingController();
  String _category = 'BOOTS';
  String _grade = 'S';
  bool _isForSale = false;

  // 축구화 전용
  String _studType = 'FG';
  String _bootsSize = '260';
  String? _bootsBrand;
  String? _bootsSilo;
  String? _releaseYear;
  final _bootsBrandCustomController = TextEditingController();
  final _bootsSiloCustomController = TextEditingController();

  // 유니폼 전용
  String _uniformSize = 'L';
  String? _league;
  String? _clubName;
  String? _season;
  String _kitType = 'HOME';
  final _modelCodeController = TextEditingController();

  static const _kitTypes = ['HOME', 'AWAY', 'THIRD'];
  static const _kitTypeLabels = {'HOME': '홈', 'AWAY': '어웨이', 'THIRD': '써드'};

  static const _studTypes = ['FG', 'SG', 'AG', 'TF', 'MG', 'HG'];
  static const _bootsSizes = [
    '220', '225', '230', '235', '240', '245', '250', '255',
    '260', '265', '270', '275', '280', '285', '290', '295',
    '300', '305', '310',
  ];
  static const _uniformSizes = ['S', 'M', 'L', 'XL', '2XL', '3XL'];

  // 축구화 브랜드
  static const _bootsBrands = [
    'Nike', 'Adidas', 'Puma', 'Mizuno', 'Asics', 'New Balance', '기타',
  ];
  // 브랜드별 사일로/모델 라인
  static const _silosByBrand = <String, List<String>>{
    'Nike': ['Mercurial', 'Tiempo', 'Phantom', '직접 입력'],
    'Adidas': ['Predator', 'X', 'Copa', 'F50', '직접 입력'],
    'Puma': ['Future', 'King', 'Ultra', '직접 입력'],
    'Mizuno': ['Morelia', 'Rebula', '직접 입력'],
    'Asics': ['DS Light', 'Ultrezza', '직접 입력'],
    'New Balance': ['Furon', 'Tekela', '직접 입력'],
  };
  // 출시 연도
  static const _releaseYears = [
    '2026', '2025', '2024', '2023', '2022', '2021', '2020', '직접 입력',
  ];
  final _releaseYearCustomController = TextEditingController();
  // 리그
  static const _leagues = [
    'EPL', 'LaLiga', 'Serie A', 'Bundesliga', 'Ligue 1', 'K리그', '직접 입력',
  ];
  final _leagueCustomController = TextEditingController();
  final _clubCustomController = TextEditingController();
  final _seasonCustomController = TextEditingController();
  // 리그별 클럽
  static const _clubsByLeague = <String, List<String>>{
    'EPL': ['맨체스터 유나이티드', '맨체스터 시티', '리버풀', '아스널', '첼시', '토트넘', '뉴캐슬', '아스톤빌라', '웨스트햄', '브라이턴', '직접 입력'],
    'LaLiga': ['레알 마드리드', '바르셀로나', '아틀레티코 마드리드', '레알 소시에다드', '비야레알', '세비야', '직접 입력'],
    'Serie A': ['유벤투스', 'AC밀란', '인테르', '나폴리', 'AS로마', '라치오', '아탈란타', '직접 입력'],
    'Bundesliga': ['바이에른 뮌헨', '도르트문트', 'RB 라이프치히', '레버쿠젠', '프랑크푸르트', '직접 입력'],
    'Ligue 1': ['PSG', '마르세유', '모나코', '릴', '리옹', '직접 입력'],
    'K리그': ['전북', '울산', '수원', '인천', '서울', '포항', '제주', '대구', '강원', '광주', '대전', '직접 입력'],
    '직접 입력': ['직접 입력'],
  };
  // 시즌
  static const _seasons = [
    '25-26', '24-25', '23-24', '22-23', '21-22', '20-21', '직접 입력',
  ];
  // 유니폼 브랜드
  static const _uniformBrands = [
    'Nike', 'Adidas', 'Puma', 'New Balance', 'Umbro', 'Kappa',
    'Joma', 'Hummel', '직접 입력',
  ];
  String? _uniformBrand;
  final _uniformBrandCustomController = TextEditingController();

  /// 카탈로그 선택 없이 직접 입력 모드인지 여부
  bool get _isManualEntry => widget.args.catalogItem == null;

  /// 현재 선택된 카테고리 (카탈로그 or 직접 입력)
  String get _resolvedCategory =>
      widget.args.catalogItem?.category ?? _category;

  /// 칩 피커 위젯 빌더 (필수 선택 - 항상 하나 선택됨)
  List<Widget> _buildChipPicker(String label, List<String> items, String selected, ValueChanged<String> onSelect) {
    return [
      Text(label, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
      const SizedBox(height: 10),
      SizedBox(
        height: 44,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          itemCount: items.length,
          separatorBuilder: (_, __) => const SizedBox(width: 8),
          itemBuilder: (context, index) {
            final item = items[index];
            final isSelected = selected == item;
            return GestureDetector(
              onTap: () => onSelect(item),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                decoration: BoxDecoration(
                  color: isSelected ? const Color(0xFF19C37D) : const Color(0xFF111111),
                  borderRadius: BorderRadius.circular(22),
                  border: Border.all(color: isSelected ? const Color(0xFF19C37D) : const Color(0xFF3F3F46)),
                ),
                child: Text(item, style: TextStyle(color: isSelected ? Colors.white : const Color(0xFFA1A1AA), fontWeight: FontWeight.w700)),
              ),
            );
          },
        ),
      ),
    ];
  }

  /// 칩 피커 위젯 빌더 (선택 해제 가능)
  List<Widget> _buildOptionalChipPicker(String label, List<String> items, String? selected, ValueChanged<String?> onSelect) {
    return [
      Text(label, style: TextStyle(color: selected != null ? Colors.white : Colors.white70, fontWeight: FontWeight.w700)),
      const SizedBox(height: 10),
      SizedBox(
        height: 44,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          itemCount: items.length,
          separatorBuilder: (_, __) => const SizedBox(width: 8),
          itemBuilder: (context, index) {
            final item = items[index];
            final isSelected = selected == item;
            return GestureDetector(
              onTap: () => onSelect(isSelected ? null : item),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                decoration: BoxDecoration(
                  color: isSelected ? const Color(0xFF19C37D) : const Color(0xFF111111),
                  borderRadius: BorderRadius.circular(22),
                  border: Border.all(color: isSelected ? const Color(0xFF19C37D) : const Color(0xFF3F3F46)),
                ),
                child: Text(item, style: TextStyle(color: isSelected ? Colors.white : const Color(0xFFA1A1AA), fontWeight: FontWeight.w700)),
              ),
            );
          },
        ),
      ),
    ];
  }

  /// 현재 사이즈 값
  String get _resolvedSize =>
      _resolvedCategory == 'BOOTS' ? '${_bootsSize}mm' : _uniformSize;

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    _wearCountController.dispose();
    _brandController.dispose();
    _modelCodeController.dispose();
    _bootsBrandCustomController.dispose();
    _bootsSiloCustomController.dispose();
    _releaseYearCustomController.dispose();
    _leagueCustomController.dispose();
    _clubCustomController.dispose();
    _seasonCustomController.dispose();
    _uniformBrandCustomController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.args.catalogItem;
    return Scaffold(
      appBar: AppBar(title: Text(_isManualEntry ? '직접 입력' : '쇼케이스 정보')),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                // 카탈로그 선택된 경우: 제품 정보 표시
                if (item != null)
                  Ink(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: const Color(0xFF111111),
                      borderRadius: BorderRadius.circular(18),
                    ),
                    child: Row(
                      children: [
                        _ImageFrame(
                          imageUrl: item.officialImageUrl,
                          size: 54,
                          emoji: item.category == 'BOOTS' ? '🥾' : '👕',
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(item.brand, style: const TextStyle(color: Color(0xFF34D399), fontSize: 12)),
                              Text(item.brand, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                // 직접 입력인 경우: 카테고리 선택
                if (_isManualEntry) ...[
                  DropdownButtonFormField<String>(
                    value: _category,
                    dropdownColor: const Color(0xFF111111),
                    style: const TextStyle(color: Colors.white),
                    decoration: const InputDecoration(labelText: '카테고리'),
                    items: const [
                      DropdownMenuItem(value: 'BOOTS', child: Text('축구화')),
                      DropdownMenuItem(value: 'UNIFORM', child: Text('유니폼')),
                    ],
                    onChanged: (v) => setState(() {
                      _category = v ?? 'BOOTS';
                      // 축구화 초기화
                      _bootsBrand = null;
                      _bootsSilo = null;
                      _studType = 'FG';
                      _bootsSize = '260';
                      _releaseYear = null;
                      _bootsBrandCustomController.clear();
                      _bootsSiloCustomController.clear();
                      // 유니폼 초기화
                      _league = null;
                      _clubName = null;
                      _season = null;
                      _uniformBrand = null;
                      _uniformSize = 'L';
                      _kitType = 'HOME';
                      _uniformBrandCustomController.clear();
                      _leagueCustomController.clear();
                      _clubCustomController.clear();
                      _seasonCustomController.clear();
                    }),
                  ),
                  const SizedBox(height: 16),
                  // 축구화: 브랜드→사일로 계층 선택
                  if (_resolvedCategory == 'BOOTS') ...[
                    const Text('브랜드', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 10),
                    SizedBox(
                      height: 44,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: _bootsBrands.length,
                        separatorBuilder: (_, __) => const SizedBox(width: 8),
                        itemBuilder: (context, index) {
                          final brand = _bootsBrands[index];
                          final selected = _bootsBrand == brand;
                          return GestureDetector(
                            onTap: () => setState(() {
                              _bootsBrand = brand;
                              _bootsSilo = null;
                              _bootsBrandCustomController.clear();
                              _bootsSiloCustomController.clear();
                            }),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                              decoration: BoxDecoration(
                                color: selected ? const Color(0xFF19C37D) : const Color(0xFF111111),
                                borderRadius: BorderRadius.circular(22),
                                border: Border.all(color: selected ? const Color(0xFF19C37D) : const Color(0xFF3F3F46)),
                              ),
                              child: Text(brand, style: TextStyle(color: selected ? Colors.white : const Color(0xFFA1A1AA), fontWeight: FontWeight.w700)),
                            ),
                          );
                        },
                      ),
                    ),
                    // 기타 브랜드: 직접 입력
                    if (_bootsBrand == '기타') ...[
                      const SizedBox(height: 12),
                      TextField(
                        controller: _bootsBrandCustomController,
                        style: const TextStyle(color: Colors.white),
                        decoration: const InputDecoration(labelText: '브랜드명 직접 입력'),
                      ),
                    ],
                    // 사일로/모델 라인 (브랜드 선택 후 표시)
                    if (_bootsBrand != null && _bootsBrand != '기타' && _silosByBrand.containsKey(_bootsBrand)) ...[
                      const SizedBox(height: 16),
                      const Text('사일로', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                      const SizedBox(height: 10),
                      SizedBox(
                        height: 44,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          itemCount: _silosByBrand[_bootsBrand]!.length,
                          separatorBuilder: (_, __) => const SizedBox(width: 8),
                          itemBuilder: (context, index) {
                            final silo = _silosByBrand[_bootsBrand]![index];
                            final selected = _bootsSilo == silo;
                            return GestureDetector(
                              onTap: () => setState(() {
                                _bootsSilo = silo;
                                _bootsSiloCustomController.clear();
                              }),
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                                decoration: BoxDecoration(
                                  color: selected ? const Color(0xFF19C37D) : const Color(0xFF111111),
                                  borderRadius: BorderRadius.circular(22),
                                  border: Border.all(color: selected ? const Color(0xFF19C37D) : const Color(0xFF3F3F46)),
                                ),
                                child: Text(silo, style: TextStyle(color: selected ? Colors.white : const Color(0xFFA1A1AA), fontWeight: FontWeight.w700)),
                              ),
                            );
                          },
                        ),
                      ),
                    ],
                    // 직접 입력 모델명 (기타 브랜드 or 직접 입력 사일로)
                    if (_bootsBrand == '기타' || _bootsSilo == '직접 입력') ...[
                      const SizedBox(height: 12),
                      TextField(
                        controller: _bootsSiloCustomController,
                        style: const TextStyle(color: Colors.white),
                        decoration: const InputDecoration(labelText: '모델명 직접 입력'),
                      ),
                    ],
                  ],
                  // 유니폼: 브랜드 칩 선택
                  if (_resolvedCategory == 'UNIFORM') ...[
                    const Text('브랜드', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 10),
                    SizedBox(
                      height: 44,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: _uniformBrands.length,
                        separatorBuilder: (_, __) => const SizedBox(width: 8),
                        itemBuilder: (context, index) {
                          final brand = _uniformBrands[index];
                          final selected = _uniformBrand == brand;
                          return GestureDetector(
                            onTap: () => setState(() {
                              _uniformBrand = brand;
                              _uniformBrandCustomController.clear();
                            }),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                              decoration: BoxDecoration(
                                color: selected ? const Color(0xFF19C37D) : const Color(0xFF111111),
                                borderRadius: BorderRadius.circular(22),
                                border: Border.all(color: selected ? const Color(0xFF19C37D) : const Color(0xFF3F3F46)),
                              ),
                              child: Text(brand, style: TextStyle(color: selected ? Colors.white : const Color(0xFFA1A1AA), fontWeight: FontWeight.w700)),
                            ),
                          );
                        },
                      ),
                    ),
                    if (_uniformBrand == '직접 입력') ...[
                      const SizedBox(height: 12),
                      TextField(
                        controller: _uniformBrandCustomController,
                        style: const TextStyle(color: Colors.white),
                        decoration: const InputDecoration(labelText: '브랜드명 직접 입력'),
                      ),
                    ],
                    const SizedBox(height: 16),
                    const Text('킷 타입', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 10),
                    SizedBox(
                      height: 44,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: _kitTypes.length,
                        separatorBuilder: (_, __) => const SizedBox(width: 8),
                        itemBuilder: (context, index) {
                          final kit = _kitTypes[index];
                          final selected = _kitType == kit;
                          return GestureDetector(
                            onTap: () => setState(() => _kitType = kit),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                              decoration: BoxDecoration(
                                color: selected ? const Color(0xFF19C37D) : const Color(0xFF111111),
                                borderRadius: BorderRadius.circular(22),
                                border: Border.all(color: selected ? const Color(0xFF19C37D) : const Color(0xFF3F3F46)),
                              ),
                              child: Text(_kitTypeLabels[kit] ?? kit, style: TextStyle(color: selected ? Colors.white : const Color(0xFFA1A1AA), fontWeight: FontWeight.w700)),
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ],
                const SizedBox(height: 20),
                // ── 축구화 SPEC ──
                if (_resolvedCategory == 'BOOTS') ...[
                  ..._buildChipPicker('스터드 타입', _studTypes, _studType, (v) => setState(() => _studType = v)),
                  const SizedBox(height: 20),
                  ..._buildChipPicker('사이즈 (mm)', _bootsSizes, _bootsSize, (v) => setState(() => _bootsSize = v)),
                  const SizedBox(height: 20),
                  ..._buildOptionalChipPicker('출시 연도', _releaseYears, _releaseYear, (v) => setState(() { _releaseYear = v; _releaseYearCustomController.clear(); })),
                  if (_releaseYear == '직접 입력') ...[
                    const SizedBox(height: 12),
                    TextField(controller: _releaseYearCustomController, style: const TextStyle(color: Colors.white), decoration: const InputDecoration(labelText: '출시 연도 직접 입력 (예: 2019)')),
                  ],
                ],
                // ── 유니폼 SPEC ──
                if (_resolvedCategory == 'UNIFORM') ...[
                  ..._buildOptionalChipPicker('리그', _leagues, _league, (v) => setState(() { _league = v; _clubName = null; _leagueCustomController.clear(); _clubCustomController.clear(); })),
                  if (_league == '직접 입력') ...[
                    const SizedBox(height: 12),
                    TextField(controller: _leagueCustomController, style: const TextStyle(color: Colors.white), decoration: const InputDecoration(labelText: '리그명 직접 입력')),
                  ],
                  if (_league != null) ...[
                    const SizedBox(height: 20),
                    ..._buildOptionalChipPicker('클럽 (필수)', _clubsByLeague[_league] ?? [], _clubName, (v) => setState(() { _clubName = v; _clubCustomController.clear(); })),
                    if (_clubName == '직접 입력') ...[
                      const SizedBox(height: 12),
                      TextField(controller: _clubCustomController, style: const TextStyle(color: Colors.white), decoration: const InputDecoration(labelText: '클럽명 직접 입력')),
                    ],
                  ],
                  const SizedBox(height: 20),
                  ..._buildOptionalChipPicker('시즌 (필수)', _seasons, _season, (v) => setState(() { _season = v; _seasonCustomController.clear(); })),
                  if (_season == '직접 입력') ...[
                    const SizedBox(height: 12),
                    TextField(controller: _seasonCustomController, style: const TextStyle(color: Colors.white), decoration: const InputDecoration(labelText: '시즌 직접 입력 (예: 19-20)')),
                  ],
                  const SizedBox(height: 20),
                  ..._buildChipPicker('사이즈', _uniformSizes, _uniformSize, (v) => setState(() => _uniformSize = v)),
                ],
                const SizedBox(height: 20),
                if (_isManualEntry)
                  TextField(
                    controller: _modelCodeController,
                    style: const TextStyle(color: Colors.white),
                    decoration: const InputDecoration(labelText: '모델 코드 (선택, 예: DJ4978-001)'),
                  ),
                if (_isManualEntry) const SizedBox(height: 16),
                TextField(
                  controller: _titleController,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(labelText: '제목'),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _descriptionController,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(labelText: '상세 설명'),
                  maxLines: 4,
                ),
                const SizedBox(height: 20),
                const Text(
                  '상태 등급',
                  style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 10),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: ['S', 'A', 'B', 'C']
                        .map(
                          (grade) => InkWell(
                            onTap: () => setState(() => _grade = grade),
                            borderRadius: BorderRadius.circular(18),
                            child: Ink(
                              width: (MediaQuery.of(context).size.width - 32 - 8 - 12) / 2,
                              padding: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: _grade == grade ? _gradeColor(grade) : const Color(0xFF111111),
                                borderRadius: BorderRadius.circular(18),
                                border: Border.all(
                                  color: _grade == grade ? Colors.white30 : const Color(0xFF3F3F46),
                                ),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '$grade급',
                                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _gradeDescription(grade),
                                    style: const TextStyle(color: Colors.white70, fontSize: 12),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        )
                        .toList(),
                  ),
                ),
                const SizedBox(height: 20),
                TextField(
                  controller: _wearCountController,
                  style: const TextStyle(color: Colors.white),
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: '착용 횟수'),
                ),
                const SizedBox(height: 20),
                SwitchListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 4),
                  value: _isForSale,
                  onChanged: (value) => setState(() => _isForSale = value),
                  activeThumbColor: const Color(0xFF10B981),
                  title: const Text('판매 여부', style: TextStyle(color: Colors.white)),
                  subtitle: const Text('거래 기능 활성화', style: TextStyle(color: Color(0xFFA1A1AA))),
                ),
              ],
            ),
          ),
          _BottomActionBar(
            label: '다음',
            onPressed: () {
              if (_titleController.text.trim().isEmpty) {
                _showSnack(context, '제목은 필수입니다.');
                return;
              }
              // 직접 입력 검증 - 축구화
              if (_isManualEntry && _resolvedCategory == 'BOOTS') {
                if (_bootsBrand == null) {
                  _showSnack(context, '브랜드를 선택해주세요.');
                  return;
                }
                if (_bootsBrand == '기타' && _bootsBrandCustomController.text.trim().isEmpty) {
                  _showSnack(context, '브랜드명을 입력해주세요.');
                  return;
                }
                final needsSilo = _bootsBrand != '기타' && _silosByBrand.containsKey(_bootsBrand);
                if (needsSilo && _bootsSilo == null) {
                  _showSnack(context, '사일로를 선택해주세요.');
                  return;
                }
                if ((_bootsBrand == '기타' || _bootsSilo == '직접 입력') && _bootsSiloCustomController.text.trim().isEmpty) {
                  _showSnack(context, '모델명을 입력해주세요.');
                  return;
                }
              }
              // 직접 입력 검증 - 유니폼
              if (_isManualEntry && _resolvedCategory == 'UNIFORM') {
                if (_uniformBrand == null) {
                  _showSnack(context, '브랜드를 선택해주세요.');
                  return;
                }
                if (_uniformBrand == '직접 입력' && _uniformBrandCustomController.text.trim().isEmpty) {
                  _showSnack(context, '브랜드명을 입력해주세요.');
                  return;
                }
                if (_clubName == null || _clubName!.isEmpty) {
                  _showSnack(context, '클럽을 선택해주세요.');
                  return;
                }
                if (_clubName == '직접 입력' && _clubCustomController.text.trim().isEmpty) {
                  _showSnack(context, '클럽명을 입력해주세요.');
                  return;
                }
                if (_season == null || _season!.isEmpty) {
                  _showSnack(context, '시즌을 선택해주세요.');
                  return;
                }
                if (_season == '직접 입력' && _seasonCustomController.text.trim().isEmpty) {
                  _showSnack(context, '시즌을 입력해주세요.');
                  return;
                }
              }
              // 직접 입력인 경우 카탈로그 생성
              String resolvedBrand;
              if (_isManualEntry && _resolvedCategory == 'BOOTS') {
                resolvedBrand = _bootsBrand == '기타'
                    ? _bootsBrandCustomController.text.trim()
                    : _bootsBrand!;
              } else if (_isManualEntry && _resolvedCategory == 'UNIFORM') {
                resolvedBrand = _uniformBrand == '직접 입력'
                    ? _uniformBrandCustomController.text.trim()
                    : _uniformBrand!;
              } else {
                resolvedBrand = _brandController.text.trim();
              }
              final resolvedItem = item ?? CatalogItemSummary(
                catalogItemId: 0,
                category: _category,
                brand: resolvedBrand,
                modelCode: _modelCodeController.text.trim(),
                officialImageUrl: null,
              );
              // 사일로 resolve
              String? resolvedSilo;
              if (_resolvedCategory == 'BOOTS' && _bootsSilo != null) {
                resolvedSilo = _bootsSilo == '직접 입력' ? _bootsSiloCustomController.text.trim() : _bootsSilo;
              }
              // 출시 연도 resolve
              String? resolvedYear;
              if (_releaseYear != null) {
                resolvedYear = _releaseYear == '직접 입력' ? _releaseYearCustomController.text.trim() : _releaseYear;
              }
              // 클럽 resolve
              String? resolvedClub;
              if (_resolvedCategory == 'UNIFORM' && _clubName != null) {
                resolvedClub = _clubName == '직접 입력' ? _clubCustomController.text.trim() : _clubName;
              }
              // 시즌 resolve
              String? resolvedSeason;
              if (_resolvedCategory == 'UNIFORM' && _season != null) {
                resolvedSeason = _season == '직접 입력' ? _seasonCustomController.text.trim() : _season;
              }
              // 리그 resolve
              String? resolvedLeague;
              if (_resolvedCategory == 'UNIFORM' && _league != null) {
                resolvedLeague = _league == '직접 입력' ? _leagueCustomController.text.trim() : _league;
              }
              final draft = ShowcaseDraft(
                catalogItem: resolvedItem,
                title: _titleController.text.trim(),
                description: _descriptionController.text.trim(),
                userSize: _resolvedSize,
                conditionGrade: _grade,
                wearCount: int.tryParse(_wearCountController.text.trim()) ?? 0,
                isForSale: _isForSale,
                studType: _resolvedCategory == 'BOOTS' ? _studType : null,
                siloName: resolvedSilo,
                releaseYear: resolvedYear,
                clubName: resolvedClub,
                season: resolvedSeason,
                league: resolvedLeague,
                kitType: _resolvedCategory == 'UNIFORM' ? _kitType : null,
              );
              Navigator.of(context).pushNamed(
                '/create/images',
                arguments: CreateImagesArgs(draft),
              );
            },
          ),
        ],
      ),
    );
  }
}

class ShowcaseCreateImagesScreen extends StatefulWidget {
  const ShowcaseCreateImagesScreen({
    super.key,
    required this.controller,
    required this.args,
  });

  final AppController controller;
  final CreateImagesArgs args;

  @override
  State<ShowcaseCreateImagesScreen> createState() => _ShowcaseCreateImagesScreenState();
}

class _ShowcaseCreateImagesScreenState extends State<ShowcaseCreateImagesScreen> {
  final List<XFile> _images = [];
  final Map<String, XFile?> _modelImages = {
    'front': null,
    'back': null,
    'left': null,
    'right': null,
  };
  int _primaryIndex = 0;
  bool _enable3d = false;
  bool _submitting = false;

  final ImagePicker _picker = ImagePicker();

  Future<void> _pickImages() async {
    final files = await _picker.pickMultiImage(limit: 6);
    if (files.isEmpty) {
      return;
    }
    setState(() {
      _images
        ..clear()
        ..addAll(files.take(6));
      if (_primaryIndex >= _images.length) {
        _primaryIndex = 0;
      }
    });
  }

  Future<void> _pickModelImage(String key) async {
    final file = await _picker.pickImage(source: ImageSource.gallery);
    if (file == null) {
      return;
    }
    setState(() => _modelImages[key] = file);
  }

  Future<void> _submit() async {
    final token = widget.controller.session?.accessToken;
    if (token == null || token.isEmpty) {
      _showSnack(context, '쇼케이스 등록은 로그인 후 가능합니다.');
      return;
    }
    if (_images.isEmpty) {
      _showSnack(context, '일반 이미지를 1장 이상 선택하세요.');
      return;
    }
    if (_enable3d && _modelImages.values.any((file) => file == null)) {
      _showSnack(context, '3D 생성을 켰다면 앞/뒤/좌/우 이미지를 모두 선택해야 합니다.');
      return;
    }

    setState(() => _submitting = true);
    try {
      final draft = widget.args.draft;
      final catalogItem = draft.catalogItem;
      final payload = CreateShowcasePayload(
        catalogItemId: catalogItem.catalogItemId > 0 ? catalogItem.catalogItemId : null,
        category: catalogItem.category,
        brand: catalogItem.brand,
        modelCode: catalogItem.modelCode.isNotEmpty ? catalogItem.modelCode : null,
        title: draft.title,
        description: draft.description,
        userSize: draft.userSize,
        conditionGrade: draft.conditionGrade,
        wearCount: draft.wearCount,
        isForSale: draft.isForSale,
        primaryImageIndex: _primaryIndex,
        images: List<XFile>.from(_images),
        modelSourceImages: _enable3d ? _modelImages.values.whereType<XFile>().toList() : const [],
        studType: draft.studType,
        siloName: draft.siloName,
        releaseYear: draft.releaseYear,
        surfaceType: draft.surfaceType,
        clubName: draft.clubName,
        season: draft.season,
        league: draft.league,
        kitType: draft.kitType,
      );
      final showcaseId = await widget.controller.api.createShowcase(
        baseUrl: widget.controller.baseUrl,
        accessToken: token,
        payload: payload,
      );
      if (!mounted) {
        return;
      }
      _showSnack(context, '쇼케이스 #$showcaseId 등록이 완료되었습니다.');
      Navigator.of(context).pushNamedAndRemoveUntil('/shell', (route) => false);
    } on ApiException catch (error) {
      _showSnack(context, error.message);
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final draft = widget.args.draft;
    final directions = const {
      'front': '앞',
      'back': '뒤',
      'left': '좌',
      'right': '우',
    };

    return Scaffold(
      appBar: AppBar(title: const Text('이미지 업로드')),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  draft.title,
                  style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 16),
                _SectionCard(
                  title: '일반 이미지',
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      FilledButton.icon(
                        onPressed: _pickImages,
                        icon: const Icon(Icons.upload_file),
                        label: const Text('이미지 선택'),
                      ),
                      const SizedBox(height: 12),
                      if (_images.isEmpty)
                        const Text(
                          '최대 6장까지 선택할 수 있습니다.',
                          style: TextStyle(color: Color(0xFFA1A1AA)),
                        )
                      else
                        GridView.builder(
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          itemCount: _images.length,
                          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 3,
                            mainAxisSpacing: 10,
                            crossAxisSpacing: 10,
                          ),
                          itemBuilder: (context, index) {
                            final image = _images[index];
                            final primary = _primaryIndex == index;
                            return InkWell(
                              onTap: () => setState(() => _primaryIndex = index),
                              borderRadius: BorderRadius.circular(18),
                              child: Ink(
                                decoration: BoxDecoration(
                                  color: primary ? const Color(0xFF10B981) : const Color(0xFF111111),
                                  borderRadius: BorderRadius.circular(18),
                                  border: Border.all(color: primary ? Colors.white30 : const Color(0xFF3F3F46)),
                                ),
                                child: Stack(
                                  children: [
                                    Center(
                                      child: Padding(
                                        padding: const EdgeInsets.all(10),
                                        child: Text(
                                          image.name,
                                          maxLines: 4,
                                          overflow: TextOverflow.ellipsis,
                                          textAlign: TextAlign.center,
                                          style: const TextStyle(color: Colors.white, fontSize: 12),
                                        ),
                                      ),
                                    ),
                                    Positioned(
                                      top: 8,
                                      left: 8,
                                      child: Icon(
                                        primary ? Icons.star : Icons.star_border,
                                        size: 18,
                                        color: Colors.white,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SwitchListTile(
                  value: _enable3d,
                  onChanged: (value) => setState(() => _enable3d = value),
                  activeThumbColor: const Color(0xFF22D3EE),
                  title: const Text('3D 모델 생성', style: TextStyle(color: Colors.white)),
                  subtitle: const Text('앞/뒤/좌/우 4방향 사진 업로드', style: TextStyle(color: Color(0xFFA1A1AA))),
                ),
                if (_enable3d) ...[
                  const SizedBox(height: 12),
                  _SectionCard(
                    title: '3D 소스 이미지',
                    child: Column(
                      children: directions.entries
                          .map(
                            (entry) => Padding(
                              padding: const EdgeInsets.only(bottom: 10),
                              child: Row(
                                children: [
                                  SizedBox(
                                    width: 48,
                                    child: Text(entry.value, style: const TextStyle(color: Color(0xFFA1A1AA))),
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: OutlinedButton(
                                      onPressed: () => _pickModelImage(entry.key),
                                      child: Text(
                                        _modelImages[entry.key]?.name ?? '이미지 선택',
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          )
                          .toList(),
                    ),
                  ),
                ],
              ],
            ),
          ),
          _BottomActionBar(
            label: _submitting ? '등록 중...' : '등록 완료',
            enabled: !_submitting,
            onPressed: _submit,
          ),
        ],
      ),
    );
  }
}

class MyPageScreen extends StatefulWidget {
  const MyPageScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<MyPageScreen> createState() => _MyPageScreenState();
}

class _MyPageScreenState extends State<MyPageScreen> {
  late Future<UserProfile?> _future;

  @override
  void initState() {
    super.initState();
    _future = _loadProfile();
  }

  Future<UserProfile?> _loadProfile() async {
    if (!widget.controller.isLoggedIn) {
      return null;
    }
    await widget.controller.loadMyProfile();
    return widget.controller.profile;
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: FutureBuilder<UserProfile?>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return _ErrorState(
              message: _errorText(snapshot.error),
              onRetry: () {
                setState(() {
                  _future = _loadProfile();
                });
              },
            );
          }
          final profile = snapshot.data;
          if (profile == null) {
            return const UnsupportedFeatureScreen(
              title: '로그인이 필요합니다',
              description: '마이페이지와 등록 API는 인증이 필요한 backend endpoint를 사용합니다.',
            );
          }

          return ListView(
            children: [
              Container(
                padding: const EdgeInsets.all(20),
                decoration: const BoxDecoration(
                  color: Color(0xFF111111),
                  border: Border(bottom: BorderSide(color: Color(0xFF27272A))),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '마이페이지',
                      style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        CircleAvatar(
                          radius: 38,
                          backgroundColor: const Color(0xFF0F766E),
                          backgroundImage: profile.profileImageUrl != null && profile.profileImageUrl!.isNotEmpty
                              ? NetworkImage(profile.profileImageUrl!)
                              : null,
                          child: profile.profileImageUrl == null || profile.profileImageUrl!.isEmpty
                              ? Text(
                                  _profileAvatar(profile),
                                  style: const TextStyle(fontSize: 34),
                                )
                              : null,
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                profile.nickname,
                                style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                profile.phoneNumber?.isNotEmpty == true ? profile.phoneNumber! : '전화번호 미등록',
                                style: const TextStyle(color: Color(0xFFA1A1AA)),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: () async {
                        await Navigator.of(context).pushNamed('/mypage/profile');
                        if (mounted) {
                          setState(() {
                            _future = _loadProfile();
                          });
                        }
                      },
                      child: const Text('프로필 수정'),
                    ),
                  ],
                ),
              ),
              _MenuTile(
                icon: Icons.inventory_2_outlined,
                label: '내 쇼케이스',
                onTap: () => Navigator.of(context).pushNamed('/mypage/showcases'),
              ),
              _MenuTile(
                icon: Icons.phone_iphone,
                label: '휴대폰 인증',
                badge: profile.isPhoneVerified ? '완료' : '미구현',
                onTap: () => _showSnack(context, '문서에는 있지만 현재 backend 구현에는 phone verification endpoint가 없습니다.'),
              ),
              _MenuTile(
                icon: Icons.receipt_long_outlined,
                label: '거래 내역',
                badge: '미구현',
                onTap: () => _showSnack(context, 'trade/payment backend API가 없습니다.'),
              ),
              _MenuTile(
                icon: Icons.settings_outlined,
                label: 'Backend Base URL',
                trailingText: widget.controller.baseUrl,
                onTap: () => _showBaseUrlDialog(context, widget.controller),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: OutlinedButton.icon(
                  onPressed: () async {
                    final navigator = Navigator.of(context);
                    final messenger = ScaffoldMessenger.of(context);
                    try {
                      await widget.controller.logout();
                      navigator.pushNamedAndRemoveUntil('/login', (route) => false);
                    } on ApiException catch (error) {
                      messenger.showSnackBar(SnackBar(content: Text(error.message)));
                    }
                  },
                  icon: const Icon(Icons.logout),
                  label: const Text('로그아웃'),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class ProfileEditScreen extends StatefulWidget {
  const ProfileEditScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<ProfileEditScreen> createState() => _ProfileEditScreenState();
}

class _ProfileEditScreenState extends State<ProfileEditScreen> {
  late final TextEditingController _nicknameController;
  bool _saving = false;
  String? _profileImageUrl;
  XFile? _selectedImage;

  @override
  void initState() {
    super.initState();
    final profile = widget.controller.profile;
    _nicknameController = TextEditingController(text: profile?.nickname ?? '');
    _profileImageUrl = profile?.profileImageUrl;
  }

  @override
  void dispose() {
    _nicknameController.dispose();
    super.dispose();
  }

  /// 갤러리에서 이미지를 선택한다.
  Future<void> _pickImage() async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 512,
      maxHeight: 512,
      imageQuality: 80,
    );
    if (picked != null) {
      setState(() => _selectedImage = picked);
    }
  }

  /// 프로필을 저장한다. Multipart로 닉네임과 이미지를 한 번에 전송한다.
  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      await widget.controller.saveProfile(
        nickname: _nicknameController.text.trim(),
        profileImage: _selectedImage,
      );
      if (!mounted) return;
      _showSnack(context, '프로필이 수정되었습니다.');
      Navigator.of(context).pop();
    } on ApiException catch (error) {
      _showSnack(context, error.message);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('프로필 수정')),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Center(
                  child: GestureDetector(
                    onTap: _saving ? null : _pickImage,
                    child: Stack(
                      children: [
                        CircleAvatar(
                          radius: 48,
                          backgroundColor: const Color(0xFF0F766E),
                          backgroundImage: _resolveAvatarImage(),
                          child: _resolveAvatarImage() == null
                              ? Text(
                                  _profileAvatar(widget.controller.profile),
                                  style: const TextStyle(fontSize: 42),
                                )
                              : null,
                        ),
                        Positioned(
                          bottom: 0,
                          right: 0,
                          child: Container(
                            padding: const EdgeInsets.all(6),
                            decoration: const BoxDecoration(
                              color: Color(0xFF19C37D),
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.camera_alt,
                              size: 16,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                const Center(
                  child: Text(
                    '사진을 탭하여 변경',
                    style: TextStyle(color: Colors.grey, fontSize: 12),
                  ),
                ),
                const SizedBox(height: 24),
                TextField(
                  controller: _nicknameController,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(labelText: '닉네임'),
                  maxLength: 20,
                ),
              ],
            ),
          ),
          _BottomActionBar(
            label: _saving ? '저장 중...' : '저장하기',
            enabled: !_saving,
            onPressed: _save,
          ),
        ],
      ),
    );
  }

  /// 아바타에 표시할 이미지를 결정한다.
  /// 새로 선택한 이미지 > 기존 프로필 이미지 > null(이모지 표시)
  ImageProvider? _resolveAvatarImage() {
    if (_selectedImage != null) {
      return FileImage(File(_selectedImage!.path));
    }
    if (_profileImageUrl != null && _profileImageUrl!.isNotEmpty) {
      return NetworkImage(_profileImageUrl!);
    }
    return null;
  }
}

class MyShowcasesScreen extends StatefulWidget {
  const MyShowcasesScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<MyShowcasesScreen> createState() => _MyShowcasesScreenState();
}

class _MyShowcasesScreenState extends State<MyShowcasesScreen> {
  String _tab = '공개';

  Future<PageInfo<ShowcaseSummary>> _load() {
    final token = widget.controller.session?.accessToken;
    if (token == null || token.isEmpty) {
      throw const ApiException('로그인이 필요합니다.');
    }
    return widget.controller.api.listMyShowcases(
      baseUrl: widget.controller.baseUrl,
      accessToken: token,
      showcaseStatus: _tab == '공개' ? 'ACTIVE' : 'HIDDEN',
      size: 30,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('내 쇼케이스')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: Row(
              children: [
                Expanded(
                  child: _TabButton(
                    label: '공개',
                    selected: _tab == '공개',
                    onTap: () => setState(() => _tab = '공개'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _TabButton(
                    label: '비공개',
                    selected: _tab == '비공개',
                    onTap: () => setState(() => _tab = '비공개'),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: FutureBuilder<PageInfo<ShowcaseSummary>>(
              future: _load(),
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return _ErrorState(
                    message: _errorText(snapshot.error),
                    onRetry: () => setState(() {}),
                  );
                }
                final items = snapshot.data?.items ?? const <ShowcaseSummary>[];
                if (items.isEmpty) {
                  return const _EmptyState(
                    icon: Icons.visibility_off_outlined,
                    message: '등록된 쇼케이스가 없습니다.',
                  );
                }
                return ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final item = items[index];
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: _ShowcaseCard(
                        item: item,
                        statusText: _tab == '공개' ? '공개' : '비공개',
                        onTap: () async {
                          final result = await Navigator.of(context).pushNamed(
                            '/showcase/detail',
                            arguments: ShowcaseDetailArgs(item.showcaseId),
                          );
                          if (result == true && mounted) setState(() {});
                        },
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class UnsupportedFeatureScreen extends StatelessWidget {
  const UnsupportedFeatureScreen({
    super.key,
    required this.title,
    required this.description,
  });

  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.construction_outlined, size: 56, color: Color(0xFF71717A)),
            const SizedBox(height: 16),
            Text(
              title,
              style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              description,
              style: const TextStyle(color: Color(0xFFA1A1AA), height: 1.5),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

class _ShowcaseCard extends StatelessWidget {
  const _ShowcaseCard({
    required this.item,
    required this.onTap,
    this.statusText,
  });

  final ShowcaseSummary item;
  final VoidCallback onTap;
  final String? statusText;

  @override
  Widget build(BuildContext context) {
    final specLabel = item.specLabel;

    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: onTap,
      child: Ink(
        decoration: BoxDecoration(
          color: const Color(0xFF111111),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFF27272A)),
        ),
        child: IntrinsicHeight(
          child: Row(
            children: [
              // 왼쪽: 대표 이미지
              SizedBox(
                width: 130,
                height: 130,
                child: Stack(
                  children: [
                    _ImageFrame(
                      imageUrl: item.primaryImageUrl,
                      size: 130,
                      emoji: item.category == 'BOOTS' ? '🥾' : '👕',
                      borderRadius: 16,
                    ),
                    // 왼쪽 상단: 3D 뱃지
                    if (item.has3dModel)
                      const Positioned(
                        top: 6,
                        left: 6,
                        child: _MiniBadge(
                          text: '3D',
                          backgroundColor: Color(0xCC0F172A),
                          foregroundColor: Color(0xFF22D3EE),
                        ),
                      ),
                    // 오른쪽 상단: 판매중 / 자랑글
                    Positioned(
                      top: 6,
                      right: 6,
                      child: item.isForSale
                          ? const _MiniBadge(
                              text: '판매중',
                              backgroundColor: Color(0xFFDC2626),
                              foregroundColor: Colors.white,
                            )
                          : const _MiniBadge(
                              text: '자랑글',
                              backgroundColor: Color(0xCC0F172A),
                              foregroundColor: Color(0xFF34D399),
                            ),
                    ),
                    if (statusText != null)
                      Positioned(
                        top: 6,
                        right: 6,
                        child: _MiniBadge(
                          text: statusText!,
                          backgroundColor: const Color(0xFF52525B),
                          foregroundColor: Colors.white,
                        ),
                      ),
                  ],
                ),
              ),
              // 오른쪽: 상품 정보
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // 제목
                      Text(
                        item.title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 6),
                      // 스펙 정보
                      Text(
                        specLabel,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Color(0xFFA1A1AA), fontSize: 13),
                      ),
                      const Spacer(),
                      // 하단: 등급 + 댓글
                      Row(
                        children: [
                          _GradePill(grade: item.conditionGrade),
                          const SizedBox(width: 8),
                          Text(
                            '착용 ${item.wearCount}회',
                            style: const TextStyle(color: Color(0xFF71717A), fontSize: 12),
                          ),
                          const Spacer(),
                          const Icon(Icons.chat_bubble_outline, size: 13, color: Color(0xFF71717A)),
                          const SizedBox(width: 3),
                          Text(
                            '${item.commentCount}',
                            style: const TextStyle(color: Color(0xFF71717A), fontSize: 12),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _BottomActionBar extends StatelessWidget {
  const _BottomActionBar({
    required this.label,
    required this.onPressed,
    this.enabled = true,
  });

  final String label;
  final VoidCallback onPressed;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      top: false,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
        decoration: const BoxDecoration(
          color: Color(0xFF111111),
          border: Border(top: BorderSide(color: Color(0xFF27272A))),
        ),
        child: SizedBox(
          width: double.infinity,
          child: FilledButton(
            onPressed: enabled ? onPressed : null,
            style: FilledButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 18),
              backgroundColor: const Color(0xFF10B981),
            ),
            child: Text(label),
          ),
        ),
      ),
    );
  }
}

class _ImageFrame extends StatelessWidget {
  const _ImageFrame({
    required this.imageUrl,
    required this.size,
    required this.emoji,
    this.borderRadius = 18,
  });

  final String? imageUrl;
  final double size;
  final String emoji;
  final double borderRadius;

  @override
  Widget build(BuildContext context) {
    final resolvedUrl = imageUrl;
    final emojiWidget = Center(
      child: Text(emoji, style: TextStyle(fontSize: size.isFinite ? size / 3 : 56)),
    );
    final child = resolvedUrl?.isNotEmpty == true
        ? CachedNetworkImage(
            imageUrl: resolvedUrl!,
            fit: BoxFit.cover,
            fadeInDuration: const Duration(milliseconds: 150),
            placeholder: (_, _) => const Center(child: CircularProgressIndicator()),
            errorWidget: (_, _, _) => emojiWidget,
          )
        : emojiWidget;

    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: Container(
        width: size.isFinite ? size : null,
        height: size.isFinite ? size : null,
        color: const Color(0xFF27272A),
        child: child,
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.title,
    required this.child,
  });

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF111111),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(color: Color(0xFFA1A1AA), fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }
}

class _LabeledValue extends StatelessWidget {
  const _LabeledValue({
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(color: Color(0xFF71717A), fontSize: 12)),
        const SizedBox(height: 4),
        Text(value, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
      ],
    );
  }
}

class _GradePill extends StatelessWidget {
  const _GradePill({required this.grade});

  final String grade;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: _gradeColor(grade),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        '$grade급',
        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 12),
      ),
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({
    required this.text,
    required this.color,
  });

  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        text,
        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 12),
      ),
    );
  }
}

class _MiniBadge extends StatelessWidget {
  const _MiniBadge({
    required this.text,
    required this.backgroundColor,
    required this.foregroundColor,
  });

  final String text;
  final Color backgroundColor;
  final Color foregroundColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        text,
        style: TextStyle(color: foregroundColor, fontSize: 11, fontWeight: FontWeight.w800),
      ),
    );
  }
}

class _SocialButton extends StatelessWidget {
  const _SocialButton({
    required this.backgroundColor,
    required this.foregroundColor,
    required this.icon,
    required this.label,
    required this.loading,
    required this.onPressed,
    this.borderColor,
  });

  final Color backgroundColor;
  final Color foregroundColor;
  final String icon;
  final String label;
  final bool loading;
  final VoidCallback onPressed;
  final Color? borderColor;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        style: ElevatedButton.styleFrom(
          backgroundColor: backgroundColor,
          foregroundColor: foregroundColor,
          padding: const EdgeInsets.symmetric(vertical: 18),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
            side: borderColor == null ? BorderSide.none : BorderSide(color: borderColor!),
          ),
        ),
        onPressed: loading ? null : onPressed,
        child: loading
            ? const SizedBox.square(
                dimension: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(icon, style: const TextStyle(fontSize: 18)),
                  const SizedBox(width: 8),
                  Text(label),
                ],
              ),
      ),
    );
  }
}

class _DisabledSocialButton extends StatelessWidget {
  const _DisabledSocialButton({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton(
        onPressed: null,
        style: OutlinedButton.styleFrom(
          padding: const EdgeInsets.symmetric(vertical: 18),
          side: const BorderSide(color: Color(0xFF3F3F46)),
        ),
        child: Text(label),
      ),
    );
  }
}

class _TabButton extends StatelessWidget {
  const _TabButton({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return FilledButton(
      onPressed: onTap,
      style: FilledButton.styleFrom(
        backgroundColor: selected ? const Color(0xFF10B981) : const Color(0xFF18181B),
        foregroundColor: selected ? Colors.white : const Color(0xFFA1A1AA),
      ),
      child: Text(label),
    );
  }
}

class _MenuTile extends StatelessWidget {
  const _MenuTile({
    required this.icon,
    required this.label,
    required this.onTap,
    this.badge,
    this.trailingText,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final String? badge;
  final String? trailingText;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: const Color(0xFFA1A1AA)),
      title: Text(label, style: const TextStyle(color: Colors.white)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (trailingText != null)
            SizedBox(
              width: 120,
              child: Text(
                trailingText!,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Color(0xFF71717A), fontSize: 12),
              ),
            ),
          if (badge != null)
            Container(
              margin: const EdgeInsets.only(right: 8),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: badge == '완료' ? const Color(0xFF10B981) : const Color(0xFFDC2626),
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                badge!,
                style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w700),
              ),
            ),
          const Icon(Icons.chevron_right, color: Color(0xFF52525B)),
        ],
      ),
      onTap: onTap,
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({
    required this.icon,
    required this.message,
  });

  final IconData icon;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48, color: const Color(0xFF52525B)),
            const SizedBox(height: 12),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Color(0xFFA1A1AA)),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({
    required this.message,
    required this.onRetry,
  });

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, size: 52, color: Color(0xFFFB7185)),
            const SizedBox(height: 12),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Color(0xFFA1A1AA), height: 1.5),
            ),
            const SizedBox(height: 12),
            OutlinedButton(onPressed: onRetry, child: const Text('다시 시도')),
          ],
        ),
      ),
    );
  }
}

String _errorText(Object? error) {
  if (error is ApiException) {
    return error.message;
  }
  return error?.toString() ?? '알 수 없는 오류가 발생했습니다.';
}

List<(String, String)> _catalogSpecEntries(CatalogItemDetail detail) {
  if (detail.category == 'BOOTS') {
    final spec = detail.bootsSpec;
    return [
      ('카테고리', '축구화'),
      ('스터드 타입', spec?.studType ?? '-'),
      ('사일로', spec?.siloName ?? '-'),
      ('출시연도', spec?.releaseYear ?? '-'),
      ('적합 표면', spec?.surfaceType ?? '-'),
      ('추가 스펙', spec?.extraSpecJson ?? '-'),
    ];
  }
  final spec = detail.uniformSpec;
  return [
    ('카테고리', '유니폼'),
    ('클럽', spec?.clubName ?? '-'),
    ('시즌', spec?.season ?? '-'),
    ('리그', spec?.league ?? '-'),
    ('킷 타입', spec?.kitType ?? '-'),
    ('추가 스펙', spec?.extraSpecJson ?? '-'),
  ];
}

Color _gradeColor(String grade) {
  switch (grade) {
    case 'S':
      return const Color(0xFFF59E0B);
    case 'A':
      return const Color(0xFF10B981);
    case 'B':
      return const Color(0xFF3B82F6);
    default:
      return const Color(0xFF52525B);
  }
}

String _gradeDescription(String grade) {
  switch (grade) {
    case 'S':
      return '미착용 새제품';
    case 'A':
      return '상태 우수';
    case 'B':
      return '보통 사용감';
    default:
      return '사용감 많음';
  }
}

String _categoryLabel(String category) {
  return category == 'UNIFORM' ? '유니폼' : '축구화';
}

String _ownerEmoji(int id) {
  const avatars = ['😊', '😎', '🥳', '⚽', '🏆', '⭐'];
  return avatars[id % avatars.length];
}

String _profileAvatar(UserProfile? profile) {
  final nickname = profile?.nickname;
  if (nickname == null || nickname.isEmpty) {
    return '😊';
  }
  return String.fromCharCode(nickname.runes.first);
}

Future<void> _showBaseUrlDialog(BuildContext context, AppController controller) async {
  final textController = TextEditingController(text: controller.baseUrl);
  await showDialog<void>(
    context: context,
    builder: (context) {
      return AlertDialog(
        backgroundColor: const Color(0xFF111111),
        title: const Text('Backend Base URL', style: TextStyle(color: Colors.white)),
        content: TextField(
          controller: textController,
          style: const TextStyle(color: Colors.white),
          decoration: const InputDecoration(hintText: 'http://localhost:8080'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () {
              controller.updateBaseUrl(textController.text);
              Navigator.of(context).pop();
            },
            child: const Text('저장'),
          ),
        ],
      );
    },
  );
}

void _showSnack(BuildContext context, String message) {
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(message)),
  );
}

// ───────────────────────────────────────────────────────────────
// 닉네임 설정 화면 (첫 로그인 시)
// ───────────────────────────────────────────────────────────────

class NicknameSetupScreen extends StatefulWidget {
  const NicknameSetupScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<NicknameSetupScreen> createState() => _NicknameSetupScreenState();
}

class _NicknameSetupScreenState extends State<NicknameSetupScreen> {
  final _nicknameController = TextEditingController();
  bool _checking = false;
  bool _saving = false;
  bool? _isAvailable;
  String? _statusMessage;

  @override
  void dispose() {
    _nicknameController.dispose();
    super.dispose();
  }

  /// 닉네임 유효성 검사 (2~20자)
  bool get _isValidLength {
    final text = _nicknameController.text.trim();
    return text.length >= 2 && text.length <= 20;
  }

  /// 닉네임 중복 확인
  Future<void> _checkNickname() async {
    if (!_isValidLength) {
      setState(() {
        _isAvailable = false;
        _statusMessage = '닉네임은 2자 이상 20자 이하여야 합니다';
      });
      return;
    }

    setState(() {
      _checking = true;
      _isAvailable = null;
      _statusMessage = null;
    });

    try {
      final available = await widget.controller
          .checkNicknameAvailable(_nicknameController.text.trim());
      if (!mounted) return;
      setState(() {
        _isAvailable = available;
        _statusMessage = available ? '사용 가능한 닉네임입니다' : '이미 사용 중인 닉네임입니다';
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _isAvailable = false;
        _statusMessage = '중복 확인 중 오류가 발생했습니다';
      });
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  /// 닉네임 저장 후 홈으로 이동
  Future<void> _saveAndNavigate() async {
    if (_isAvailable != true) return;

    setState(() => _saving = true);
    try {
      await widget.controller.saveProfile(
        nickname: _nicknameController.text.trim(),
      );
      if (!mounted) return;
      Navigator.of(context).pushReplacementNamed('/shell');
    } catch (error) {
      if (!mounted) return;
      _showSnack(context, '닉네임 설정에 실패했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 48),

              // 타이틀
              Text(
                '닉네임을 설정해주세요',
                style: theme.textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '다른 사용자에게 보여질 이름입니다',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: Colors.white54,
                ),
              ),
              const SizedBox(height: 32),

              // 닉네임 입력 + 중복확인 버튼
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _nicknameController,
                      maxLength: 20,
                      style: const TextStyle(color: Colors.white),
                      decoration: const InputDecoration(
                        hintText: '닉네임 입력 (2~20자)',
                        counterStyle: TextStyle(color: Colors.white38),
                      ),
                      onChanged: (_) => setState(() {
                        _isAvailable = null;
                        _statusMessage = null;
                      }),
                    ),
                  ),
                  const SizedBox(width: 12),
                  SizedBox(
                    height: 48,
                    child: ElevatedButton(
                      onPressed: (_checking || !_isValidLength) ? null : _checkNickname,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF19C37D),
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: _checking
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Text('중복확인'),
                    ),
                  ),
                ],
              ),

              // 상태 메시지
              if (_statusMessage != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    _statusMessage!,
                    style: TextStyle(
                      fontSize: 13,
                      color: _isAvailable == true
                          ? const Color(0xFF19C37D)
                          : Colors.redAccent,
                    ),
                  ),
                ),

              const Spacer(),

              // 시작하기 버튼
              SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton(
                  onPressed: (_isAvailable == true && !_saving)
                      ? _saveAndNavigate
                      : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF19C37D),
                    foregroundColor: Colors.white,
                    disabledBackgroundColor: const Color(0xFF1A1A1A),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                  child: _saving
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text(
                          '시작하기',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
