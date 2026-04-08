import 'dart:convert';

import 'package:image_picker/image_picker.dart';

class ApiException implements Exception {
  const ApiException(this.message);

  final String message;

  @override
  String toString() => message;
}

class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.refreshToken,
    required this.tokenType,
    required this.expiresIn,
  });

  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final int expiresIn;

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json['accessToken'] as String? ?? '',
      refreshToken: json['refreshToken'] as String? ?? '',
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresIn: (json['expiresIn'] as num?)?.toInt() ?? 0,
    );
  }
}

class UserProfile {
  const UserProfile({
    required this.userId,
    required this.nickname,
    required this.profileImageUrl,
    required this.phoneNumber,
    required this.isPhoneVerified,
    required this.userStatus,
  });

  final int userId;
  final String nickname;
  final String? profileImageUrl;
  final String? phoneNumber;
  final bool isPhoneVerified;
  final String userStatus;

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      userId: (json['userId'] as num?)?.toInt() ?? 0,
      nickname: json['nickname'] as String? ?? '사용자',
      profileImageUrl: json['profileImageUrl'] as String?,
      phoneNumber: json['phoneNumber'] as String?,
      isPhoneVerified: json['isPhoneVerified'] as bool? ?? false,
      userStatus: json['userStatus'] as String? ?? 'UNKNOWN',
    );
  }
}

class PageInfo<T> {
  const PageInfo({
    required this.pageToken,
    required this.items,
    required this.size,
    required this.hasNext,
  });

  final String? pageToken;
  final List<T> items;
  final int size;
  final bool hasNext;
}

class CatalogItemSummary {
  const CatalogItemSummary({
    required this.catalogItemId,
    required this.category,
    required this.brand,
    required this.modelCode,
    required this.officialImageUrl,
  });

  final int catalogItemId;
  final String category;
  final String brand;
  final String modelCode;
  final String? officialImageUrl;

  factory CatalogItemSummary.fromJson(Map<String, dynamic> json) {
    return CatalogItemSummary(
      catalogItemId: (json['catalogItemId'] as num?)?.toInt() ?? 0,
      category: json['category'] as String? ?? 'BOOTS',
      brand: json['brand'] as String? ?? '',
      modelCode: json['modelCode'] as String? ?? '',
      officialImageUrl: json['officialImageUrl'] as String?,
    );
  }
}

class CatalogItemDetail {
  const CatalogItemDetail({
    required this.catalogItemId,
    required this.category,
    required this.brand,
    required this.modelCode,
    required this.officialImageUrl,
    required this.catalogStatus,
    required this.bootsSpec,
    required this.uniformSpec,
  });

  final int catalogItemId;
  final String category;
  final String brand;
  final String modelCode;
  final String? officialImageUrl;
  final String catalogStatus;
  final BootsSpec? bootsSpec;
  final UniformSpec? uniformSpec;

  factory CatalogItemDetail.fromJson(Map<String, dynamic> json) {
    return CatalogItemDetail(
      catalogItemId: (json['catalogItemId'] as num?)?.toInt() ?? 0,
      category: json['category'] as String? ?? 'BOOTS',
      brand: json['brand'] as String? ?? '',
      modelCode: json['modelCode'] as String? ?? '',
      officialImageUrl: json['officialImageUrl'] as String?,
      catalogStatus: json['catalogStatus'] as String? ?? 'ACTIVE',
      bootsSpec: json['bootsSpec'] is Map<String, dynamic>
          ? BootsSpec.fromJson(json['bootsSpec'] as Map<String, dynamic>)
          : null,
      uniformSpec: json['uniformSpec'] is Map<String, dynamic>
          ? UniformSpec.fromJson(json['uniformSpec'] as Map<String, dynamic>)
          : null,
    );
  }
}

class BootsSpec {
  const BootsSpec({
    required this.studType,
    required this.siloName,
    required this.releaseYear,
    required this.surfaceType,
    required this.extraSpecJson,
  });

  final String? studType;
  final String? siloName;
  final String? releaseYear;
  final String? surfaceType;
  final String? extraSpecJson;

  factory BootsSpec.fromJson(Map<String, dynamic> json) {
    return BootsSpec(
      studType: json['studType'] as String?,
      siloName: json['siloName'] as String?,
      releaseYear: json['releaseYear'] as String?,
      surfaceType: json['surfaceType'] as String?,
      extraSpecJson: json['extraSpecJson'] as String?,
    );
  }
}

