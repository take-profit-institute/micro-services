package org.profit.candle.auth.oauth;

import java.util.List;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;

public final class OAuthRedirectUris {

    private OAuthRedirectUris() {
    }

    /**
     * 토큰 교환에 사용할 redirect_uri를 결정한다.
     *
     * <p>OAuth 2.0에서 토큰 교환의 redirect_uri는 인가 코드를 발급받을 때 쓴 값과 문자 그대로
     * 같아야 한다. 웹/앱(Capacitor)마다 콜백 URL이 다르므로 클라이언트가 보낸 값을 그대로 쓰되,
     * 임의 값 주입을 막기 위해 화이트리스트(configured + allowed)로만 허용한다.
     *
     * @param requested 클라이언트가 보낸 redirect_uri (없을 수 있음)
     * @param configured 서버 기본 redirect_uri (요청값이 없을 때의 폴백)
     * @param allowed 추가로 허용되는 redirect_uri 목록 (null 가능)
     * @return 실제 토큰 교환에 사용할 redirect_uri
     * @throws AuthException 요청값이 허용 목록에 없을 때
     */
    public static String resolve(String requested, String configured, List<String> allowed) {
        if (requested == null || requested.isBlank()) {
            return configured;
        }
        if (requested.equals(configured) || (allowed != null && allowed.contains(requested))) {
            return requested;
        }
        throw new AuthException(AuthErrorCode.INVALID_OAUTH_REQUEST);
    }
}
