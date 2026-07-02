package org.profit.candle.batch.portfolio.eod.idempotency;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SnapshotIdempotencyKeyFactory {

    public String create(LocalDate businessDate, String userId) {
        String source = "portfolio-eod:" + businessDate + ":" + userId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
