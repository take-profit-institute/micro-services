package org.profit.candle.portfolio.analytics.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일별 스냅샷 저장을 DB upsert로 처리한다.
 * EOD 배치는 같은 거래일을 재실행할 수 있으므로 (user_id, snapshot_date)가 이미 있으면
 * 최신 계산값으로 덮어쓴다.
 */
@Component
@RequiredArgsConstructor
public class PortfolioSnapshotInserter {

    private static final String UPSERT_SQL = """
            INSERT INTO portfolio_snapshots (
                user_id, snapshot_date, total_asset, stock_value,
                daily_profit, cumulative_return_rate, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (user_id, snapshot_date) DO UPDATE SET
                total_asset = EXCLUDED.total_asset,
                stock_value = EXCLUDED.stock_value,
                daily_profit = EXCLUDED.daily_profit,
                cumulative_return_rate = EXCLUDED.cumulative_return_rate
            RETURNING id, user_id, snapshot_date, total_asset, stock_value,
                      daily_profit, cumulative_return_rate, created_at
            """;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public PortfolioSnapshotEntity upsert(PortfolioSnapshotEntity entity) {
        return jdbcTemplate.queryForObject(
                UPSERT_SQL,
                (resultSet, rowNumber) -> new PortfolioSnapshotEntity(
                        resultSet.getLong("id"),
                        resultSet.getString("user_id"),
                        resultSet.getObject("snapshot_date", java.time.LocalDate.class),
                        resultSet.getLong("total_asset"),
                        resultSet.getLong("stock_value"),
                        resultSet.getLong("daily_profit"),
                        resultSet.getString("cumulative_return_rate"),
                        resultSet.getTimestamp("created_at").toInstant()
                ),
                entity.userId(),
                entity.snapshotDate(),
                entity.totalAsset(),
                entity.stockValue(),
                entity.dailyProfit(),
                entity.cumulativeReturnRate()
        );
    }
}
