# Tripo API 레퍼런스 정리

> 원본: https://platform.tripo3d.ai/docs
> 정리일: 2026-04-16
> API 버전: v1.9.6 (2026-04-14 기준)

---

## 1. 개요

Tripo는 텍스트/이미지 입력을 고품질 3D 모델로 변환하는 API.
GearShow에서는 **사진 4장(앞/뒤/좌/우) → `multiview_to_model`** 방식으로 3D 모델을 생성한다.

### 서버 URL

```
https://api.tripo3d.ai/v2/openapi
```

### 인증

```
Authorization: Bearer YOUR_TRIPO_API_KEY
```

- API Key는 `tsk_`로 시작 (Client ID `tcli_`와 혼동 주의)
- 서버 사이드에서만 사용, 클라이언트 코드에 포함 금지

---

## 2. 응답 구조

### 성공

```json
{
  "code": 0,
  "data": {}
}
```

### 에러

```json
{
  "code": 2002,
  "message": "This task type is not supported",
  "suggestion": "Refer to API documentation for supported task types"
}
```

### Trace ID

모든 응답에 `X-Tripo-Trace-ID` 헤더 포함. 장애 추적용으로 DB에 저장 권장.

---

## 3. 태스크 상태 모델

### 상태 분류

| 분류 | 상태 | 설명 |
|---|---|---|
| Ongoing | `queued` | 처리 대기 중 (progress=0) |
| Ongoing | `running` | 처리 중 (progress 0~100) |
| Finalized | `success` | 완료. output 사용 가능 (progress=100) |
| Finalized | `failed` | 실패 (주로 Tripo 측 문제) |
| Finalized | `banned` | 콘텐츠 정책 위반 |
| Finalized | `expired` | 일정 기간 후 만료 |
| Finalized | `cancelled` | 취소됨 |
| Finalized | `unknown` | 시스템 수준 문제 |

### 과금 규칙

- `consumed_credit`: 실제 소모 크레딧
- **`failed` 시 `consumed_credit = 0`** — 성공 시에만 과금

### Task 응답 필드

| 필드 | 설명 |
|---|---|
| `task_id` | 고유 식별자 |
| `type` | 태스크 타입 |
| `status` | 상태 (위 표 참조) |
| `input` | 입력 데이터 (태스크 타입별 상이) |
| `output` | 결과 (model, base_model, pbr_model, rendered_image 등) |
| `output.consumed_credit` | 소모 크레딧 |
| `progress` | 진행률 (0~100) |
| `create_time` | 생성 시각 (Unix timestamp) |

### Output URL 만료

- Task API 문서: "expires after **five minutes**"
- FAQ: "download link is valid for **60 seconds**"
- 보수적으로 **60초 기준** 설계 권장. 만료 시 task 재조회로 새 URL 획득.

---

## 4. 동시성 제한 (Generation Rate Limit)

| Task Type | 동시 실행 수 |
|---|---|
| `generate_multiview_image` | **1** |
| `refine_model` | **5** |
| Other Task (image_to_model, multiview_to_model 등) | **10** |

- 각 태스크 타입별 **독립 카운트** (예: text_to_model 9개 + image_to_model 9개 동시 가능)
- 이미지 업로드: **10 QPS** 제한
- 초과 시 HTTP 429 + 에러 코드 2000 + `Retry-After` 헤더

---

## 5. 엔드포인트 상세

### 5.1 이미지 업로드

#### 일반 업로드

```
POST /upload
Content-Type: multipart/form-data
```

- 지원 파일: webp, jpeg, png (최대 10MB)
- 권장 해상도: 256px 이상
- 응답: `image_token`

#### STS 업로드 (권장)

```
POST /upload/sts/token
Content-Type: application/json
```

요청:
```json
{ "format": "jpeg" }
```

응답:
```json
{
  "s3_host": "...",
  "resource_bucket": "...",
  "resource_uri": "...",
  "session_token": "...",
  "sts_ak": "...",
  "sts_sk": "..."
}
```

STS 임시 자격증명으로 S3에 직접 업로드 후, `object: { bucket, key }` 형태로 참조.

---

### 5.2 3D 모델 생성

#### Multiview to Model (GearShow 사용)

```
POST /task
```

```json
{
  "type": "multiview_to_model",
  "files": [
    { "type": "jpg", "file_token": "front_token" },
    { "type": "jpg", "file_token": "left_token" },
    { "type": "jpg", "file_token": "back_token" },
    { "type": "jpg", "file_token": "right_token" }
  ]
}
```

- `files`: 정확히 4개, 순서는 [front, left, back, right]
- front는 필수, 나머지는 생략 가능 (최소 2장)
- `original_task_id`로 `generate_multiview_image`/`edit_multiview_image` 결과 참조 가능 (files와 상호 배타)

