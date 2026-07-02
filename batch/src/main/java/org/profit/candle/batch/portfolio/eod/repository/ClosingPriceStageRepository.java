package org.profit.candle.batch.portfolio.eod.repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClosingPriceStageRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO batch_portfolio_eod_closing_prices (
                job_instance_id, business_date, symbol, closing_price, quoted_at
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (job_instance_id, symbol) DO UPDATE SET
                closing_price = EXCLUDED.closing_price,
                quoted_at = EXCLUDED.quoted_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public ClosingPriceStageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertAll(
            long jobInstanceId,
            LocalDate businessDate,
            List<ClosingPrice> prices
    ) {
        jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                prices,
                prices.size(),
                (statement, price) -> {
                    statement.setLong(1, jobInstanceId);
                    statement.setObject(2, businessDate);
                    statement.setString(3, price.symbol());
                    statement.setLong(4, price.price());
                    statement.setTimestamp(5, Timestamp.from(price.quotedAt()));
                }
        );
    }

    public Map<String, ClosingPrice> loadAll(long jobInstanceId) {
        Map<String, ClosingPrice> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT symbol, closing_price, quoted_at
                FROM batch_portfolio_eod_closing_prices
                WHERE job_instance_id = ?
                """,
                resultSet -> {
                    String symbol = resultSet.getString("symbol");
                    result.put(
                            symbol,
                            new ClosingPrice(
                                    symbol,
                                    resultSet.getLong("closing_price"),
                                    resultSet.getTimestamp("quoted_at").toInstant()
                            )
                    );
                },
                jobInstanceId
        );
        return Map.copyOf(result);
    }
}
