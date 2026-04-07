import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:kakao_flutter_sdk_common/kakao_flutter_sdk_common.dart';

import 'src/app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // 환경 결정: --dart-define=ENV=dev 로 실행하면 개발 모드
  const envString = String.fromEnvironment('ENV', defaultValue: 'prod');
  final environment =
      envString == 'dev' ? AppEnvironment.dev : AppEnvironment.prod;

  if (kDebugMode) {
    debugPrint('[GearShow] 환경: ${environment.name}');
  }

  // 운영 환경에서만 카카오 SDK 초기화
  if (environment == AppEnvironment.prod) {
    _initKakaoSdk();
  }

  runApp(GearShowApp(environment: environment));
}

/// 카카오 SDK를 초기화한다. 네이티브 앱 키가 제공된 경우에만 실행된다.
void _initKakaoSdk() {
  const nativeAppKey = String.fromEnvironment('KAKAO_NATIVE_APP_KEY');
  if (nativeAppKey.isNotEmpty) {
    const javaScriptAppKey = String.fromEnvironment('KAKAO_JAVASCRIPT_APP_KEY');
    KakaoSdk.init(
      nativeAppKey: nativeAppKey,
      javaScriptAppKey: javaScriptAppKey.isNotEmpty ? javaScriptAppKey : null,
    );
    if (kDebugMode) {
      debugPrint('[GearShow] 카카오 SDK 초기화 완료');
    }
  } else {
    if (kDebugMode) {
      debugPrint('[GearShow] KAKAO_NATIVE_APP_KEY 미설정 - 카카오 로그인 비활성화');
    }
  }
}