**주요 파라미터:**

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `model_version` | v2.5-20250123 | P1-20260311, v3.1-20260211, v3.0-20250812 등 |
| `texture` | true | 텍스처 생성 여부 |
| `pbr` | true | PBR 활성화 (true면 texture도 true) |
| `face_limit` | adaptive | 폴리곤 수 제한 |
| `texture_quality` | standard | standard / detailed |
| `texture_alignment` | original_image | original_image / geometry |
| `auto_size` | false | 실제 크기 자동 스케일 (미터 단위) |
| `orientation` | default | align_image로 원본 이미지 방향 정렬 |
| `quad` | false | 쿼드 메시 출력 (FBX 강제) |
| `geometry_quality` | standard | v3.0+: detailed(Ultra Mode, 최대 2M 폴리곤) |
| `export_uv` | true | false면 생성 속도 향상, 텍스처 단계에서 UV 처리 |

#### Image to Model

```json
{
  "type": "image_to_model",
  "file": { "type": "jpg", "file_token": "***" }
}
```

- 단일 이미지 → 3D 모델
- `enable_image_autofix`: 입력 이미지 자동 보정
- 동시 10개 (Other Task)

#### Text to Model

```json
{
  "type": "text_to_model",
  "prompt": "a small cat"
}
```

- 텍스트 프롬프트 → 3D 모델
- `prompt`: 최대 1024자 (~100단어), 다국어 지원
- `negative_prompt`: 역방향 지시 (최대 255자)

---

### 5.3 Multiview Image 생성/편집

#### Generate Multiview Image

```json
{
  "type": "generate_multiview_image",
  "file": { "type": "jpg", "file_token": "***" }
}
```

- 단일 이미지 → 4방향 뷰 이미지 생성 (front, left, back, right)
- **동시 1개 제한** — 병목 주의
- 출력: `front_view_url`, `left_view_url`, `back_view_url`, `right_view_url`

#### Edit Multiview Image

```json
{
  "type": "edit_multiview_image",
  "original_task_id": "...",
  "prompts": [{ "prompt": "모자 추가", "view": "front" }]
}
```

- 개별 뷰를 선택적으로 편집

---

### 5.4 텍스처

```json
{
  "type": "texture_model",
  "original_model_task_id": "..."
}
```

- 기존 모델에 텍스처 재생성/변경
- `texture_prompt`: text, image, images, style_image 지원
- `texture_quality: detailed` + `texture: false` + `pbr: false` → 4K 업스케일 (v3.0+)

---

### 5.5 후처리

#### Refine Model

```json
{
  "type": "refine_model",
  "draft_model_task_id": "..."
}
```

- 드래프트 모델 → 고해상도 모델
- v2.0 이상은 refine 미지원
- 처리 시간: ~2분 + 큐 대기

#### Stylization

```json
{
  "type": "stylize_model",
  "style": "lego",
  "original_model_task_id": "..."
}
```

- 스타일: `lego`, `voxel`, `voronoi`, `minecraft`

#### Conversion

```json
{
  "type": "convert_model",
  "format": "USDZ",
  "original_model_task_id": "..."
}
```

- 포맷: GLTF, USDZ, FBX, OBJ, STL, 3MF
- `quad`, `face_limit`, `scale_factor`, `texture_size`, `bake` 등 상세 옵션

---

### 5.6 Mesh Editing

| 태스크 | 설명 |
|---|---|
| `mesh_segmentation` | 모델을 파트별로 분리 |
| `mesh_completion` | 분리된 파트 보완 |
| `highpoly_to_lowpoly` | 고폴리 → 저폴리 변환 |

---

### 5.7 이미지 생성

#### Text to Image

```json
{ "type": "text_to_image", "prompt": "a small cat" }
```

#### Advanced Generate Image

```json
{
  "type": "generate_image",
  "prompt": "a small cat",
  "model_version": "flux.1_kontext_pro"
}
```

- 모델: flux.1_kontext_pro (기본), flux.1_dev, gpt_4o, gemini_2.5_flash_image_preview, z_image
- 다중 이미지 레퍼런스: `[image number]` 문법
- `t_pose`, `sketch_to_render` 옵션

---

### 5.8 기타

#### Import Model

```json
{
  "type": "import_model",
  "file": { "object": { "bucket": "tripo-data", "key": "***" } }
}
```

- 외부 모델 가져오기 (최대 150MB)
- 이후 후처리(texture, convert 등) 동일하게 사용 가능

#### Check Balance

```
GET /user/balance
```

```json
{ "balance": 100.0, "frozen": 5.0 }
```

---

## 6. 에러 코드 전체

### 공통 에러

