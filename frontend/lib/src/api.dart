import 'dart:convert';

import 'package:http/http.dart' as http;

import 'models.dart';

class GearShowApiClient {
  static const _jsonHeaders = {'Content-Type': 'application/json'};

  Uri _uri(
    String baseUrl,
    String path, {
    Map<String, String?> query = const {},
  }) {
    final base = Uri.parse(baseUrl);
    return base.replace(
      path: '${base.path}$path'.replaceAll('//', '/'),
      queryParameters: {
        for (final entry in query.entries)
          if (entry.value != null && entry.value!.isNotEmpty)
            entry.key: entry.value,
      },
    );
  }

  Future<AuthSession> login({
    required String baseUrl,
    required String provider,
    String? authorizationCode,
    String? accessToken,
  }) async {
    final response = await http.post(
      _uri(baseUrl, '/api/v1/auth/login/$provider'),
      headers: _jsonHeaders,
      body: jsonEncode({
        'authorizationCode': authorizationCode,
        'accessToken': accessToken,
      }),
    );
    return AuthSession.fromJson(_extractData(response));
  }

  Future<AuthSession> refresh({
    required String baseUrl,
    required String refreshToken,
  }) async {
    final response = await http.post(
      _uri(baseUrl, '/api/v1/auth/refresh'),
      headers: _jsonHeaders,
      body: jsonEncode({'refreshToken': refreshToken}),
    );
    return AuthSession.fromJson(_extractData(response));
  }

  Future<void> logout({
    required String baseUrl,
    required String accessToken,
  }) async {
    final response = await http.post(
      _uri(baseUrl, '/api/v1/auth/logout'),
      headers: _headers(accessToken: accessToken),
    );
    _extractData(response);
  }

  /// 닉네임 중복 여부를 확인한다.
  Future<bool> checkNicknameAvailable({
    required String baseUrl,
    required String nickname,
  }) async {
    final response = await http.get(
      _uri(baseUrl, '/api/v1/users/nicknames/check', query: {'nickname': nickname}),
    );
    final data = _extractData(response);
    return data['available'] as bool? ?? false;
  }

  Future<UserProfile> getMyProfile({
    required String baseUrl,
    required String accessToken,
  }) async {
    final response = await http.get(
      _uri(baseUrl, '/api/v1/users/me'),
      headers: _headers(accessToken: accessToken),
    );
    return UserProfile.fromJson(_extractData(response));
  }

  Future<UserProfile> updateMyProfile({
    required String baseUrl,
    required String accessToken,
    String? nickname,
    String? profileImageUrl,
  }) async {
    final response = await http.patch(
      _uri(baseUrl, '/api/v1/users/me'),
      headers: _headers(accessToken: accessToken),
      body: jsonEncode({
        'nickname': nickname,
        'profileImageUrl': profileImageUrl,
      }),
    );
    return UserProfile.fromJson(_extractData(response));
  }

  Future<PageInfo<CatalogItemSummary>> listCatalogs({
    required String baseUrl,
    String? category,
    String? keyword,
    String? pageToken,
    int size = 20,
  }) async {
    final response = await http.get(
      _uri(
        baseUrl,
        '/api/v1/catalogs',
        query: {
          'category': category,
          'keyword': keyword,
          'pageToken': pageToken,
          'size': '$size',
        },
      ),
    );
    return _parsePageInfo(
      _extractData(response),
      CatalogItemSummary.fromJson,
    );
  }

  Future<CatalogItemDetail> getCatalogDetail({
    required String baseUrl,
    required int catalogItemId,
  }) async {
    final response = await http.get(
      _uri(baseUrl, '/api/v1/catalogs/$catalogItemId'),
    );
    return CatalogItemDetail.fromJson(_extractData(response));
  }

  Future<PageInfo<ShowcaseSummary>> listShowcases({
    required String baseUrl,
    String? category,
    String? keyword,
    bool? isForSale,
    String? conditionGrade,
    String? pageToken,
    int size = 20,
  }) async {
    final response = await http.get(
      _uri(
        baseUrl,
        '/api/v1/showcases',
        query: {
          'category': category,
          'keyword': keyword,
          'isForSale': isForSale == null ? null : '$isForSale',
          'conditionGrade': conditionGrade,
          'pageToken': pageToken,
          'size': '$size',
        },
      ),
    );
    return _parsePageInfo(_extractData(response), ShowcaseSummary.fromJson);
  }

  Future<PageInfo<ShowcaseSummary>> listMyShowcases({
    required String baseUrl,
    required String accessToken,
    String? showcaseStatus,
    String? pageToken,
    int size = 20,
  }) async {
    final response = await http.get(
      _uri(
        baseUrl,
        '/api/v1/users/me/showcases',
        query: {
          'showcaseStatus': showcaseStatus,
          'pageToken': pageToken,
          'size': '$size',
        },
      ),
      headers: _headers(accessToken: accessToken),
    );
    return _parsePageInfo(_extractData(response), ShowcaseSummary.fromJson);
  }

