# GearShow API 명세

---

## 공통 사항

### Base URL

```
/api/v1
```

### 인증 방식

- Spring Security OAuth2 Client + JWT (Access Token / Refresh Token)
- Access Token은 `Authorization: Bearer {token}` 헤더로 전달
- 인증이 필요한 API에는 🔒 표시

### 응답 언어 정책

> API 응답의 `message`, `errorCode`, `failureReason` 등 모든 문자열은 **한글**로 작성한다.

### 공통 응답 형식

**성공 응답**
```json
{
  "status": 200,
  "message": "Success message",
  "data": { ... }
}
```

**에러 응답**
```json
{
  "status": 400,
  "code": "SHOWCASE_NOT_FOUND",
  "message": "Showcase not found",
  "data": null
}
```

### 공통 커서 페이징 응답

```json
{
  "status": 200,
  "message": "Retrieved successfully",
  "data": {
    "pageToken": "eyJpZCI6MTAwfQ==",
    "data": [ ... ],
    "size": 20,
    "hasNext": true
  }
}
```

> - `pageToken`: 다음 페이지 조회를 위한 커서 값 (Base64 인코딩). 마지막 페이지이면 `null`
> - 첫 페이지 요청 시 `pageToken` 파라미터를 생략한다.

### 공통 쿼리 파라미터 (페이징)

| 파라미터 | 타입 | 기본값 | 설명 |
|:--------|:-----|:------|:-----|
| pageToken | string | | 이전 응답의 `pageToken` 값 (첫 페이지는 생략) |
| size | int | 20 | 페이지 크기 |

---

## 1. AUTH (인증)

### 1-1. 소셜 로그인

```
POST /api/v1/auth/login/{provider}
```

소셜 로그인 인가 코드를 전달받아 JWT를 발급한다.

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|:--------|:-----|:-----|
| provider | string | 소셜 로그인 제공자 (`kakao`, `google`, `apple`) |

**Request Body**
```json
{
  "authorizationCode": "소셜 인가 코드"
}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

### 1-2. 토큰 갱신

```
POST /api/v1/auth/refresh
```

Refresh Token으로 새로운 Access Token을 발급한다.

**Request Body**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

### 1-3. 로그아웃 🔒

```
POST /api/v1/auth/logout
```

현재 토큰을 무효화한다.

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Logout successful",
  "data": null
}
```

---

## 2. USER (사용자)

### 2-1. 내 프로필 조회 🔒

```
GET /api/v1/users/me
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Profile retrieved successfully",
  "data": {
    "userId": 1,
    "nickname": "축구매니아",
    "profileImageUrl": "https://cdn.gearshow.com/profiles/1.jpg",
    "phoneNumber": "010-1234-5678",
    "isPhoneVerified": true,
    "userStatus": "ACTIVE",
    "createdAt": "2026-03-01T10:00:00"
  }
}
```

### 2-2. 프로필 수정 🔒

```
PATCH /api/v1/users/me
```

**Request Body**
```json
{
  "nickname": "새닉네임",
  "profileImageUrl": "https://cdn.gearshow.com/profiles/new.jpg"
}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Profile updated successfully",
  "data": {
    "userId": 1,
    "nickname": "새닉네임",
    "profileImageUrl": "https://cdn.gearshow.com/profiles/new.jpg"
  }
}
```

### 2-3. 휴대폰 인증 요청 🔒

```
POST /api/v1/users/me/phone/verification
```

**Request Body**
```json
{
  "phoneNumber": "010-1234-5678"
}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Verification code sent successfully",
  "data": {
    "expiresIn": 180
  }
}
```

### 2-4. 휴대폰 인증 확인 🔒

```
POST /api/v1/users/me/phone/verification/confirm
```

**Request Body**
```json
{
  "phoneNumber": "010-1234-5678",
  "verificationCode": "123456"
}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Phone verified successfully",
  "data": {
    "isPhoneVerified": true
  }
}
```

### 2-5. 회원 탈퇴 🔒

```
DELETE /api/v1/users/me
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Account deleted successfully",
  "data": null
}
```

### 2-6. 다른 사용자 프로필 조회

