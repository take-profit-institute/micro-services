package org.profit.candle.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.common.error.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 *   - 언제·어디서 호출되는지는 JPA가 결정한다.
 *     (@Convert 어노테이션이 붙은 필드를 쓰거나 읽는 순간 자동 호출됨)
 *     호출하는 service/repository 코드는 암복호화를 전혀 인지하지 않는다.
 *
 * 저장 형식: [12바이트 IV][암호문 + 16바이트 GCM 인증 태그]
 *   - GCM은 매 암호화마다 다른 IV가 필요하므로, IV를 암호문 앞에 같이 저장한다.
 *   - IV는 비밀이 아니라 "다시 못 쓰는 일회성 값"이라 평문 저장이 표준 관행이다.
 *
 * 키 관리:
 *   - 키는 외부 설정(환경변수/Secret Manager)에서 주입받는다. 코드에 하드코딩하지 않는다.
 *   - 키 회전(rotation)이 필요해지면, 이 컨버터에 key version prefix를 추가해
 *     이전 키로도 복호화 가능하게 확장한다. (현재 범위에서는 단일 키만 지원)
 */
@Converter
@Component
public class EncryptedPayloadConverter implements AttributeConverter<byte[], byte[]> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param base64Key application 설정의 candle.security.payload-encryption-key.
     *                  32바이트(256비트) 키를 Base64로 인코딩한 문자열이어야 한다.
     *                  환경변수 PAYLOAD_ENCRYPTION_KEY로 주입받는 것을 권장한다.
     */
    public EncryptedPayloadConverter(
            @Value("${candle.security.payload-encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new CandleException(CommonErrorCode.INVALID_ENCRYPTION_KEY);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
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
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
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
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException e) {
            // 키가 바뀌었거나 손상된 경우 여기로 떨어진다.
            throw new CandleException(CommonErrorCode.PAYLOAD_DECRYPTION_FAILED, e);
        }
    }
}