package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.port.in.DevLoginUseCase;
import com.gearshow.backend.user.application.port.out.AuthAccountPort;
import com.gearshow.backend.user.application.port.out.TokenIssuer;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.model.AuthAccount;
import com.gearshow.backend.user.domain.model.User;
import com.gearshow.backend.user.domain.vo.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발 환경 전용 로그인 서비스.
 *
 * <p>OAuth 없이 고정된 테스트 사용자로 JWT를 발급한다.
 * 테스트 사용자가 없으면 자동 생성하고, 있으면 기존 사용자로 로그인한다.</p>
 *
 * <p>{@code local}, {@code dev}, {@code test} 프로파일에서만 Bean 으로 등록된다.
 * 프로덕션 환경에는 절대 노출되어서는 안 된다.</p>
 */
@Profile({"local", "dev", "test"})
@Service
@RequiredArgsConstructor
public class DevLoginService implements DevLoginUseCase {

    private static final String DEV_PROVIDER_USER_KEY = "dev-test-user";
    private static final String DEV_NICKNAME = "개발자";

    private final UserPort userPort;
    private final AuthAccountPort authAccountPort;
    private final TokenIssuer tokenIssuer;

    @Override
    @Transactional
    public LoginResult devLogin() {
        User user = findOrCreateDevUser();
        return tokenIssuer.issue(user.getId());
    }

    @Override
    @Transactional
    public LoginResult devLoginAs(Long userId) {
        User user = userPort.findById(userId)
                .orElseGet(() -> {
                    User newUser = User.create("테스트유저_" + userId);
                    return userPort.save(newUser);
                });
        return tokenIssuer.issue(user.getId());
    }

    /**
     * 개발용 테스트 사용자를 조회하거나 생성한다.
     */
    private User findOrCreateDevUser() {
        return authAccountPort
                .findByProviderTypeAndProviderUserKey(ProviderType.KAKAO, DEV_PROVIDER_USER_KEY)
                .map(account -> userPort.findById(account.getUserId()).orElseThrow())
                .orElseGet(this::createDevUser);
    }

    /**
     * 개발용 테스트 사용자를 신규 생성한다.
     */
    private User createDevUser() {
        User user = User.create(DEV_NICKNAME);
        User saved = userPort.save(user);

        AuthAccount account = AuthAccount.create(
                saved.getId(), ProviderType.KAKAO, DEV_PROVIDER_USER_KEY);
        authAccountPort.save(account);

        return saved;
    }
}