```
GET /api/v1/users/{userId}
```

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|:--------|:-----|:-----|
| userId | long | 사용자 ID |

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Profile retrieved successfully",
  "data": {
    "userId": 2,
    "nickname": "풋살러",
    "profileImageUrl": "https://cdn.gearshow.com/profiles/2.jpg"
  }
}
```

---

## 3. CATALOG (카탈로그)

### 3-1. 카탈로그 아이템 목록 조회

```
GET /api/v1/catalogs
```

**Query Parameter**

| 파라미터 | 타입 | 필수 | 설명 |
|:--------|:-----|:----|:-----|
| category | string | N | 카테고리 필터 (`BOOTS`, `UNIFORM`) |
| brand | string | N | 브랜드 필터 |
| keyword | string | N | 아이템명/모델코드 검색 |
| cursor | string | N | 커서 값 |
| size | int | N | 페이지 크기 |

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Catalog items retrieved successfully",
  "data": {
    "pageToken": "eyJpZCI6MjB9",
    "data": [
      {
        "catalogItemId": 1,
        "category": "BOOTS",
        "brand": "Nike",
        "itemName": "Mercurial Superfly 10 Elite",
        "modelCode": "DJ2839-001",
        "officialImageUrl": "https://cdn.gearshow.com/catalogs/1.jpg"
      }
    ],
    "size": 20,
    "hasNext": true
  }
}
```

### 3-2. 카탈로그 아이템 상세 조회

```
GET /api/v1/catalogs/{catalogItemId}
```

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|:--------|:-----|:-----|
| catalogItemId | long | 카탈로그 아이템 ID |

**Response** `200 OK` (축구화인 경우)
```json
{
  "status": 200,
  "message": "Catalog item retrieved successfully",
  "data": {
    "catalogItemId": 1,
    "category": "BOOTS",
    "brand": "Nike",
    "itemName": "Mercurial Superfly 10 Elite",
    "modelCode": "DJ2839-001",
    "officialImageUrl": "https://cdn.gearshow.com/catalogs/1.jpg",
    "catalogStatus": "ACTIVE",
    "bootsSpec": {
      "studType": "FG",
      "siloName": "Mercurial",
      "releaseYear": "2025",
      "surfaceType": "천연잔디",
      "extraSpecJson": {
        "weight": "185g",
        "upperMaterial": "Vaporposite+"
      }
    },
    "uniformSpec": null,
    "createdAt": "2026-01-15T09:00:00"
  }
}
```

### 3-3. 카탈로그 아이템 등록 🔒

```
POST /api/v1/catalogs
```

> 관리자 또는 인증된 사용자가 새 카탈로그 아이템을 등록한다.

**Request Body**
```json
{
  "category": "BOOTS",
  "brand": "Nike",
  "itemName": "Mercurial Superfly 10 Elite",
  "modelCode": "DJ2839-001",
  "officialImageUrl": "https://cdn.gearshow.com/catalogs/1.jpg",
  "bootsSpec": {
    "studType": "FG",
    "siloName": "Mercurial",
    "releaseYear": "2025",
    "surfaceType": "천연잔디",
    "extraSpecJson": {
      "weight": "185g",
      "upperMaterial": "Vaporposite+"
    }
  }
}
```

**Response** `201 Created`
```json
{
  "status": 201,
  "message": "Catalog item created successfully",
  "data": {
    "catalogItemId": 1
  }
}
```

---

## 4. SHOWCASE (쇼케이스)

### 이미지 업로드 구분

쇼케이스 등록 시 이미지는 두 종류로 구분된다.

| 구분 | 필드명 | 필수 | 설명 |
|:----|:------|:----|:-----|
| 일반 이미지 | `images` | Y (최소 1개) | 사용자가 자유롭게 촬영한 쇼케이스 이미지 |
| 3D 모델용 이미지 | `modelSourceImages` | N | 3D 모델 생성에 필요한 이미지 (앞/뒤/좌/우, 기본 4장) |

> - `modelSourceImages`가 포함되면 쇼케이스 등록과 동시에 3D 모델 생성을 비동기로 요청한다.
> - `modelSourceImages`가 없으면 일반 이미지만으로 쇼케이스를 등록한다.

### 4-1. 쇼케이스 목록 조회

```
GET /api/v1/showcases
```

**Query Parameter**

| 파라미터 | 타입 | 필수 | 설명 |
|:--------|:-----|:----|:-----|
| pageToken | string | N | 커서 기반 페이지 토큰 |
| size | int | N | 페이지 크기 (기본 20, 최대 100) |