class UniformSpec {
  const UniformSpec({
    required this.clubName,
    required this.season,
    required this.league,
    required this.kitType,
    required this.extraSpecJson,
  });

  final String? clubName;
  final String? season;
  final String? league;
  final String? kitType;
  final String? extraSpecJson;

  factory UniformSpec.fromJson(Map<String, dynamic> json) {
    return UniformSpec(
      clubName: json['clubName'] as String?,
      season: json['season'] as String?,
      league: json['league'] as String?,
      kitType: json['kitType'] as String?,
      extraSpecJson: json['extraSpecJson'] as String?,
    );
  }
}

class ShowcaseSummary {
  const ShowcaseSummary({
    required this.showcaseId,
    required this.title,
    required this.category,
    required this.brand,
    this.userSize,
    required this.conditionGrade,
    required this.isForSale,
    required this.wearCount,
    required this.primaryImageUrl,
    required this.commentCount,
    required this.has3dModel,
    this.spec,
  });

  final int showcaseId;
  final String title;
  final String category;
  final String brand;
  final String? userSize;
  final String conditionGrade;
  final bool isForSale;
  final int wearCount;
  final String? primaryImageUrl;
  final int commentCount;
  final bool has3dModel;
  final SpecSummary? spec;

  /// 목록 카드에 표시할 스펙 정보 텍스트.
  /// 축구화: 브랜드 · 사일로 · 스터드 · 사이즈
  /// 유니폼: 시즌 · 클럽 · 사이즈
  String get specLabel {
    final s = spec;
    if (category == 'BOOTS') {
      final parts = <String>[
        brand,
        if (s?.siloName != null) s!.siloName!,
        if (s?.studType != null) s!.studType!,
        if (userSize != null && userSize!.isNotEmpty) userSize!,
      ];
      return parts.join(' · ');
    }
    if (category == 'UNIFORM') {
      final parts = <String>[
        if (s?.season != null) s!.season!,
        if (s?.clubName != null) s!.clubName!,
        if (userSize != null && userSize!.isNotEmpty) userSize!,
      ];
      return parts.join(' · ');
    }
    return brand;
  }

  factory ShowcaseSummary.fromJson(Map<String, dynamic> json) {
    return ShowcaseSummary(
      showcaseId: (json['showcaseId'] as num?)?.toInt() ?? 0,
      title: json['title'] as String? ?? '',
      category: json['category'] as String? ?? '',
      brand: json['brand'] as String? ?? '',
      userSize: json['userSize'] as String?,
      conditionGrade: json['conditionGrade'] as String? ?? 'A',
      isForSale: json['isForSale'] as bool? ?? false,
      wearCount: (json['wearCount'] as num?)?.toInt() ?? 0,
      primaryImageUrl: json['primaryImageUrl'] as String?,
      commentCount: (json['commentCount'] as num?)?.toInt() ?? 0,
      has3dModel: json['has3dModel'] as bool? ?? false,
      spec: json['spec'] != null
          ? SpecSummary.fromJson(json['spec'] as Map<String, dynamic>)
          : null,
    );
  }
}

/// 카테고리별 스펙 요약 (단일 모델, specType으로 구분).
class SpecSummary {
  const SpecSummary({
    required this.specType,
    this.studType, this.siloName, this.surfaceType,
    this.clubName, this.season, this.league, this.kitType,
  });

  final String specType;
  // BOOTS
  final String? studType;
  final String? siloName;
  final String? surfaceType;
  // UNIFORM
  final String? clubName;
  final String? season;
  final String? league;
  final String? kitType;

  factory SpecSummary.fromJson(Map<String, dynamic> json) {
    return SpecSummary(
      specType: json['specType'] as String? ?? '',
      studType: json['studType'] as String?,
      siloName: json['siloName'] as String?,
      surfaceType: json['surfaceType'] as String?,
      clubName: json['clubName'] as String?,
      season: json['season'] as String?,
      league: json['league'] as String?,
      kitType: json['kitType'] as String?,
    );
  }
}

