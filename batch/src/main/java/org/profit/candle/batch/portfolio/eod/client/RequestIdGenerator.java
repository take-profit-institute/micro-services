package org.profit.candle.batch.portfolio.eod.client;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RequestIdGenerator {

    public String generate() {
        return UUID.randomUUID().toString();
    }
}