> **참고**: 필터링(카테고리, 브랜드, 키워드 등)은 추후 Elasticsearch 도입 시 별도 검색 API로 제공 예정

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "쇼케이스 목록 조회 성공",
  "data": {
    "pageToken": "eyJpZCI6MjB9",
    "data": [
      {
        "showcaseId": 1,
        "title": "머큐리얼 슈퍼플라이 10 엘리트 착용 후기",
        "category": "BOOTS",
        "brand": "Nike",
        "userSize": "275",
        "primaryImageUrl": "https://cdn.gearshow.com/showcases/1/primary.jpg",
        "conditionGrade": "A",
        "isForSale": true,
        "wearCount": 5,
        "commentCount": 12,
        "has3dModel": true,
        "spec": {
          "studType": "FG",
          "siloName": "Mercurial",
          "surfaceType": "천연잔디"
        },
        "createdAt": "2026-03-20T14:30:00"
      }
    ],
    "size": 20,
    "hasNext": true
  }
}
```

### 4-2. 쇼케이스 상세 조회

```
GET /api/v1/showcases/{showcaseId}
```

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|:--------|:-----|:-----|
| showcaseId | long | 쇼케이스 ID |

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Showcase retrieved successfully",
  "data": {
    "showcaseId": 1,
    "owner": {
      "userId": 1,
      "nickname": "축구매니아",
      "profileImageUrl": "https://cdn.gearshow.com/profiles/1.jpg"
    },
    "category": "BOOTS",
    "brand": "Nike",
    "modelCode": "DJ2839-XXX",
    "catalogItem": null,
    "title": "머큐리얼 슈퍼플라이 10 엘리트 착용 후기",
    "description": "FG 천연잔디에서 5번 착용했습니다. 경량성이 뛰어나고...",
    "userSize": "270",
    "conditionGrade": "A",
    "wearCount": 5,
    "isForSale": true,
    "showcaseStatus": "ACTIVE",
    "spec": {
      "specType": "BOOTS",
      "specData": "{\"studType\":\"FG\",\"siloName\":\"Mercurial\",\"releaseYear\":\"2025\",\"surfaceType\":\"천연잔디\"}"
    },
    "images": [
      {
        "showcaseImageId": 1,
        "imageUrl": "https://cdn.gearshow.com/showcases/1/img1.jpg",
        "sortOrder": 1,
        "isPrimary": true
      },
      {
        "showcaseImageId": 2,
        "imageUrl": "https://cdn.gearshow.com/showcases/1/img2.jpg",
        "sortOrder": 2,
        "isPrimary": false
      }
    ],
    "model3d": {
      "showcase3dModelId": 1,
      "modelFileUrl": "https://cdn.gearshow.com/showcases/1/model.glb",
      "previewImageUrl": "https://cdn.gearshow.com/showcases/1/preview.jpg",
      "modelStatus": "COMPLETED"
    },
    "createdAt": "2026-03-20T14:30:00",
    "updatedAt": "2026-03-20T14:30:00"
  }
}
```

### 4-3. 쇼케이스 등록 🔒

```
POST /api/v1/showcases
```

**Request Body** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|:----|:-----|:----|:-----|
| catalogItemId | long | N | 카탈로그 아이템 ID (선택, 연결 시 category/brand/modelCode 자동 복사) |
| category | string | Y | 카테고리 (`BOOTS`, `UNIFORM`) — catalogItemId 제공 시 자동 설정 |
| brand | string | Y | 브랜드명 — catalogItemId 제공 시 자동 설정 |
| modelCode | string | N | 모델 코드 — catalogItemId 제공 시 자동 설정 |
| title | string | Y | 제목 |
| description | string | N | 상세 설명 |
| userSize | string | N | 사용자 사이즈 |
| conditionGrade | string | Y | 상태 등급 (`S`, `A`, `B`, `C`) |
| wearCount | int | N | 착용 횟수 (기본값: 0) |
| isForSale | boolean | N | 판매 여부 (기본값: false) |
| images | MultipartFile[] | Y | 일반 이미지 파일 (최소 1개) |
| primaryImageIndex | int | N | 대표 이미지 인덱스 (기본값: 0) |
| modelSourceImages | MultipartFile[] | N | 3D 모델 생성용 이미지 (앞/뒤/좌/우, 기본 4장) |
| spec.studType | string | N | (BOOTS) 스터드 타입 (FG, SG, AG, TF, IC) |
| spec.siloName | string | N | (BOOTS) 사일로명 |
| spec.releaseYear | string | N | (BOOTS) 출시 연도 |
| spec.surfaceType | string | N | (BOOTS) 적합 표면 |
| spec.clubName | string | N | (UNIFORM) 클럽명 |
| spec.season | string | N | (UNIFORM) 시즌 |
| spec.league | string | N | (UNIFORM) 리그 |
| spec.kitType | string | N | (UNIFORM) 킷 타입 (HOME, AWAY, THIRD) |

