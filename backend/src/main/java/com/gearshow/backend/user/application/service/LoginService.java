package com.gearshow.backend.user.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gearshow.backend.user.application.dto.LoginCommand;
import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.dto.OAuthUserInfo;
import com.gearshow.backend.user.application.port.in.LoginUseCase;
import com.gearshow.backend.user.application.port.out.AuthAccountPort;
import com.gearshow.backend.user.application.port.out.OAuthUserInfoResolver;
import com.gearshow.backend.user.application.port.out.TokenIssuer;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.model.AuthAccount;
import com.gearshow.backend.user.domain.model.User;
import com.gearshow.backend.user.domain.vo.ProviderType;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 유스케이스 구현체.
 * OAuth 사용자 정보 조회 → 사용자 조회/생성 → 토큰 발급 흐름을 제어한다.
 */
@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final OAuthUserInfoResolver oAuthUserInfoResolver;
    private final UserPort userPort;
    private final AuthAccountPort authAccountPort;
    private final TokenIssuer tokenIssuer;

    /**
     * 소셜 로그인을 수행한다.
     * 소셜 사용자 정보를 조회하고, 신규 사용자이면 자동 가입 후 토큰을 발급한다.
     */
    @Override
    @Transactional
    public LoginResult login(LoginCommand command) {
        OAuthUserInfo userInfo = oAuthUserInfoResolver.resolve(command);
        ProviderType providerType = ProviderType.from(command.provider());
        User user = findOrCreateUser(providerType, userInfo);
        return tokenIssuer.issue(user.getId());
    }

    /**
     * 기존 사용자를 조회하거나, 신규 사용자를 자동 생성한다.
     * 기존 사용자이면 최종 로그인 시간을 갱신한다.
     */
    private User findOrCreateUser(ProviderType providerType, OAuthUserInfo userInfo) {
        return authAccountPort
                .findByProviderTypeAndProviderUserKey(providerType, userInfo.providerUserKey())
                .map(this::updateLastLoginAndFindUser)
                .orElseGet(() -> registerNewUser(providerType, userInfo));
    }

    private User updateLastLoginAndFindUser(AuthAccount authAccount) {
        authAccountPort.save(authAccount.updateLastLogin());
        return userPort.findById(authAccount.getUserId()).orElseThrow();
    }

    private User registerNewUser(ProviderType providerType, OAuthUserInfo userInfo) {
        User savedUser = userPort.save(User.createWithTempNickname());

        AuthAccount authAccount = AuthAccount.create(
                savedUser.getId(), providerType, userInfo.providerUserKey());
        authAccountPort.save(authAccount);

        return savedUser;
    }
}
