package org.profit.candle.portfolio.analytics.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일별 스냅샷 삽입을 감싸는 독립 트랜잭션 경계.
 *
 * 별도 빈으로 두는 이유: (user_id, snapshot_date) UNIQUE 위반이 이 트랜잭션 "안"에서 발생하고
 * 롤백되어 DataIntegrityViolationException 으로 호출부에 전파돼야 한다. 그래야 동시 중복 호출
 * 경합에서 진 쪽이 상위 로직에서 재조회로 멱등 반환할 수 있다(IDEMPOTENCY.md §5-5).
 * 같은 클래스 내부 호출은 프록시를 우회해 트랜잭션이 걸리지 않으므로 분리한다.
 */
@Component
@RequiredArgsConstructor
public class PortfolioSnapshotInserter {

    private final PortfolioSnapshotWriter snapshotWriter;

    @Transactional
    public PortfolioSnapshotEntity insert(PortfolioSnapshotEntity entity) {
        return snapshotWriter.save(entity);
    }
}