> `modelSourceImages`가 포함되면 쇼케이스 등록 후 3D 모델 생성을 비동기로 요청한다.

**Response** `201 Created` (3D 모델 생성 요청 포함)
```json
{
  "status": 201,
  "message": "Showcase created successfully",
  "data": {
    "showcaseId": 1,
    "model3dStatus": "REQUESTED"
  }
}
```

**Response** `201 Created` (일반 이미지만 등록)
```json
{
  "status": 201,
  "message": "Showcase created successfully",
  "data": {
    "showcaseId": 1,
    "model3dStatus": null
  }
}
```

### 4-4. 쇼케이스 수정 🔒

```
PATCH /api/v1/showcases/{showcaseId}
```

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|:--------|:-----|:-----|
| showcaseId | long | 쇼케이스 ID |

**Request Body**
```json
{
  "title": "수정된 제목",
  "description": "수정된 설명",
  "userSize": "275",
  "conditionGrade": "B",
  "wearCount": 10,
  "isForSale": false
}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Showcase updated successfully",
  "data": {
    "showcaseId": 1
  }
}
```

### 4-5. 쇼케이스 삭제 🔒

```
DELETE /api/v1/showcases/{showcaseId}
```

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|:--------|:-----|:-----|
| showcaseId | long | 쇼케이스 ID |

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Showcase deleted successfully",
  "data": null
}
```

### 4-6. 내 쇼케이스 목록 조회 🔒

```
GET /api/v1/users/me/showcases
```

**Query Parameter**

| 파라미터 | 타입 | 필수 | 설명 |
|:--------|:-----|:----|:-----|
| showcaseStatus | string | N | 상태 필터 (`ACTIVE`, `HIDDEN`) |
| cursor | string | N | 커서 값 |
| size | int | N | 페이지 크기 |

**Response** `200 OK` — 4-1 목록 응답과 동일한 구조

---

## 5. SHOWCASE IMAGE (쇼케이스 이미지)

### 5-1. 이미지 추가 🔒

```
POST /api/v1/showcases/{showcaseId}/images
```

**Request Body** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|:----|:-----|:----|:-----|
| images | MultipartFile[] | Y | 추가할 이미지 파일 |

**Response** `201 Created`
```json
{
  "status": 201,
  "message": "Images added successfully",
  "data": {
    "addedImageIds": [3, 4]
  }
}
```

### 5-2. 이미지 삭제 🔒

```
DELETE /api/v1/showcases/{showcaseId}/images/{showcaseImageId}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Image deleted successfully",
  "data": null
}
```

### 5-3. 이미지 정렬 순서 변경 🔒

```
PATCH /api/v1/showcases/{showcaseId}/images/order
```

**Request Body**
```json
{
  "imageOrders": [
    { "showcaseImageId": 2, "sortOrder": 1, "isPrimary": true },
    { "showcaseImageId": 1, "sortOrder": 2, "isPrimary": false },
    { "showcaseImageId": 3, "sortOrder": 3, "isPrimary": false }
  ]
}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Image order updated successfully",
  "data": null
}
```

---

## 6. SHOWCASE 3D MODEL (쇼케이스 3D 모델)

> 3D 모델 생성은 쇼케이스 등록 시 `modelSourceImages`를 함께 업로드하면 비동기로 자동 요청된다.
> 아래 API는 등록 이후 상태 확인 및 재요청을 위한 것이다.

### 6-1. 3D 모델 생성 재요청 🔒

```
POST /api/v1/showcases/{showcaseId}/3d-model
```

기존 3D 모델 생성이 실패했거나, 새로운 소스 이미지로 재생성을 요청한다.

**Request Body** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|:----|:-----|:----|:-----|
| modelSourceImages | MultipartFile[] | Y | 3D 모델 생성용 이미지 (앞/뒤/좌/우, 기본 4장) |

**Response** `202 Accepted`
```json
{
  "status": 202,
  "message": "3D model generation requested",
  "data": {
    "showcase3dModelId": 1,
    "modelStatus": "REQUESTED"
  }
}
```

### 6-2. 3D 모델 상태 조회

```
GET /api/v1/showcases/{showcaseId}/3d-model
```

**Response** `200 OK` (생성 완료)
```json
{
  "status": 200,
  "message": "3D model retrieved successfully",
  "data": {
    "showcase3dModelId": 1,
    "modelFileUrl": "https://cdn.gearshow.com/showcases/1/model.glb",
    "previewImageUrl": "https://cdn.gearshow.com/showcases/1/preview.jpg",
    "modelStatus": "COMPLETED",
    "generationProvider": "tripo",
    "sourceImageCount": 4,
    "requestedAt": "2026-03-20T14:35:00",
    "generatedAt": "2026-03-20T14:37:00"
  }
}
```

**Response** `200 OK` (생성 중)
```json
{
  "status": 200,
  "message": "3D model retrieved successfully",
  "data": {
    "showcase3dModelId": 1,
    "modelFileUrl": null,
    "previewImageUrl": null,
    "modelStatus": "GENERATING",
    "generationProvider": "tripo",
    "sourceImageCount": 4,
    "requestedAt": "2026-03-20T14:35:00",
    "generatedAt": null
  }
}
```

**Response** `200 OK` (생성 준비 중 — Worker 가 잡고 Tripo 호출 준비)
```json
{
  "status": 200,
  "message": "3D model retrieved successfully",
  "data": {
    "showcase3dModelId": 1,
    "modelFileUrl": null,
    "previewImageUrl": null,
    "modelStatus": "PREPARING",
    "generationProvider": "tripo",
    "sourceImageCount": 4,
    "requestedAt": "2026-03-20T14:35:00",
    "generatedAt": null
  }
}
```

**Response** `200 OK` (생성 실패)
```json
{
  "status": 200,
  "message": "3D model retrieved successfully",
  "data": {
    "showcase3dModelId": 1,
    "modelFileUrl": null,
    "previewImageUrl": null,
    "modelStatus": "FAILED",
    "generationProvider": "tripo",
    "sourceImageCount": 4,
    "requestedAt": "2026-03-20T14:35:00",
    "generatedAt": null,
    "failureReason": "Tripo 크레딧이 부족합니다. 크레딧 충전이 필요합니다"
  }
}
```

**Response** `200 OK` (서비스 일시 이용 불가 — Circuit Breaker OPEN)
```json
{
  "status": 200,
  "message": "3D model retrieved successfully",
  "data": {
    "showcase3dModelId": 1,
    "modelFileUrl": null,
    "previewImageUrl": null,
    "modelStatus": "UNAVAILABLE",
    "generationProvider": "tripo",
    "sourceImageCount": 4,
    "requestedAt": "2026-03-20T14:35:00",
    "generatedAt": null,
    "failureReason": "3D 생성 서비스가 일시적으로 이용 불가합니다"
  }
}
```

> **modelStatus 전체 값**: `REQUESTED`, `PREPARING`, `GENERATING`, `COMPLETED`, `FAILED`, `UNAVAILABLE`

---

## 7. SHOWCASE COMMENT (쇼케이스 댓글)

### 7-1. 댓글 목록 조회

```
GET /api/v1/showcases/{showcaseId}/comments
```

**Query Parameter**

| 파라미터 | 타입 | 필수 | 설명 |
|:--------|:-----|:----|:-----|
| cursor | string | N | 커서 값 |
| size | int | N | 페이지 크기 |

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Comments retrieved successfully",
  "data": {
    "pageToken": "eyJpZCI6NX0=",
    "data": [
      {
        "showcaseCommentId": 1,
        "author": {
          "userId": 2,
          "nickname": "풋살러",
          "profileImageUrl": "https://cdn.gearshow.com/profiles/2.jpg"
        },
        "content": "사이즈 정사이즈인가요?",
        "createdAt": "2026-03-21T10:00:00"
      }
    ],
    "size": 20,
    "hasNext": false
  }
}
```

