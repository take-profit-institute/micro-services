package org.profit.candle.auth.oauth;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.springframework.stereotype.Component;

/**
 * 요청이 넘긴 redirect_uri를 provider별 allowlist로 검증해 "토큰 교환에 사용할 redirect_uri"를 결정한다.
 *
 * <p>OAuth는 인가 단계와 토큰 교환 단계의 redirect_uri가 일치해야 하므로, 플랫폼(웹/네이티브)에 따라
 * 다른 redirect를 쓰려면 클라이언트가 교환 요청에 redirect_uri를 함께 보낸다. 임의 값 주입을 막기 위해
 * 설정된 기본값 + 허용목록에 포함된 값만 통과시킨다. 미지정이면 기본값(웹)을 쓴다.
 */
@Component
public class OAuthRedirectUriResolver {

    private record Policy(String defaultUri, Set<String> allowed) {
    }

    private final Map<String, Policy> policies;

    public OAuthRedirectUriResolver(AuthProperties properties) {
        this.policies = Map.of(
                "google", policy(properties.google().redirectUri(), properties.google().allowedRedirectUris()),
                "kakao", policy(properties.kakao().redirectUri(), properties.kakao().allowedRedirectUris()),
                "naver", policy(properties.naver().redirectUri(), properties.naver().allowedRedirectUris()));
    }

    private static Policy policy(String defaultUri, List<String> allowed) {
        Set<String> set = new HashSet<>();
        if (defaultUri != null && !defaultUri.isBlank()) {
            set.add(defaultUri);
        }
        if (allowed != null) {
            allowed.stream().filter(uri -> uri != null && !uri.isBlank()).forEach(set::add);
        }
        return new Policy(defaultUri, set);
    }

    /** 요청 redirect를 검증해 사용할 redirect를 반환. 미지정이면 기본값, 허용목록 밖이면 거부. */
    public String resolve(String provider, String requestedRedirectUri) {
        Policy policy = policies.get(provider);
        if (policy == null) {
            throw new AuthException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }
        if (requestedRedirectUri == null || requestedRedirectUri.isBlank()) {
            return policy.defaultUri();
        }
        if (!policy.allowed().contains(requestedRedirectUri)) {
            throw new AuthException(AuthErrorCode.OAUTH_REDIRECT_URI_NOT_ALLOWED);
        }
        return requestedRedirectUri;
    }
}
