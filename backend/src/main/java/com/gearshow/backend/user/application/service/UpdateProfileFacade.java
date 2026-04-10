package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.UpdateProfileCommand;
import com.gearshow.backend.user.application.dto.UpdateProfileResult;
import com.gearshow.backend.user.application.port.in.UpdateProfileUseCase;
import com.gearshow.backend.user.application.port.out.ProfileImageStoragePort;
import com.gearshow.backend.user.domain.policy.ProfileImagePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 프로필 수정 Facade.
 *
 * <p>S3 업로드/삭제 같은 외부 I/O를 트랜잭션 밖에서 수행하고,
 * DB 작업만 {@link UpdateProfileService}에 위임하여 트랜잭션 범위를 최소화한다.</p>
 *
 * <p>처리 순서:</p>
 * <ol>
 *     <li>이미지 검증 (정책)</li>
 *     <li>S3 업로드 (트랜잭션 밖)</li>
 *     <li>DB 갱신 (트랜잭션 안) — 기존 이미지 URL 반환</li>
 *     <li>커밋 성공 시 기존 이미지 S3 삭제</li>
 *     <li>실패 시 신규 업로드 보상 삭제</li>
 * </ol>
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class UpdateProfileFacade implements UpdateProfileUseCase {

    private static final String PROFILE_DIRECTORY = "profiles";

    private final UpdateProfileService updateProfileService;
    private final ProfileImageStoragePort profileImageStoragePort;

    @Override
    public UpdateProfileResult updateProfile(Long userId, UpdateProfileCommand command) {
        // 1. 이미지 검증 (트랜잭션 밖)
        ProfileImagePolicy.validate(command.imageContent(), command.imageContentType());

        // 2. 새 이미지 업로드 (트랜잭션 밖)
        String newImageUrl = uploadIfPresent(command);
        String newKey = newImageUrl != null
                ? profileImageStoragePort.extractKey(newImageUrl)
                : null;

        try {
            // 3. DB 갱신 (트랜잭션) — 기존 이미지 URL을 함께 반환받는다
            UpdateProfileService.UpdateProfileTxResult txResult =
                    updateProfileService.updateProfileTx(userId, command.nickname(), newImageUrl);

            // 4. 커밋 성공 후에만 기존 이미지 삭제 (S3에 고아 파일 방지)
            if (newImageUrl != null) {
                deleteOldImageSafely(txResult.oldImageUrl());
            }

            return txResult.result();
        } catch (Exception e) {
            // 5. DB 갱신 실패 시 방금 업로드한 신규 이미지 보상 삭제
            if (newKey != null) {
                deleteUploadedImageSafely(newKey);
            }
            throw e;
        }
    }

    /**
     * 새 이미지가 있으면 S3에 업로드하고 CDN URL을 반환한다.
     */
    private String uploadIfPresent(UpdateProfileCommand command) {
        if (!command.hasImage()) {
            return null;
        }
        String s3Key = generateKey(command.imageFilename());
        profileImageStoragePort.upload(s3Key, command.imageContent(), command.imageContentType());
        return profileImageStoragePort.toUrl(s3Key);
    }

    /**
     * 기존 이미지를 안전하게 삭제한다. 삭제 실패는 로그만 남기고 무시한다.
     * 트랜잭션은 이미 커밋되었으므로 예외가 발생해도 사용자 응답에 영향을 주지 않는다.
     */
    private void deleteOldImageSafely(String oldImageUrl) {
        if (oldImageUrl == null || oldImageUrl.isBlank()) {
            return;
        }
        String oldKey = profileImageStoragePort.extractKey(oldImageUrl);
        if (oldKey == null) {
            return;
        }
        try {
            profileImageStoragePort.delete(oldKey);
        } catch (Exception e) {
            log.warn("기존 프로필 이미지 삭제 실패 (커밋 후): key={}", oldKey, e);
        }
    }

    /**
     * DB 갱신 실패 시 방금 업로드한 신규 이미지를 보상 삭제한다.
     */
    private void deleteUploadedImageSafely(String newKey) {
        try {
            profileImageStoragePort.delete(newKey);
            log.info("DB 갱신 실패로 신규 이미지 보상 삭제 완료: key={}", newKey);
        } catch (Exception e) {
            log.error("신규 이미지 보상 삭제 실패: key={}", newKey, e);
        }
    }

    /**
     * UUID 기반 고유 S3 키를 생성한다.
     */
    private String generateKey(String filename) {
        String extension = extractExtension(filename);
        return PROFILE_DIRECTORY + "/" + UUID.randomUUID() + extension;
    }

    /**
     * 파일명에서 확장자를 추출한다.
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