### 7-2. 댓글 작성 🔒

```
POST /api/v1/showcases/{showcaseId}/comments
```

**Request Body**
```json
{
  "content": "사이즈 정사이즈인가요?"
}
```

**Response** `201 Created`
```json
{
  "status": 201,
  "message": "Comment created successfully",
  "data": {
    "showcaseCommentId": 1
  }
}
```

### 7-3. 댓글 수정 🔒

```
PATCH /api/v1/showcases/{showcaseId}/comments/{commentId}
```

**Request Body**
```json
{
  "content": "수정된 댓글 내용"
}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Comment updated successfully",
  "data": {
    "showcaseCommentId": 1
  }
}
```

### 7-4. 댓글 삭제 🔒

```
DELETE /api/v1/showcases/{showcaseId}/comments/{commentId}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Comment deleted successfully",
  "data": null
}
```

---

## 8. CHAT (채팅)

> 1:1 채팅. 쇼케이스 단위 + 판매자-구매자 쌍으로 채팅방이 존재한다.
> 자세한 규칙: `docs/business/biz-logic.md §7`
> 아키텍처 결정: `docs/architecture/adr/ADR-005`

### 8-1. 채팅방 목록 조회 🔒

자신이 참여 중인 모든 채팅방을 최신 활동 순으로 조회한다.

