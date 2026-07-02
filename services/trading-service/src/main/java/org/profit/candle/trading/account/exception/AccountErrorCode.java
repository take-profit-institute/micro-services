package org.profit.candle.trading.account.exception;

import org.profit.candle.common.error.ErrorCode;

/**
 * account 도메인이 소유하는 에러 코드와 사용자 노출 메시지.
 * HTTP/gRPC 상태 변환은 각 전송 계층(AccountGrpcService)에서 담당한다 — 컨벤션 8장.
 *
 * <p><b>임시 상태</b>: 도메인 예외 → gRPC Status 공통 변환 계층(인터셉터/AOP)이
 * 아직 support/에 없어, 지금은 AccountGrpcService가 switch문으로 직접 매핑한다.
 * 공통 계층이 생기면 그 switch문은 제거하고 위임 대상이 된다.</p>
 */
public enum AccountErrorCode implements ErrorCode {

    INVALID_LOCK_AMOUNT(
            "ACCOUNT_INVALID_LOCK_AMOUNT",
            "잠금 금액은 0보다 커야 합니다."
    ),
    INVALID_RELEASE_AMOUNT(
            "ACCOUNT_INVALID_RELEASE_AMOUNT",
            "반환 금액은 0보다 커야 합니다."
    ),
    INSUFFICIENT_LOCKED_BALANCE(
            "ACCOUNT_INSUFFICIENT_LOCKED_BALANCE",
            "반환 요청 금액이 잠긴 금액을 초과합니다."
    ),
    INSUFFICIENT_AVAILABLE_BALANCE(
            "ACCOUNT_INSUFFICIENT_AVAILABLE_BALANCE",
            "가용 가능 금액이 부족합니다."
    ),
    INSUFFICIENT_CASH_BALANCE(
            "ACCOUNT_INSUFFICIENT_CASH_BALANCE",
            "체결 금액이 현금 잔고를 초과합니다."
    ),
    ACCOUNT_NOT_FOUND(
            "ACCOUNT_NOT_FOUND",
            "계좌를 찾을 수 없습니다."
    ),
    ACCOUNT_INACTIVE(
            "ACCOUNT_INACTIVE",
            "비활성화된 계좌입니다."
    );

    private final String code;
    private final String message;

    AccountErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
