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
    "content": [ ... ],
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "size": 20,
    "hasNext": true
  }
}
```

> - `nextCursor`: 다음 페이지 조회를 위한 커서 값 (Base64 인코딩). 마지막 페이지이면 `null`
> - 첫 페이지 요청 시 `cursor` 파라미터를 생략한다.

### 공통 쿼리 파라미터 (페이징)

| 파라미터 | 타입 | 기본값 | 설명 |
|:--------|:-----|:------|:-----|
| cursor | string | | 이전 응답의 `nextCursor` 값 (첫 페이지는 생략) |
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
    "content": [
      {
        "catalogItemId": 1,
        "category": "BOOTS",
        "brand": "Nike",
        "itemName": "Mercurial Superfly 10 Elite",
        "modelCode": "DJ2839-001",
        "officialImageUrl": "https://cdn.gearshow.com/catalogs/1.jpg"
      }
    ],
    "nextCursor": "eyJpZCI6MjB9",
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
| category | string | N | 카테고리 필터 (`BOOTS`, `UNIFORM`) |
| brand | string | N | 브랜드 필터 |
| keyword | string | N | 제목 검색 |
| isForSale | boolean | N | 판매 여부 필터 |
| conditionGrade | string | N | 상태 등급 필터 (`S`, `A`, `B`, `C`) |
| cursor | string | N | 커서 값 |
| size | int | N | 페이지 크기 |

**Response** `200 OK`
```json
{
  "status": 200,
  "message": "Showcases retrieved successfully",
  "data": {
    "content": [
      {
        "showcaseId": 1,
        "title": "머큐리얼 슈퍼플라이 10 엘리트 착용 후기",
        "ownerNickname": "축구매니아",
        "primaryImageUrl": "https://cdn.gearshow.com/showcases/1/primary.jpg",
        "conditionGrade": "A",
        "isForSale": true,
        "wearCount": 5,
        "commentCount": 12,
        "has3dModel": true,
        "createdAt": "2026-03-20T14:30:00"
      }
    ],
    "nextCursor": "eyJpZCI6MjB9",
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
    "catalogItem": {
      "catalogItemId": 1,
      "brand": "Nike",
      "itemName": "Mercurial Superfly 10 Elite",
      "category": "BOOTS"
    },
    "title": "머큐리얼 슈퍼플라이 10 엘리트 착용 후기",
    "description": "FG 천연잔디에서 5번 착용했습니다. 경량성이 뛰어나고...",
    "userSize": "270",
    "conditionGrade": "A",
    "wearCount": 5,
    "isForSale": true,
    "showcaseStatus": "ACTIVE",
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
| catalogItemId | long | Y | 카탈로그 아이템 ID |
| title | string | Y | 제목 |
| description | string | N | 상세 설명 |
| userSize | string | N | 사용자 사이즈 |
| conditionGrade | string | Y | 상태 등급 (`S`, `A`, `B`, `C`) |
| wearCount | int | N | 착용 횟수 (기본값: 0) |
| isForSale | boolean | N | 판매 여부 (기본값: false) |
| images | MultipartFile[] | Y | 일반 이미지 파일 (최소 1개) |
| primaryImageIndex | int | N | 대표 이미지 인덱스 (기본값: 0) |
| modelSourceImages | MultipartFile[] | N | 3D 모델 생성용 이미지 (앞/뒤/좌/우, 기본 4장) |

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
    "failureReason": "Insufficient image quality"
  }
}
```

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
    "content": [
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
    "nextCursor": "eyJpZCI6NX0=",
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