```
GET /api/v1/chat-rooms?size=20&cursor={pageToken}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "items": [
      {
        "chatRoomId": 1,
        "showcaseId": 42,
        "showcaseTitle": "프레데터 24 FG 280mm",
        "showcaseThumbnailUrl": "https://cdn.../thumb.jpg",
        "peer": {
          "userId": 7,
          "nickname": "boots_lover",
          "profileImageUrl": "https://cdn.../p.jpg"
        },
        "lastMessage": {
          "content": "네, 괜찮습니다.",
          "messageType": "TEXT",
          "sentAt": "2026-04-15T14:30:00Z"
        },
        "unreadCount": 3,
        "chatRoomStatus": "ACTIVE"
      }
    ],
    "pageInfo": {
      "hasNext": true,
      "nextCursor": "eyJpZCI6MSwidHMiOi4uLn0="
    }
  }
}
```

### 8-2. 채팅방 상세 조회 🔒

```
GET /api/v1/chat-rooms/{chatRoomId}
```

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "chatRoomId": 1,
    "showcaseId": 42,
    "seller": { "userId": 5, "nickname": "seller", "profileImageUrl": "..." },
    "buyer":  { "userId": 7, "nickname": "buyer",  "profileImageUrl": "..." },
    "chatRoomStatus": "ACTIVE",
    "createdAt": "2026-04-10T09:00:00Z",
    "lastMessageAt": "2026-04-15T14:30:00Z"
  }
}
```

**에러**:
- `403 FORBIDDEN_CHAT_ROOM_ACCESS` — 참여자가 아님

### 8-3. 채팅방 생성 또는 조회 🔒

쇼케이스 상세 "채팅하기" 버튼의 진입점. 기존 채팅방이 있으면 그것을 반환하고, 없으면 새로 생성한다.

```
POST /api/v1/chat-rooms
```

**Request Body**
```json
{
  "showcaseId": 42
}
```

**Response** `200 OK` (기존 채팅방 반환) 또는 `201 Created` (신규 생성)
```json
{
  "status": 201,
  "message": "Chat room created",
  "data": {
    "chatRoomId": 1
  }
}
```

**에러**:
- `400 CHAT_ROOM_OWN_SHOWCASE` — 자신의 쇼케이스에 채팅 시도
- `400 CHAT_ROOM_SHOWCASE_NOT_AVAILABLE` — 쇼케이스가 `DELETED`/`SOLD`

### 8-4. 메시지 목록 조회 🔒

채팅방 히스토리를 커서 기반으로 조회한다. 오래된 메시지부터 최신 방향으로.

```
GET /api/v1/chat-rooms/{chatRoomId}/messages?size=50&before={messageId}
```

**쿼리 파라미터**:
- `size` — 페이지 크기 (기본 50, 최대 200)
- `before` — 해당 messageId 이전 메시지 조회 (무한 스크롤용)

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "items": [
      {
        "chatMessageId": 1001,
        "senderId": 7,
        "seq": 1,
        "messageType": "TEXT",
        "content": "안녕하세요, 가격 조정 가능할까요?",
        "payloadJson": null,
        "messageStatus": "ACTIVE",
        "sentAt": "2026-04-10T09:01:00Z"
      },
      {
        "chatMessageId": 1005,
        "senderId": null,
        "seq": 5,
        "messageType": "SYSTEM_TICKET_ISSUED",
        "content": "안전거래 요청이 발급되었습니다",
        "payloadJson": { "ticketId": "abc-123-def" },
        "messageStatus": "ACTIVE",
        "sentAt": "2026-04-10T09:05:00Z"
      }
    ],
    "pageInfo": {
      "hasNext": true,
      "nextCursor": "bmV4dA=="
    }
  }
}
```

### 8-5. 메시지 전송 🔒 (HTTP)