class ShowcaseDetail {
  const ShowcaseDetail({
    required this.showcaseId,
    required this.ownerId,
    required this.catalogItemId,
    required this.category,
    required this.brand,
    this.modelCode,
    required this.title,
    required this.description,
    required this.userSize,
    required this.conditionGrade,
    required this.wearCount,
    required this.isForSale,
    required this.showcaseStatus,
    required this.images,
    required this.model3d,
    this.spec,
  });

  final int showcaseId;
  final int ownerId;
  final int catalogItemId;
  final String category;
  final String brand;
  final String? modelCode;
  final String title;
  final String? description;
  final String? userSize;
  final String conditionGrade;
  final int wearCount;
  final bool isForSale;
  final String showcaseStatus;
  final List<ShowcaseImage> images;
  final ShowcaseModel3d? model3d;
  final ShowcaseSpecDetail? spec;

  factory ShowcaseDetail.fromJson(Map<String, dynamic> json) {
    return ShowcaseDetail(
      showcaseId: (json['showcaseId'] as num?)?.toInt() ?? 0,
      ownerId: (json['ownerId'] as num?)?.toInt() ?? 0,
      catalogItemId: (json['catalogItemId'] as num?)?.toInt() ?? 0,
      category: json['category'] as String? ?? 'BOOTS',
      brand: json['brand'] as String? ?? '',
      modelCode: json['modelCode'] as String?,
      title: json['title'] as String? ?? '',
      description: json['description'] as String?,
      userSize: json['userSize'] as String?,
      conditionGrade: json['conditionGrade'] as String? ?? 'A',
      wearCount: (json['wearCount'] as num?)?.toInt() ?? 0,
      isForSale: json['isForSale'] as bool? ?? false,
      showcaseStatus: json['showcaseStatus'] as String? ?? 'ACTIVE',
      images: ((json['images'] as List<dynamic>?) ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(ShowcaseImage.fromJson)
          .toList(),
      model3d: json['model3d'] is Map<String, dynamic>
          ? ShowcaseModel3d.fromJson(json['model3d'] as Map<String, dynamic>)
          : null,
      spec: json['spec'] is Map<String, dynamic>
          ? ShowcaseSpecDetail.fromJson(json['spec'] as Map<String, dynamic>)
          : null,
    );
  }
}

/// 상세 조회용 스펙 (specData JSON을 파싱하여 필드로 제공).
class ShowcaseSpecDetail {
  const ShowcaseSpecDetail({
    required this.specType,
    // BOOTS
    this.studType, this.siloName, this.releaseYear, this.surfaceType,
    // UNIFORM
    this.clubName, this.season, this.league, this.kitType,
  });

  final String specType;
  final String? studType;
  final String? siloName;
  final String? releaseYear;
  final String? surfaceType;
  final String? clubName;
  final String? season;
  final String? league;
  final String? kitType;

  factory ShowcaseSpecDetail.fromJson(Map<String, dynamic> json) {
    final specType = json['specType'] as String? ?? '';
    // specData는 JSON 문자열 → 파싱하여 필드 추출
    Map<String, dynamic> data = {};
    if (json['specData'] is String) {
      try {
        data = Map<String, dynamic>.from(
          const JsonDecoder().convert(json['specData'] as String) as Map,
        );
      } catch (_) {}
    }
    return ShowcaseSpecDetail(
      specType: specType,
      studType: (data['studType'] ?? json['studType']) as String?,
      siloName: (data['siloName'] ?? json['siloName']) as String?,
      releaseYear: (data['releaseYear'] ?? json['releaseYear']) as String?,
      surfaceType: (data['surfaceType'] ?? json['surfaceType']) as String?,
      clubName: (data['clubName'] ?? json['clubName']) as String?,
      season: (data['season'] ?? json['season']) as String?,
      league: (data['league'] ?? json['league']) as String?,
      kitType: (data['kitType'] ?? json['kitType']) as String?,
    );
  }
}

class ShowcaseImage {
  const ShowcaseImage({
    required this.showcaseImageId,
    required this.imageUrl,
    required this.sortOrder,
    required this.isPrimary,
  });

  final int showcaseImageId;
  final String imageUrl;
  final int sortOrder;
  final bool isPrimary;

