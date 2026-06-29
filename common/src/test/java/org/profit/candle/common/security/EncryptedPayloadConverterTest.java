package org.profit.candle.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.common.error.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EncryptedPayloadConverter 단위 테스트.
 *
 * 키는 정적 홀더(EncryptedPayloadConverter.initKey)를 통해 설정한다 — 실제
 * 운영에서도 각 서비스가 부팅 시 한 번 이 방식으로 키를 주입하기 때문이다.
 * 정적 상태이므로 각 테스트 전후에 초기화/정리한다.
 *
 * Spring/JPA 컨텍스트는 띄우지 않고 순수 암복호화 로직만 검증한다.
 * (@Convert가 실제 엔티티에 연결되는지는 별도의 @DataJpaTest에서 확인한다.)
 */
class EncryptedPayloadConverterTest {

    // 테스트 전용 키. 운영 키와 무관하며, 코드에 노출돼도 문제없다.
    private static final String TEST_KEY_BASE64 =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private final EncryptedPayloadConverter converter = new EncryptedPayloadConverter();

    @BeforeEach
    void setUpKey() {
        EncryptedPayloadConverter.initKey(TEST_KEY_BASE64);
    }

    @AfterEach
    void clearKey() {
        // 정적 상태를 다음 테스트 클래스/실행에 흘리지 않도록 리셋한다.
        EncryptedPayloadConverter.resetKeyForTest();
    }

    @Test
    @DisplayName("암호화 후 복호화하면 원본 평문이 그대로 나온다")
    void roundTrip_returnsOriginalPlainText() {
        byte[] original = "order placed: AAPL x10".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = converter.convertToDatabaseColumn(original);
        byte[] decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("같은 평문을 두 번 암호화해도 결과(암호문)는 매번 다르다 (IV가 랜덤이기 때문)")
    void encrypt_sameInputTwice_producesDifferentCipherText() {
        byte[] original = "same payload".getBytes(StandardCharsets.UTF_8);

        byte[] encryptedFirst = converter.convertToDatabaseColumn(original);
        byte[] encryptedSecond = converter.convertToDatabaseColumn(original);

        assertThat(encryptedFirst).isNotEqualTo(encryptedSecond);
    }

    @Test
    @DisplayName("암호화된 값이 저장 중 한 바이트라도 조작되면 복호화 시 예외가 발생한다")
    void decrypt_tamperedData_throwsException() {
        byte[] original = "sensitive response".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = converter.convertToDatabaseColumn(original);

        // 암호문 영역의 마지막 바이트를 조작 (앞 12바이트는 IV이므로 그 뒤를 변경)
        encrypted[encrypted.length - 1] ^= 0x01;

        assertThatThrownBy(() -> converter.convertToEntityAttribute(encrypted))
                .isInstanceOf(CandleException.class)
                .satisfies(e -> assertThat(((CandleException) e).errorCode())
                        .isEqualTo(CommonErrorCode.PAYLOAD_DECRYPTION_FAILED));
    }

    @Test
    @DisplayName("32바이트가 아닌 키로 초기화하면 즉시 예외가 발생한다")
    void initKey_invalidKeyLength_throwsImmediately() {
        String tooShortKey = Base64.getEncoder().encodeToString("short-key".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> EncryptedPayloadConverter.initKey(tooShortKey))
                .isInstanceOf(CandleException.class)
                .satisfies(e -> assertThat(((CandleException) e).errorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ENCRYPTION_KEY));
    }

    @Test
    @DisplayName("Base64 형식이 아닌 키 문자열로 초기화하면 즉시 예외가 발생한다")
    void initKey_malformedBase64_throwsImmediately() {
        String notBase64 = "this is not base64!!! ###";

        assertThatThrownBy(() -> EncryptedPayloadConverter.initKey(notBase64))
                .isInstanceOf(CandleException.class)
                .satisfies(e -> assertThat(((CandleException) e).errorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ENCRYPTION_KEY));
    }

    @Test
    @DisplayName("키가 초기화되지 않은 상태에서 암호화를 시도하면 예외가 발생한다")
    void convertToDatabaseColumn_keyNotInitialized_throwsException() {
        EncryptedPayloadConverter.resetKeyForTest(); // setUpKey()가 설정한 키를 일부러 제거

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("data".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CandleException.class)
                .satisfies(e -> assertThat(((CandleException) e).errorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ENCRYPTION_KEY));
    }

    @Test
    @DisplayName("null을 암호화하면 null을 반환한다")
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("null을 복호화하면 null을 반환한다")
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("저장된 값이 IV 길이(12바이트)보다 짧으면 예외가 발생한다")
    void convertToEntityAttribute_tooShort_throwsException() {
        byte[] tooShort = new byte[5];

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tooShort))
                .isInstanceOf(CandleException.class)
                .satisfies(e -> assertThat(((CandleException) e).errorCode())
                        .isEqualTo(CommonErrorCode.PAYLOAD_DECRYPTION_FAILED));
    }
}