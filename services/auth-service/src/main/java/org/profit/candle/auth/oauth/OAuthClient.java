package org.profit.candle.auth.oauth;

public interface OAuthClient {
    String provider();

    /**
     * @param authorizationCode provider가 redirect로 넘긴 인가 코드
     * @param state CSRF 방지용 state. 프론트가 생성/검증하며, 토큰 교환에 state가 필요한
     *              provider(naver)만 사용한다. 그 외 provider는 무시한다.
     * @param redirectUri 인가 코드 발급에 사용한 redirect_uri. 토큰 교환 시 발급 때와 반드시
     *                    일치해야 한다. null/blank면 서버 기본값으로 폴백한다.
     */
    OAuthProfile fetch(String authorizationCode, String state, String redirectUri);
}
