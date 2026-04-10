package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.UpdateProfileResult;
import com.gearshow.backend.user.application.exception.DuplicateNicknameException;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.exception.NotFoundUserException;
import com.gearshow.backend.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 수정 DB 작업 전용 서비스.
 *
 * <p>외부 I/O(S3 업로드/삭제)는 {@link UpdateProfileFacade}에서 트랜잭션 외부로 분리되어 처리되며,
 * 이 서비스는 트랜잭션 안에서 닉네임 중복 검증 + 도메인 모델 갱신만 수행한다.</p>
 */
@Service
@RequiredArgsConstructor
public class UpdateProfileService {

    private final UserPort userPort;

    /**
     * 프로필을 갱신하고 기존 이미지 URL을 반환한다.
     * 반환된 기존 URL은 Facade에서 트랜잭션 커밋 후 S3 삭제 처리에 사용된다.
     *
     * @param userId         사용자 ID
     * @param nickname       변경할 닉네임 (null이면 변경하지 않음)
     * @param newImageUrl    새 프로필 이미지 URL (null이면 변경하지 않음)
     * @return 갱신 결과와 기존 이미지 URL을 포함한 객체
     */
    @Transactional
    public UpdateProfileTxResult updateProfileTx(Long userId, String nickname, String newImageUrl) {
        User user = userPort.findById(userId)
                .orElseThrow(NotFoundUserException::new);

        validateNicknameDuplicate(user, nickname);

        String oldImageUrl = user.getProfileImageUrl();
        String resolvedImageUrl = newImageUrl != null ? newImageUrl : oldImageUrl;

        User updated = user.updateProfile(nickname, resolvedImageUrl);
        User saved = userPort.save(updated);

        return new UpdateProfileTxResult(UpdateProfileResult.from(saved), oldImageUrl);
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

    /**
     * 트랜잭션 내부 처리 결과.
     *
     * @param result      클라이언트에 반환할 결과
     * @param oldImageUrl 트랜잭션 커밋 후 삭제할 기존 이미지 URL (없으면 null)
     */
    public record UpdateProfileTxResult(UpdateProfileResult result, String oldImageUrl) {
    }
}
