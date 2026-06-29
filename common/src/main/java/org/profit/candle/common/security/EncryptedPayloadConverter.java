package org.profit.candle.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.common.error.CommonErrorCode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * IdempotencyRecord.responsePayload 자동 암복호화 (AES-256-GCM).
 *
 * 책임 분리:
 *   - 이 클래스는 byte[] 평문 ↔ byte[] 암호문 변환만 담당한다.
 *   - 언제·어디서 호출되는지는 JPA가 결정한다 (@Convert 어노테이션이 붙은 필드를
 *     쓰거나 읽는 순간 자동 호출됨). 호출하는 service/repository 코드는
 *     암복호화를 전혀 인지하지 않는다.
 *
 * 저장 형식: [12바이트 IV][암호문 + 16바이트 GCM 인증 태그]
 *   - GCM은 매 암호화마다 다른 IV가 필요하므로, IV를 암호문 앞에 같이 저장한다.
 *   - IV는 비밀이 아니라 "다시 못 쓰는 일회성 값"이라 평문 저장이 표준 관행이다.
 *
 * 인스턴스화 방식 (중요):
 *   - common 패키지는 각 서비스의 Spring 컴포넌트 스캔 범위 밖이라 @Component만으로는
 *     빈으로 등록되지 않는다. 또한 JPA @Converter는 Hibernate가 기본 생성자로 직접
 *     인스턴스화하므로, Spring 생성자 주입(@Value)을 받을 수 없다.
 *   - 그래서 이 클래스는 기본 생성자만 두고, 암호화 키는 정적 홀더에서 읽는다.
 *     각 서비스는 부팅 시 한 번 EncryptedPayloadConverter.initKey(...)를 호출해
 *     키를 주입해야 한다 (각 서비스의 @Configuration 클래스 또는 @PostConstruct에서).
 *
 * 키 관리:
 *   - 키는 외부 설정(환경변수/Secret Manager)에서 주입받는다. 코드에 하드코딩하지 않는다.
 *   - application 설정 키는 candle.security.payload-encryption-key이며,
 *     Spring relaxed binding 규칙상 대응 환경변수 이름은
 *     CANDLE_SECURITY_PAYLOAD_ENCRYPTION_KEY이다.
 *   - 키 회전(rotation)이 필요해지면, 이 컨버터에 key version prefix를 추가해
 *     이전 키로도 복호화 가능하게 확장한다. (현재 범위에서는 단일 키만 지원)
 */
@Converter
public class EncryptedPayloadConverter implements AttributeConverter<byte[], byte[]> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // JPA가 기본 생성자로 인스턴스를 만들기 때문에, 키는 생성자 주입이 아니라
    // 부팅 시점에 각 서비스가 채워주는 정적 홀더에서 읽는다.
    private static volatile SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 각 서비스 부팅 시 한 번 호출해 암호화 키를 설정한다.
     * (예: 서비스의 @Configuration 클래스에서 @PostConstruct로 호출)
     *
     * @param base64Key candle.security.payload-encryption-key 설정값.
     *                   32바이트(256비트) 키를 Base64로 인코딩한 문자열이어야 한다.
     */
    public static void initKey(String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new CandleException(CommonErrorCode.INVALID_ENCRYPTION_KEY, e);
        }
        if (keyBytes.length != 32) {
            throw new CandleException(CommonErrorCode.INVALID_ENCRYPTION_KEY);
        }
        secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public byte[] convertToDatabaseColumn(byte[] plainPayload) {
        if (plainPayload == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainPayload);

            return ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new CandleException(CommonErrorCode.PAYLOAD_ENCRYPTION_FAILED, e);
        }
    }

    @Override
    public byte[] convertToEntityAttribute(byte[] storedValue) {
        if (storedValue == null) {
            return null;
        }
        if (storedValue.length < IV_LENGTH_BYTES) {
            throw new CandleException(CommonErrorCode.PAYLOAD_DECRYPTION_FAILED);
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(storedValue);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException e) {
            // 키가 바뀌었거나 데이터가 손상된 경우 여기로 떨어진다.
            throw new CandleException(CommonErrorCode.PAYLOAD_DECRYPTION_FAILED, e);
        }
    }

    private SecretKeySpec requireKey() {
        SecretKeySpec key = secretKey;
        if (key == null) {
            // initKey()가 호출되지 않은 상태에서 암복호화가 시도된 경우.
            // 부팅 설정 누락을 즉시 드러내기 위해 INVALID_ENCRYPTION_KEY로 통일한다.
            throw new CandleException(CommonErrorCode.INVALID_ENCRYPTION_KEY);
        }
        return key;
    }

    /**
     * 테스트에서 정적 키 상태를 초기화하기 위한 메서드. 운영 코드에서는 호출하지 않는다.
     * (각 서비스는 부팅 시 한 번만 initKey()를 호출하고 이후 재설정하지 않는다.)
     */
    static void resetKeyForTest() {
        secretKey = null;
    }
}