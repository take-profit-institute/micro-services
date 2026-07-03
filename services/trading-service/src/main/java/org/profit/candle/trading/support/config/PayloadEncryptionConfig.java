package org.profit.candle.trading.support.config;

import jakarta.annotation.PostConstruct;
import org.profit.candle.common.security.EncryptedPayloadConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PayloadEncryptionConfig {

    @Value("${candle.security.payload-encryption-key}")
    private String payloadEncryptionKey;

    @PostConstruct
    public void init() {
        EncryptedPayloadConverter.initKey(payloadEncryptionKey);
    }
}