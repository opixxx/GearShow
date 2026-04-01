package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.UpdateProfileCommand;
import com.gearshow.backend.user.application.dto.UpdateProfileResult;
import com.gearshow.backend.user.application.exception.DuplicateNicknameException;
import com.gearshow.backend.user.application.port.in.UpdateProfileUseCase;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.exception.NotFoundUserException;
import com.gearshow.backend.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 수정 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class UpdateProfileService implements UpdateProfileUseCase {

    private final UserPort userPort;

    @Override
    @Transactional
    public UpdateProfileResult updateProfile(Long userId, UpdateProfileCommand command) {
        User user = userPort.findById(userId)
                .orElseThrow(NotFoundUserException::new);

        validateNicknameDuplicate(user, command.nickname());

        User updated = user.updateProfile(command.nickname(), command.profileImageUrl());
        User saved = userPort.save(updated);
        return UpdateProfileResult.from(saved);
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
