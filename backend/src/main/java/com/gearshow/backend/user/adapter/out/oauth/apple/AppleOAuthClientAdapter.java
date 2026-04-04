package com.gearshow.backend.user.adapter.out.oauth.apple;

import com.gearshow.backend.user.application.dto.OAuthUserInfo;
import com.gearshow.backend.user.application.exception.InvalidAuthCodeException;
import com.gearshow.backend.user.application.port.out.OAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

/**
 * 애플 OAuth 클라이언트 구현체.
 * 애플 인가 코드로 ID Token을 발급받고 사용자 정보를 추출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppleOAuthClientAdapter implements OAuthClient {

    @Value("${oauth.apple.client-id}")
    private String clientId;

    @Value("${oauth.apple.token-uri}")
    private String tokenUri;

    @Value("${oauth.apple.redirect-uri}")
    private String redirectUri;

    private final RestTemplate restTemplate;

    @Override
    public OAuthUserInfo getUserInfo(String authorizationCode) {
        Map<String, Object> tokenResponse = requestToken(authorizationCode);
        return extractUserInfoFromIdToken(tokenResponse);
    }

    /**
     * 애플은 SDK 액세스 토큰 기반 사용자 정보 조회를 지원하지 않는다.
     * 애플 로그인은 반드시 인가 코드(authorization code) 방식을 사용해야 한다.
     */
    @Override
    public OAuthUserInfo getUserInfoByAccessToken(String accessToken) {
        throw new InvalidAuthCodeException();
    }

    @Override
    public String getProvider() {
        return "apple";
    }

    /**
     * 인가 코드로 애플 토큰을 발급받는다.
     */
    private Map<String, Object> requestToken(String authorizationCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("client_secret", generateClientSecret());
        body.add("redirect_uri", redirectUri);
        body.add("code", authorizationCode);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUri, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("id_token")) {
                throw new InvalidAuthCodeException();
            }
            return responseBody;
        } catch (RestClientException e) {
            log.error("애플 토큰 발급 실패: code={}", authorizationCode, e);
            throw new InvalidAuthCodeException();
        }
    }

    /**
     * ID Token의 payload에서 사용자 정보를 추출한다.
     */
    @SuppressWarnings("unchecked")
    private OAuthUserInfo extractUserInfoFromIdToken(Map<String, Object> tokenResponse) {
        String idToken = (String) tokenResponse.get("id_token");
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new InvalidAuthCodeException();
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        // ID Token payload에서 sub(사용자 고유 ID)를 추출
        // 실제 구현에서는 JSON 파싱 라이브러리 사용
        String sub = extractClaimFromPayload(payload, "sub");
        String email = extractClaimFromPayload(payload, "email");

        return new OAuthUserInfo(sub, email, null);
    }

    /**
     * JWT payload에서 특정 클레임 값을 추출한다.
     * TODO: 프로덕션에서는 ObjectMapper를 사용하여 파싱
     */
    private String extractClaimFromPayload(String payload, String claim) {
        int startIndex = payload.indexOf("\"" + claim + "\"");
        if (startIndex == -1) {
            return null;
        }
        int valueStart = payload.indexOf("\"", startIndex + claim.length() + 3) + 1;
        int valueEnd = payload.indexOf("\"", valueStart);
        if (valueStart <= 0 || valueEnd <= 0) {
            return null;
        }
        return payload.substring(valueStart, valueEnd);
    }

    /**
     * 애플 Client Secret (JWT)을 생성한다.
     * TODO: 실제 구현 시 Apple Private Key로 서명된 JWT 생성 필요
     */
    private String generateClientSecret() {
        // API 키 설정 후 구현 예정
        return "apple-client-secret-placeholder";
    }
}
