package com.gearshow.backend.support;

import com.gearshow.backend.user.application.dto.OAuthUserInfo;
import com.gearshow.backend.user.application.exception.InvalidAuthCodeException;
import com.gearshow.backend.user.application.port.out.OAuthClient;

/**
 * 테스트용 OAuth 클라이언트 스텁.
 * 실제 카카오/애플 API를 호출하지 않고, 인가 코드에 따라 고정된 사용자 정보를 반환한다.
 *
 * <p>인가 코드 규칙:</p>
 * <ul>
 *   <li>"valid-code" → 정상 사용자 정보 반환</li>
 *   <li>"valid-code-{id}" → 커스텀 providerUserKey 반환</li>
 *   <li>그 외 → InvalidAuthCodeException 발생</li>
 * </ul>
 */
public class StubOAuthClient implements OAuthClient {

    private final String provider;

    public StubOAuthClient(String provider) {
        this.provider = provider;
    }

    @Override
    public OAuthUserInfo getUserInfo(String authorizationCode) {
        if (authorizationCode.equals("valid-code")) {
            return new OAuthUserInfo("provider-user-123", "테스트유저", "https://example.com/profile.jpg");
        }

        if (authorizationCode.startsWith("valid-code-")) {
            String id = authorizationCode.substring("valid-code-".length());
            return new OAuthUserInfo("provider-user-" + id, "테스트유저_" + id, null);
        }

        throw new InvalidAuthCodeException();
    }

    @Override
    public String getProvider() {
        return provider;
    }
}
