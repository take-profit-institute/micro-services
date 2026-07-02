package org.profit.candle.common.error;

public enum CommonErrorCode implements ErrorCode {

    PAYLOAD_ENCRYPTION_FAILED(
            "COMMON_PAYLOAD_ENCRYPTION_FAILED", "데이터를 암호화하는 중 오류가 발생했습니다."),
    PAYLOAD_DECRYPTION_FAILED(
            "COMMON_PAYLOAD_DECRYPTION_FAILED", "데이터를 복호화하는 중 오류가 발생했습니다."),
    INVALID_ENCRYPTION_KEY(
            "COMMON_INVALID_ENCRYPTION_KEY", "암호화 키 설정이 올바르지 않습니다.");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
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