  Future<ShowcaseDetail> getShowcaseDetail({
    required String baseUrl,
    required int showcaseId,
  }) async {
    final response = await http.get(
      _uri(baseUrl, '/api/v1/showcases/$showcaseId'),
    );
    return ShowcaseDetail.fromJson(_extractData(response));
  }

  Future<PageInfo<ShowcaseComment>> listComments({
    required String baseUrl,
    required int showcaseId,
    String? pageToken,
    int size = 20,
  }) async {
    final response = await http.get(
      _uri(
        baseUrl,
        '/api/v1/showcases/$showcaseId/comments',
        query: {'pageToken': pageToken, 'size': '$size'},
      ),
    );
    return _parsePageInfo(_extractData(response), ShowcaseComment.fromJson);
  }

  Future<void> createComment({
    required String baseUrl,
    required String accessToken,
    required int showcaseId,
    required String content,
  }) async {
    final response = await http.post(
      _uri(baseUrl, '/api/v1/showcases/$showcaseId/comments'),
      headers: _headers(accessToken: accessToken),
      body: jsonEncode({'content': content}),
    );
    _extractData(response);
  }

  Future<int> createShowcase({
    required String baseUrl,
    required String accessToken,
    required CreateShowcasePayload payload,
  }) async {
    final request = http.MultipartRequest(
      'POST',
      _uri(baseUrl, '/api/v1/showcases'),
    );
    request.headers.addAll({'Authorization': 'Bearer $accessToken'});
    request.fields.addAll({
      'category': payload.category,
      'brand': payload.brand,
      'title': payload.title,
      'description': payload.description,
      'userSize': payload.userSize,
      'conditionGrade': payload.conditionGrade,
      'wearCount': '${payload.wearCount}',
      'isForSale': '${payload.isForSale}',
      'primaryImageIndex': '${payload.primaryImageIndex}',
    });
    if (payload.catalogItemId != null) {
      request.fields['catalogItemId'] = '${payload.catalogItemId}';
    }
    if (payload.modelCode != null) {
      request.fields['modelCode'] = payload.modelCode!;
    }
    // 축구화 스펙
    if (payload.studType != null) request.fields['studType'] = payload.studType!;
    if (payload.siloName != null) request.fields['siloName'] = payload.siloName!;
    if (payload.releaseYear != null) request.fields['releaseYear'] = payload.releaseYear!;
    if (payload.surfaceType != null) request.fields['surfaceType'] = payload.surfaceType!;
    // 유니폼 스펙
    if (payload.clubName != null) request.fields['clubName'] = payload.clubName!;
    if (payload.season != null) request.fields['season'] = payload.season!;
    if (payload.league != null) request.fields['league'] = payload.league!;
    if (payload.kitType != null) request.fields['kitType'] = payload.kitType!;

    for (final image in payload.images) {
      request.files.add(
        http.MultipartFile.fromBytes(
          'images',
          await image.readAsBytes(),
          filename: image.name,
        ),
      );
    }

    for (final image in payload.modelSourceImages) {
      request.files.add(
        http.MultipartFile.fromBytes(
          'modelSourceImages',
          await image.readAsBytes(),
          filename: image.name,
        ),
      );
    }

    final streamed = await request.send();
    final response = await http.Response.fromStream(streamed);
    final data = _extractData(response);
    return (data['showcaseId'] as num?)?.toInt() ?? 0;
  }

  PageInfo<T> _parsePageInfo<T>(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) parser,
  ) {
    final rawItems = (json['data'] as List<dynamic>? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(parser)
        .toList();
    return PageInfo(
      pageToken: json['pageToken'] as String?,
      items: rawItems,
      size: (json['size'] as num?)?.toInt() ?? rawItems.length,
      hasNext: json['hasNext'] as bool? ?? false,
    );
  }

  Map<String, String> _headers({String? accessToken}) {
    return {
      ..._jsonHeaders,
      if (accessToken != null && accessToken.isNotEmpty)
        'Authorization': 'Bearer $accessToken',
    };
  }

  Map<String, dynamic> _extractData(http.Response response) {
    final decoded = response.body.isEmpty
        ? <String, dynamic>{}
        : jsonDecode(response.body) as Map<String, dynamic>;
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(
        decoded['message'] as String? ??
            '요청 처리 중 오류가 발생했습니다 (${response.statusCode}).',
      );
    }
    final data = decoded['data'];
    if (data is Map<String, dynamic>) {
      return data;
    }
    if (data == null) {
      return <String, dynamic>{};
    }
    throw const ApiException('예상하지 못한 응답 형식입니다.');
  }
}