  factory ShowcaseImage.fromJson(Map<String, dynamic> json) {
    return ShowcaseImage(
      showcaseImageId: (json['showcaseImageId'] as num?)?.toInt() ?? 0,
      imageUrl: json['imageUrl'] as String? ?? '',
      sortOrder: (json['sortOrder'] as num?)?.toInt() ?? 0,
      isPrimary: json['isPrimary'] as bool? ?? false,
    );
  }
}

class ShowcaseModel3d {
  const ShowcaseModel3d({
    required this.showcase3dModelId,
    required this.modelFileUrl,
    required this.previewImageUrl,
    required this.modelStatus,
  });

  final int showcase3dModelId;
  final String? modelFileUrl;
  final String? previewImageUrl;
  final String modelStatus;

  factory ShowcaseModel3d.fromJson(Map<String, dynamic> json) {
    return ShowcaseModel3d(
      showcase3dModelId: (json['showcase3dModelId'] as num?)?.toInt() ?? 0,
      modelFileUrl: json['modelFileUrl'] as String?,
      previewImageUrl: json['previewImageUrl'] as String?,
      modelStatus: json['modelStatus'] as String? ?? 'PENDING',
    );
  }
}

class ShowcaseComment {
  const ShowcaseComment({
    required this.showcaseCommentId,
    required this.authorId,
    required this.content,
    required this.createdAt,
  });

  final int showcaseCommentId;
  final int authorId;
  final String content;
  final String? createdAt;

  factory ShowcaseComment.fromJson(Map<String, dynamic> json) {
    return ShowcaseComment(
      showcaseCommentId: (json['showcaseCommentId'] as num?)?.toInt() ?? 0,
      authorId: (json['authorId'] as num?)?.toInt() ?? 0,
      content: json['content'] as String? ?? '',
      createdAt: json['createdAt'] as String?,
    );
  }
}

class ShowcaseDraft {
  const ShowcaseDraft({
    required this.catalogItem,
    required this.title,
    required this.description,
    required this.userSize,
    required this.conditionGrade,
    required this.wearCount,
    required this.isForSale,
    this.studType,
    this.siloName,
    this.releaseYear,
    this.surfaceType,
    this.clubName,
    this.season,
    this.league,
    this.kitType,
  });

  final CatalogItemSummary catalogItem;
  final String title;
  final String description;
  final String userSize;
  final String conditionGrade;
  final int wearCount;
  final bool isForSale;
  // 축구화 스펙
  final String? studType;
  final String? siloName;
  final String? releaseYear;
  final String? surfaceType;
  // 유니폼 스펙
  final String? clubName;
  final String? season;
  final String? league;
  final String? kitType;

  ShowcaseDraft copyWith({
    CatalogItemSummary? catalogItem,
    String? title,
    String? description,
    String? userSize,
    String? conditionGrade,
    int? wearCount,
    bool? isForSale,
  }) {
    return ShowcaseDraft(
      catalogItem: catalogItem ?? this.catalogItem,
      title: title ?? this.title,
      description: description ?? this.description,
      userSize: userSize ?? this.userSize,
      conditionGrade: conditionGrade ?? this.conditionGrade,
      wearCount: wearCount ?? this.wearCount,
      isForSale: isForSale ?? this.isForSale,
      studType: studType,
      siloName: siloName,
      releaseYear: releaseYear,
      surfaceType: surfaceType,
      clubName: clubName,
      season: season,
      league: league,
      kitType: kitType,
    );
  }
}

class CreateShowcasePayload {
  const CreateShowcasePayload({
    this.catalogItemId,
    required this.category,
    required this.brand,
    this.modelCode,
    required this.title,
    required this.description,
    required this.userSize,
    required this.conditionGrade,
    required this.wearCount,
    required this.isForSale,
    required this.primaryImageIndex,
    required this.images,
    required this.modelSourceImages,
    this.studType,
    this.siloName,
    this.releaseYear,
    this.surfaceType,
    this.clubName,
    this.season,
    this.league,
    this.kitType,
  });

  final int? catalogItemId;
  final String category;
  final String brand;
  final String? modelCode;
  final String title;
  final String description;
  final String userSize;
  final String conditionGrade;
  final int wearCount;
  final bool isForSale;
  final int primaryImageIndex;
  final List<XFile> images;
  final List<XFile> modelSourceImages;
  // 축구화 스펙
  final String? studType;
  final String? siloName;
  final String? releaseYear;
  final String? surfaceType;
  // 유니폼 스펙
  final String? clubName;
  final String? season;
  final String? league;
  final String? kitType;
}
