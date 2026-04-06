import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';

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
    // 1. 쇼케이스 이미지 + 3D 소스 이미지 Presigned URL 발급
    final imageFiles = payload.images.map((f) => _FileEntry(f, 'SHOWCASE_IMAGE')).toList();
    final modelFiles = payload.modelSourceImages.map((f) => _FileEntry(f, 'MODEL_SOURCE')).toList();
    final allFiles = [...imageFiles, ...modelFiles];

    final presigned = await _generatePresignedUrls(
      baseUrl: baseUrl,
      accessToken: accessToken,
      files: allFiles,
    );

    // 2. 각 이미지를 S3에 직접 PUT 업로드
    for (var i = 0; i < allFiles.length; i++) {
      await _uploadToS3(
        presignedUrl: presigned[i].presignedUrl,
        file: allFiles[i].xFile,
        contentType: presigned[i].contentType,
      );
    }

    // 3. s3Key 목록 분리
    final imageKeys = presigned
        .where((r) => r.type == 'SHOWCASE_IMAGE')
        .map((r) => r.s3Key)
        .toList();
    final modelSourceImageKeys = presigned
        .where((r) => r.type == 'MODEL_SOURCE')
        .map((r) => r.s3Key)
        .toList();

    // 4. 쇼케이스 등록 (JSON)
    final body = <String, dynamic>{
      'category': payload.category,
      'brand': payload.brand,
      'title': payload.title,
      'description': payload.description,
      'userSize': payload.userSize,
      'conditionGrade': payload.conditionGrade,
      'wearCount': payload.wearCount,
      'isForSale': payload.isForSale,
      'primaryImageIndex': payload.primaryImageIndex,
      'imageKeys': imageKeys,
      if (modelSourceImageKeys.isNotEmpty) 'modelSourceImageKeys': modelSourceImageKeys,
      if (payload.catalogItemId != null) 'catalogItemId': payload.catalogItemId,
      if (payload.modelCode != null) 'modelCode': payload.modelCode,
      // 축구화 스펙
      if (payload.studType != null) 'studType': payload.studType,
      if (payload.siloName != null) 'siloName': payload.siloName,
      if (payload.releaseYear != null) 'releaseYear': payload.releaseYear,
      if (payload.surfaceType != null) 'surfaceType': payload.surfaceType,
      // 유니폼 스펙
      if (payload.clubName != null) 'clubName': payload.clubName,
      if (payload.season != null) 'season': payload.season,
      if (payload.league != null) 'league': payload.league,
      if (payload.kitType != null) 'kitType': payload.kitType,
    };

    final response = await http.post(
      _uri(baseUrl, '/api/v1/showcases'),
      headers: _headers(accessToken: accessToken),
      body: jsonEncode(body),
    );
    final data = _extractData(response);
    return (data['showcaseId'] as num?)?.toInt() ?? 0;
  }

  /// Presigned URL 목록을 발급받는다.
  Future<List<_PresignedUrlResult>> _generatePresignedUrls({
    required String baseUrl,
    required String accessToken,
    required List<_FileEntry> files,
  }) async {
    if (files.isEmpty) return const [];

    final fileInfos = files.map((entry) => {
      'contentType': entry.contentType,
      'filename': entry.xFile.name,
      'type': entry.type,
    }).toList();

    final response = await http.post(
      _uri(baseUrl, '/api/v1/showcases/upload-urls'),
      headers: _headers(accessToken: accessToken),
      body: jsonEncode({'files': fileInfos}),
    );

    final List<dynamic> raw = jsonDecode(response.body)['data'] as List<dynamic>;
    return raw
        .whereType<Map<String, dynamic>>()
        .map(_PresignedUrlResult.fromJson)
        .toList();
  }

  /// Presigned URL에 이미지를 직접 PUT 업로드한다.
  Future<void> _uploadToS3({
    required String presignedUrl,
    required XFile file,
    required String contentType,
  }) async {
    final bytes = await file.readAsBytes();
    final response = await http.put(
      Uri.parse(presignedUrl),
      headers: {'Content-Type': contentType},
      body: bytes,
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException('이미지 업로드에 실패했습니다 (${response.statusCode}).');
    }
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

/// Presigned URL 발급 요청 시 파일 정보와 업로드 유형을 묶는 내부 헬퍼.
class _FileEntry {
  _FileEntry(this.xFile, this.type);

  final XFile xFile;
  final String type; // 'SHOWCASE_IMAGE' | 'MODEL_SOURCE'

  String get contentType => xFile.mimeType ?? 'image/jpeg';
}

/// Presigned URL 발급 응답 항목.
class _PresignedUrlResult {
  const _PresignedUrlResult({
    required this.presignedUrl,
    required this.s3Key,
    required this.type,
    required this.contentType,
  });

  final String presignedUrl;
  final String s3Key;
  final String type;
  final String contentType;

  factory _PresignedUrlResult.fromJson(Map<String, dynamic> json) {
    return _PresignedUrlResult(
      presignedUrl: json['presignedUrl'] as String? ?? '',
      s3Key: json['s3Key'] as String? ?? '',
      type: json['type'] as String? ?? '',
      contentType: 'image/jpeg', // S3 업로드 시 사용할 Content-Type
    );
  }
}
