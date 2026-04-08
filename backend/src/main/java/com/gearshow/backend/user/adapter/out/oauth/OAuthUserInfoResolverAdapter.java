package com.gearshow.backend.user.adapter.out.oauth;

import java.util.List;

import org.springframework.stereotype.Component;

import com.gearshow.backend.user.application.dto.LoginCommand;
import com.gearshow.backend.user.application.dto.OAuthUserInfo;
import com.gearshow.backend.user.application.exception.InvalidAuthCodeException;
import com.gearshow.backend.user.application.port.out.OAuthClient;
import com.gearshow.backend.user.application.port.out.OAuthUserInfoResolver;
import com.gearshow.backend.user.domain.exception.UnsupportedProviderException;

import lombok.RequiredArgsConstructor;

/**
 * OAuth 사용자 정보 조회 어댑터.
 * 제공자별 OAuthClient 라우팅과 인증 방식 분기를 처리한다.
 */
@Component
@RequiredArgsConstructor
public class OAuthUserInfoResolverAdapter implements OAuthUserInfoResolver {

    private final List<OAuthClient> oAuthClients;

    @Override
    public OAuthUserInfo resolve(LoginCommand command) {
        OAuthClient client = findClient(command.provider());
        return getUserInfo(client, command);
    }

    private OAuthClient findClient(String provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(UnsupportedProviderException::new);
    }

    private OAuthUserInfo getUserInfo(OAuthClient client, LoginCommand command) {
        if (command.hasAccessToken()) {
            return client.getUserInfoByAccessToken(command.accessToken());
        }
        if (command.hasAuthorizationCode()) {
            return client.getUserInfo(command.authorizationCode());
        }
        throw new InvalidAuthCodeException();
    }
}
