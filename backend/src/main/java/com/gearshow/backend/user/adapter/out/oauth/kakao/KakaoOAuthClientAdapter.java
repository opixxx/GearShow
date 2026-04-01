package com.gearshow.backend.user.adapter.out.oauth.kakao;

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

import java.util.Map;

/**
 * 카카오 OAuth 클라이언트 구현체.
 * 카카오 인가 코드로 액세스 토큰을 발급받고 사용자 정보를 조회한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClientAdapter implements OAuthClient {

    @Value("${oauth.kakao.client-id}")
    private String clientId;

    @Value("${oauth.kakao.client-secret}")
    private String clientSecret;

    @Value("${oauth.kakao.redirect-uri}")
    private String redirectUri;

    @Value("${oauth.kakao.token-uri}")
    private String tokenUri;

    @Value("${oauth.kakao.user-info-uri}")
    private String userInfoUri;

    private final RestTemplate restTemplate;

    @Override
    public OAuthUserInfo getUserInfo(String authorizationCode) {
        String accessToken = requestAccessToken(authorizationCode);
        return requestUserInfo(accessToken);
    }

    @Override
    public String getProvider() {
        return "kakao";
    }

    /**
     * 인가 코드로 카카오 액세스 토큰을 발급받는다.
     */
    private String requestAccessToken(String authorizationCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("code", authorizationCode);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUri, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("access_token")) {
                throw new InvalidAuthCodeException();
            }
            return (String) responseBody.get("access_token");
        } catch (RestClientException e) {
            log.error("카카오 토큰 발급 실패", e);
            throw new InvalidAuthCodeException();
        }
    }

    /**
     * 카카오 액세스 토큰으로 사용자 정보를 조회한다.
     */
    @SuppressWarnings("unchecked")
    private OAuthUserInfo requestUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userInfoUri, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new InvalidAuthCodeException();
            }

            String providerUserKey = String.valueOf(responseBody.get("id"));
            String nickname = extractNickname(responseBody);
            String profileImageUrl = extractProfileImage(responseBody);

            return new OAuthUserInfo(providerUserKey, nickname, profileImageUrl);
        } catch (RestClientException e) {
            log.error("카카오 사용자 정보 조회 실패", e);
            throw new InvalidAuthCodeException();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractNickname(Map<String, Object> responseBody) {
        Map<String, Object> properties = (Map<String, Object>) responseBody.get("properties");
        if (properties != null) {
            return (String) properties.get("nickname");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractProfileImage(Map<String, Object> responseBody) {
        Map<String, Object> properties = (Map<String, Object>) responseBody.get("properties");
        if (properties != null) {
            return (String) properties.get("profile_image");
        }
        return null;
    }
}