| HTTP | 코드 | 설명 | 분류 |
|---|---|---|---|
| 500 | 1000 | 서버 알 수 없는 에러 | Retryable |
| 500 | 1001 | 서버 치명적 에러 | Retryable |
| 401 | 1002 | 인증 실패 | Non-retryable (긴급 Alert) |
| 400 | 1003 | 요청 본문 형식 오류 | Non-retryable |
| 400 | 1004 | 파라미터 유효하지 않음 | Non-retryable |
| 403 | 1005 | 리소스 접근 권한 없음 | Non-retryable |
| 429 | 1007 | 일반 Rate Limit 초과 | Retryable (대기 후) |
| 429 | 2000 | Generation Rate Limit 초과 | Retryable (`Retry-After` 헤더) |
| 404 | 2001 | 태스크 미발견 | Non-retryable |
| 400 | 2002 | 태스크 타입 미지원 | Non-retryable |
| 400 | 2003 | 이미지 파일 비어있음 | Non-retryable |
| 400 | 2004 | 이미지 파일 타입 미지원 | Non-retryable |
| 400 | 2005 | 드래프트 태스크가 성공 상태 아님 | Non-retryable |
| 400 | 2006 | 원본 태스크 타입 유효하지 않음 | Non-retryable |
| 400 | 2007 | 원본 태스크가 성공 상태 아님 | Non-retryable |
| 400 | 2008 | 콘텐츠 정책 위반 | Non-retryable |
| 400 | 2009 | 프롬프트에 잘못된 문자 | Non-retryable |
| 403 | 2010 | 크레딧 부족 | Non-retryable (Alert) |
| 400 | 2011 | prerigcheck에 모델 없음 | Non-retryable |
| 400 | 2012 | 입력 태스크 타입 유효하지 않음 | Non-retryable |
| 403 | 2013 | 우선순위 유효하지 않음 | Non-retryable |
| 500 | 2014 | 감사(audit) 서비스 에러 | Retryable |
| 400 | 2015 | 버전 deprecated | Non-retryable |
| 400 | 2016 | 요청 타입 deprecated | Non-retryable |
| 400 | 2017 | 버전 값 유효하지 않음 | Non-retryable |
| 400 | 2018 | 모델이 너무 복잡하여 리메시 불가 | Non-retryable |
| 404 | 2019 | 파일 미발견 | Non-retryable |
| 400 | 2020 | 이미지 URL 유효하지 않음 | Non-retryable |

---

## 7. 모델 버전 히스토리

| 버전 | 출시일 | 주요 특징 |
|---|---|---|
| P1-20260311 | 2026-03-11 | 저폴리 최적화, ~2초 메시 생성, 게임/AR/VR용 |
| v3.1-20260211 | 2026-02-11 | 고충실도, 최대 2M 폴리곤, 3D 프린팅용 |
| v3.0-20250812 | 2025-08-12 | Ultra Mode, 향상된 디테일/텍스처/PBR (**GearShow 현재 기본값**, 2026-04-30~) |
| Turbo-v1.0-20250506 | 2025-05-06 | 빠른 생성 속도 |
| v2.5-20250123 | 2025-01-23 | 향상된 지오메트리/텍스처 (이전 기본값, ~2026-04-30) |
| v2.0-20240919 | 2024-09-19 | 시드 기반 재현성 도입 |
| v1.4-20240625 | 2024-06-25 | 레거시 |

---

## 8. GearShow 설계 시 핵심 고려사항

### 사용 태스크 타입: `multiview_to_model`

- 동시 10개 가능 (Other Task 카테고리)
- 사진 4장 순서: [front, left, back, right]
- front 필수, 최소 2장

### 과금 모델

- **성공 시에만 과금** (`failed` → `consumed_credit = 0`)
- 설계 문서의 "taskId = 과금 확정" 전제는 과하게 보수적

### 폴링 vs 스트리밍

- 현재: 3초 주기 폴링
- Tripo 권장: 스트리밍(SSE) — 상세 문서 확인 필요

### 업로드 방식

- Tripo 권장: **STS 방식** (임시 자격증명 → S3 직접 업로드)
- `file_token` 방식보다 안정적

### 사전 크레딧 체크

- `GET /user/balance`로 Tripo 호출 전 잔액 확인 가능
- 부족 시 FAILED + Alert 처리

### Trace ID 저장

- 모든 응답의 `X-Tripo-Trace-ID` 헤더를 DB에 저장
- Tripo 측 장애 문의 시 필수

### Webhook 미지원

- Tripo는 콜백/웹훅을 **제거**하고 폴링으로 전환함 (v1.1.0)
- 능동적 알림 불가 — 반드시 폴링 또는 SSE로 상태 확인
