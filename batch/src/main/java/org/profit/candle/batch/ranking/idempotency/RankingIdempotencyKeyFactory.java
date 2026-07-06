package org.profit.candle.batch.ranking.idempotency;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 거래일별 Ranking 명령에 사용할 결정적 UUID를 생성한다. */
@Component
public class RankingIdempotencyKeyFactory {

    /** 같은 거래일에는 항상 같은 canonical UUID를 반환한다. */
    public String create(LocalDate rankingDate) {
        String source = "ranking-finalize:" + rankingDate;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
