package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.UpdateProfileCommand;
import com.gearshow.backend.user.application.dto.UpdateProfileResult;
import com.gearshow.backend.user.application.exception.DuplicateNicknameException;
import com.gearshow.backend.user.application.port.in.UpdateProfileUseCase;
import com.gearshow.backend.user.application.port.out.ProfileImageStoragePort;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.exception.NotFoundUserException;
import com.gearshow.backend.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 프로필 수정 유스케이스 구현체.
 * 닉네임 변경, 프로필 이미지 업로드/교체를 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProfileService implements UpdateProfileUseCase {

    private static final String PROFILE_DIRECTORY = "profiles";

    private final UserPort userPort;
    private final ProfileImageStoragePort profileImageStoragePort;

    @Override
    @Transactional
    public UpdateProfileResult updateProfile(Long userId, UpdateProfileCommand command) {
        User user = userPort.findById(userId)
                .orElseThrow(NotFoundUserException::new);

        validateNicknameDuplicate(user, command.nickname());

        String profileImageUrl = resolveProfileImageUrl(user, command);

        User updated = user.updateProfile(command.nickname(), profileImageUrl);
        User saved = userPort.save(updated);
        return UpdateProfileResult.from(saved);
    }

    /**
     * 프로필 이미지 URL을 결정한다.
     * 새 이미지가 있으면 업로드하고 기존 이미지를 삭제한다.
     * 새 이미지가 없으면 기존 URL을 유지한다.
     */
    private String resolveProfileImageUrl(User user, UpdateProfileCommand command) {
        if (!command.hasImage()) {
            return user.getProfileImageUrl();
        }

        // 새 이미지 업로드
        String s3Key = generateKey(command.imageFilename());
        profileImageStoragePort.upload(s3Key, command.imageContent(), command.imageContentType());

        // 기존 이미지 삭제
        deleteOldImage(user.getProfileImageUrl());

        return profileImageStoragePort.toUrl(s3Key);
    }

    /**
     * 기존 프로필 이미지를 S3에서 삭제한다.
     * 기존 이미지가 없으면 아무것도 하지 않는다.
     */
    private void deleteOldImage(String oldImageUrl) {
        String oldKey = profileImageStoragePort.extractKey(oldImageUrl);
        if (oldKey != null) {
            profileImageStoragePort.delete(oldKey);
            log.debug("기존 프로필 이미지 삭제 완료: key={}", oldKey);
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

    /**
     * 닉네임 변경 시 중복 여부를 확인한다.
     * 본인의 기존 닉네임과 동일하면 중복 검사를 건너뛴다.
     */
    private void validateNicknameDuplicate(User user, String nickname) {
        if (nickname == null || nickname.equals(user.getNickname())) {
            return;
        }
        if (userPort.existsByNickname(nickname)) {
            throw new DuplicateNicknameException();
        }
    }
}