실시간 WebSocket 경로가 있지만, HTTP fallback으로도 송신 가능.

```
POST /api/v1/chat-rooms/{chatRoomId}/messages
```

**Request Body**
```json
{
  "messageType": "TEXT",
  "content": "네, 괜찮습니다.",
  "clientMessageId": "uuid-멱등성-키"
}
```

**응답** `201 Created`
```json
{
  "status": 201,
  "message": "Message sent",
  "data": {
    "chatMessageId": 1010,
    "seq": 10,
    "sentAt": "2026-04-15T14:30:00Z"
  }
}
```

**에러**:
- `400 CHAT_MESSAGE_TOO_LONG` — 2,000자 초과
- `403 CHAT_ROOM_CLOSED` — 채팅방 CLOSED 상태
- `409 DUPLICATE_CLIENT_MESSAGE_ID` — 동일 clientMessageId 재시도 (응답은 기존 메시지 반환)

### 8-6. WebSocket 연결 (STOMP) 🔒

```
WebSocket endpoint: /ws
STOMP handshake: CONNECT
구독 대상: /topic/chat-rooms/{chatRoomId}
발신 대상: /app/chat-rooms/{chatRoomId}/send
```

**구독 메시지 스키마** (서버 → 클라이언트):
```json
{
  "type": "MESSAGE",
  "payload": {
    "chatMessageId": 1010,
    "chatRoomId": 1,
    "senderId": 7,
    "seq": 10,
    "messageType": "TEXT",
    "content": "네, 괜찮습니다.",
    "payloadJson": null,
    "sentAt": "2026-04-15T14:30:00Z"
  }
}
```

**송신 메시지 스키마** (클라이언트 → 서버):
```json
{
  "messageType": "TEXT",
  "content": "안녕하세요",
  "clientMessageId": "uuid"
}
```

**재연결 동기화**: 클라이언트는 연결 성립 시 `GET /api/v1/chat-rooms/{id}/messages?since_seq=N` 로 누락 메시지 delta 조회.

### 8-7. 읽음 처리 🔒

채팅방 진입 시 호출. 해당 시점까지의 모든 메시지를 읽음 처리.

```
POST /api/v1/chat-rooms/{chatRoomId}/read
```

**Request Body**
```json
{
  "lastReadMessageId": 1010
}
```

**응답** `200 OK`
```json
{
  "status": 200,
  "message": "Read marker updated",
  "data": null
}
```

### 8-8. 메시지 삭제 🔒 (soft delete)

자신이 보낸 메시지만 삭제 가능. 시스템 메시지는 삭제 불가.

```
DELETE /api/v1/chat-rooms/{chatRoomId}/messages/{messageId}
```

**응답** `200 OK` — 메시지는 `DELETED` 상태로 전환되며 목록 조회 시 "삭제된 메시지입니다" 플레이스홀더로 응답.

**에러**:
- `403 CHAT_MESSAGE_NOT_OWNER` — 본인 메시지가 아님
- `400 CHAT_MESSAGE_SYSTEM_UNDELETABLE` — 시스템 메시지

---

## 9. TRANSACTION TICKET (거래 티켓)

> 채팅방·쇼케이스 상세 등에서 거래 요청 시 발급되는 1회용 티켓.
> 티켓이 소비되면 `TRANSACTION`이 생성된다.
> 자세한 규칙: `docs/business/biz-logic.md §7-6`
> 아키텍처 결정: `docs/architecture/adr/ADR-006`

### 9-1. 티켓 발급 🔒

```
POST /api/v1/transaction-tickets
```

**Request Body**
```json
{
  "contextType": "SHOWCASE_ESCROW",
  "contextRefId": 42,
  "paymentMethod": "ESCROW"
}
```

**응답** `201 Created`
```json
{
  "status": 201,
  "message": "Ticket issued",
  "data": {
    "ticketId": "9b3e-...-uuid",
    "contextType": "SHOWCASE_ESCROW",
    "contextRefId": 42,
    "amount": 120000,
    "sellerId": 5,
    "buyerId": 7,
    "paymentMethod": "ESCROW",
    "expiresAt": "2026-04-15T15:30:00Z",
    "ticketStatus": "ISSUED"
  }
}
```

**에러**:
- `400 TICKET_INVALID_CONTEXT` — 맥락 대상 없음/판매 불가
- `400 TICKET_NOT_PHONE_VERIFIED` — 휴대폰 인증 미완 (거래 선행 조건)
- `409 TICKET_CONCURRENT_ACTIVE` — 동일 맥락 진행 중 티켓 존재

