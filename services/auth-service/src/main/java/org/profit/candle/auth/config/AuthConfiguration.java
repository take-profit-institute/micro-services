package org.profit.candle.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    @Bean
    KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static final Logger log = LoggerFactory.getLogger(AuthConfiguration.class);

    // RS256 서명 키(JWK). privateKey(PEM PKCS#8)가 있으면 로드, 없으면 dev용 임시 키 생성.
    // kid = RFC7638 thumbprint(공개키 결정론적) → 로테이션 시 자연히 달라지고 JWKS 선택에 쓰인다.
    @Bean
    RSAKey rsaSigningKey(AuthProperties properties) {
        try {
            RSAPublicKey publicKey;
            java.security.PrivateKey privateKey;
            String pem = properties.jwt().privateKey();
            if (pem != null && !pem.isBlank()) {
                String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
                byte[] der = Base64.getDecoder().decode(base64);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                RSAPrivateCrtKey priv = (RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
                publicKey = (RSAPublicKey) kf.generatePublic(
                        new RSAPublicKeySpec(priv.getModulus(), priv.getPublicExponent()));
                privateKey = priv;
            } else {
                log.warn("auth.jwt.private-key 미설정 — dev용 임시 RSA 키 생성. 운영에서는 반드시 주입할 것.");
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                publicKey = (RSAPublicKey) pair.getPublic();
                privateKey = pair.getPrivate();
            }
            RSAKey key = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
        } catch (Exception e) {
            throw new IllegalStateException("RS256 서명 키 초기화 실패", e);
        }
    }

    // 공개 JWKS(엔드포인트에서 toPublicJWKSet 로 노출). 로테이션 시 이 셋에 키를 추가한다.
    @Bean
    JWKSet jwkSet(RSAKey rsaSigningKey) {
        return new JWKSet(rsaSigningKey);
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSet jwkSet) {
        // NimbusJwtEncoder 가 알고리즘에 맞는 JWK 를 선택하고 kid 헤더를 자동으로 채운다.
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(jwkSet));
    }
}
