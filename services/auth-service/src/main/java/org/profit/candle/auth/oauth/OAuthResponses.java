package org.profit.candle.auth.oauth;

import java.util.Map;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;

/**
 * OAuth provider 응답(JSON Map)에서 필수 필드를 안전하게 추출하기 위한 헬퍼.
 * 응답 본문이 null이거나 필수 필드가 누락/공백이면 {@code failure} 에러로 변환해
 * NPE/500 및 "null" 문자열 subject 생성을 방지한다.
 */
public final class OAuthResponses {

    private OAuthResponses() {
    }

    /** 응답 본문이 null이면 예외, 아니면 그대로 반환한다. */
    public static Map<?, ?> requireBody(Map<?, ?> body, AuthErrorCode failure) {
        if (body == null) {
            throw new AuthException(failure);
        }
        return body;
    }

    /** 응답 본문에서 필수 문자열 필드를 추출한다. 누락/공백이면 예외를 던진다. */
    public static String requireString(Map<?, ?> body, String key, AuthErrorCode failure) {
        Object value = requireBody(body, failure).get(key);
        if (value == null || value.toString().isBlank()) {
            throw new AuthException(failure);
        }
        return value.toString();
    }
}