### 9-2. 티켓 조회 🔒

```
GET /api/v1/transaction-tickets/{ticketId}
```

### 9-3. 티켓 취소 🔒 (발급자만)

```
POST /api/v1/transaction-tickets/{ticketId}/cancel
```

**에러**:
- `403 TICKET_NOT_ISSUER` — 발급자가 아님
- `409 TICKET_NOT_CANCELLABLE` — 이미 USED/EXPIRED/CANCELLED

### 9-4. 티켓 소비 (거래 생성) 🔒

상대방이 티켓을 수락하여 실제 거래 생성. 원자적 `UPDATE`로 멱등성 보장.

```
POST /api/v1/transactions
```

**Request Body**
```json
{
  "ticketId": "9b3e-...-uuid"
}
```

**응답** `201 Created`
```json
{
  "status": 201,
  "message": "Transaction created",
  "data": {
    "transactionId": 1,
    "ticketId": "9b3e-...-uuid",
    "transactionStatus": "PENDING"
  }
}
```

**에러**:
- `409 TICKET_ALREADY_USED` — 이미 소비된 티켓
- `410 TICKET_EXPIRED` — 만료
- `403 TICKET_NOT_TARGET` — 티켓 수락 대상이 아님 (상대방이 아님)

---

## 에러 코드 목록

> 모든 에러 메시지는 한글로 작성한다.

### 공통

| 코드 | HTTP Status | 메시지 |
|:----|:-----------|:------|
| INVALID_INPUT | 400 | 잘못된 입력입니다 |
| UNAUTHORIZED | 401 | 인증이 필요합니다 |
| FORBIDDEN | 403 | 접근이 거부되었습니다 |
| INVALID_CURSOR | 400 | 유효하지 않은 커서 값입니다 |
| INTERNAL_ERROR | 500 | 서버 내부 오류가 발생했습니다 |

### AUTH

| 코드 | HTTP Status | 메시지 |
|:----|:-----------|:------|
| AUTH_INVALID_CODE | 400 | 유효하지 않은 인가 코드입니다 |
| AUTH_EXPIRED_TOKEN | 401 | 토큰이 만료되었습니다 |
| AUTH_INVALID_TOKEN | 401 | 유효하지 않은 토큰입니다 |

### USER

| 코드 | HTTP Status | 메시지 |
|:----|:-----------|:------|
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다 |
| USER_DUPLICATE_NICKNAME | 400 | 이미 사용 중인 닉네임입니다 |
| USER_INVALID_VERIFICATION_CODE | 400 | 유효하지 않은 인증 코드입니다 |
| USER_EXPIRED_VERIFICATION_CODE | 400 | 인증 코드가 만료되었습니다 |

### CATALOG

| 코드 | HTTP Status | 메시지 |
|:----|:-----------|:------|
| CATALOG_ITEM_NOT_FOUND | 404 | 카탈로그 아이템을 찾을 수 없습니다 |
| CATALOG_DUPLICATE_MODEL_CODE | 400 | 이미 존재하는 모델 코드입니다 |

### SHOWCASE

| 코드 | HTTP Status | 메시지 |
|:----|:-----------|:------|
| SHOWCASE_NOT_FOUND | 404 | 쇼케이스를 찾을 수 없습니다 |
| SHOWCASE_NOT_OWNER | 403 | 쇼케이스 소유자만 수정 또는 삭제할 수 있습니다 |
| SHOWCASE_MIN_IMAGE_REQUIRED | 400 | 최소 1개의 이미지가 필요합니다 |
| SHOWCASE_INSUFFICIENT_MODEL_SOURCE_IMAGES | 400 | 3D 모델 생성을 위해 최소 4개의 이미지가 필요합니다 |
| SHOWCASE_MODEL_ALREADY_GENERATING | 400 | 3D 모델이 이미 생성 중입니다 |
| SHOWCASE_MODEL_GENERATION_FAILED | 500 | 3D 모델 생성에 실패했습니다 |

### SHOWCASE COMMENT

| 코드 | HTTP Status | 메시지 |
|:----|:-----------|:------|
| COMMENT_NOT_FOUND | 404 | 댓글을 찾을 수 없습니다 |
| COMMENT_NOT_AUTHOR | 403 | 댓글 작성자만 수정 또는 삭제할 수 있습니다 |
