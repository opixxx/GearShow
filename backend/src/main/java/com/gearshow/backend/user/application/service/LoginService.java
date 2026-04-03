package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.LoginCommand;
import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.dto.OAuthUserInfo;
import com.gearshow.backend.user.application.exception.InvalidAuthCodeException;
import com.gearshow.backend.user.application.exception.UnsupportedProviderException;
import com.gearshow.backend.user.application.port.in.LoginUseCase;
import com.gearshow.backend.user.application.port.out.AuthAccountPort;
import com.gearshow.backend.user.application.port.out.OAuthClient;
import com.gearshow.backend.user.application.port.out.RefreshTokenPort;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.model.AuthAccount;
import com.gearshow.backend.user.domain.model.User;
import com.gearshow.backend.user.domain.vo.ProviderType;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 소셜 로그인 유스케이스 구현체.
 * 인가 코드로 소셜 사용자 정보를 조회하고, 신규 사용자이면 자동 가입 후 JWT를 발급한다.
 */
@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final List<OAuthClient> oAuthClients;
    private final UserPort userPort;
    private final AuthAccountPort authAccountPort;
    private final RefreshTokenPort refreshTokenPort;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public LoginResult login(LoginCommand command) {
        OAuthClient client = findOAuthClient(command.provider());
        OAuthUserInfo userInfo = getOAuthUserInfo(client, command);

        ProviderType providerType = resolveProviderType(command.provider());
        User user = findOrCreateUser(providerType, userInfo);

        return generateTokens(user.getId());
    }

    /**
     * 제공자 이름에 맞는 OAuthClient를 찾는다.
     */
    private OAuthClient findOAuthClient(String provider) {
        return oAuthClients.stream()
                .filter(client -> client.getProvider().equals(provider))
                .findFirst()
                .orElseThrow(UnsupportedProviderException::new);
    }

    private OAuthUserInfo getOAuthUserInfo(OAuthClient client, LoginCommand command) {
        if (command.accessToken() != null && !command.accessToken().isBlank()) {
            return client.getUserInfoByAccessToken(command.accessToken());
        }
        if (command.authorizationCode() != null && !command.authorizationCode().isBlank()) {
            return client.getUserInfo(command.authorizationCode());
        }
        throw new InvalidAuthCodeException();
    }

    /**
     * 기존 사용자를 조회하거나, 신규 사용자를 자동 생성한다.
     */
    private User findOrCreateUser(ProviderType providerType, OAuthUserInfo userInfo) {
        return authAccountPort
                .findByProviderTypeAndProviderUserKey(providerType, userInfo.providerUserKey())
                .map(authAccount -> {
                    authAccountPort.save(authAccount.updateLastLogin());
                    return userPort.findById(authAccount.getUserId()).orElseThrow();
                })
                .orElseGet(() -> createNewUser(providerType, userInfo));
    }

    /**
     * 신규 사용자와 인증 계정을 생성한다.
     */
    private User createNewUser(ProviderType providerType, OAuthUserInfo userInfo) {
        String nickname = generateUniqueNickname(userInfo.nickname());
        User newUser = User.create(nickname);
        User savedUser = userPort.save(newUser);

        AuthAccount authAccount = AuthAccount.create(
                savedUser.getId(), providerType, userInfo.providerUserKey());
        authAccountPort.save(authAccount);

        return savedUser;
    }

    /**
     * 신규 가입 시 임시 닉네임을 생성한다.
     * 카카오 등 소셜 닉네임은 사용하지 않고, 가입 후 사용자가 직접 설정하도록 한다.
     * "사용자_"로 시작하는 닉네임은 닉네임 미설정 상태를 의미한다.
     */
    private String generateUniqueNickname(String nickname) {
        return "사용자_" + System.currentTimeMillis();
    }

    /**
     * Access Token과 Refresh Token을 생성하고 DB에 저장한다.
     */
    private LoginResult generateTokens(Long userId) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        // 기존 Refresh Token 삭제 후 새로 저장
        refreshTokenPort.deleteByUserId(userId);
        refreshTokenPort.save(userId, refreshToken,
                Instant.now().plus(Duration.ofDays(14)));

        return new LoginResult(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds()
        );
    }

    /**
     * 문자열 제공자명을 ProviderType enum으로 변환한다.
     */
    private ProviderType resolveProviderType(String provider) {
        return switch (provider.toLowerCase()) {
            case "kakao" -> ProviderType.KAKAO;
            case "apple" -> ProviderType.APPLE;
            case "google" -> ProviderType.GOOGLE;
            default -> throw new UnsupportedProviderException();
        };
    }
}
