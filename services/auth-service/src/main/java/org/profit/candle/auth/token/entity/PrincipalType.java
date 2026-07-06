package org.profit.candle.auth.token.entity;

/** refresh token이 어떤 주체(일반 사용자 / 관리자)에 속하는지 구분한다. */
public enum PrincipalType {
    USER,
    ADMIN
}
