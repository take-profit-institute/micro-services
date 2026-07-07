package org.profit.candle.common.kafka;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KafkaIamSupport {
    private static final String IAM_PORT = ":9098";

    private KafkaIamSupport() {}

    public static Map<String, Object> withIamIfNeeded(String bootstrapServers, Map<String, Object> base) {
        Map<String, Object> properties = new LinkedHashMap<>(base);
        if (bootstrapServers != null && bootstrapServers.contains(IAM_PORT)) {
            properties.put("security.protocol", "SASL_SSL");
            properties.put("sasl.mechanism", "AWS_MSK_IAM");
            properties.put("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");
            properties.put("sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
        }
        return properties;
    }
}
