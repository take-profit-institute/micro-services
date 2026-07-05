package org.profit.candle.auth.admin.entity;

/** SUPER_ADMIN만 관리자 계정 CRUD가 가능하고, ADMIN은 콘솔 사용만 가능하다. */
public enum AdminRole {
    ADMIN,
    SUPER_ADMIN
}
