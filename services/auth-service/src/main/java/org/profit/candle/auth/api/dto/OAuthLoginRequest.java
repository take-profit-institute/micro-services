package org.profit.candle.auth.api.dto;

/**
 * @param authorizationCode provider가 redirect로 넘긴 인가 코드
 * @param state CSRF 방지용 state (naver 토큰 교환에 필요)
 * @param redirectUri 프론트가 인가 코드를 발급받을 때 사용한 redirect_uri.
 *                    토큰 교환 시 이 값이 발급 때와 반드시 일치해야 하므로 그대로 전달받는다.
 *                    웹/앱(Capacitor)마다 콜백 URL이 달라 클라이언트가 보내며,
 *                    서버는 화이트리스트로 검증한 뒤 사용한다. 없으면 서버 기본값으로 폴백한다.
 */
public record OAuthLoginRequest(String authorizationCode, String state, String redirectUri) {
}
