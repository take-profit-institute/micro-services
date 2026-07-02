package org.profit.candle.ranking.ranking.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.ranking.ranking.dto.RankingResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcRankingQueryRepository implements RankingQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    /** ranking_runs의 마지막 완료일을 조회한다. */
    @Override
    public Optional<LocalDate> findLatestCompletedDate() {
        return jdbcTemplate.query(
                "SELECT max(ranking_date) AS ranking_date FROM ranking_runs",
                (resultSet, rowNumber) -> {
                    var date = resultSet.getDate("ranking_date");
                    return date == null ? null : date.toLocalDate();
                }).stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /** 순위 결과와 현재 닉네임을 결합해 TOP 100 범위 안에서 조회한다. */
    @Override
    public List<RankingResult> findRankings(LocalDate rankingDate, int afterPosition, int limit) {
        return jdbcTemplate.query("""
                SELECT h.ranking_position, h.user_id, p.nickname, h.total_asset,
                       h.profit_rate, h.trade_count, h.ranking_date
                FROM ranking_history h
                JOIN ranking_participants p ON p.user_id = h.user_id
                WHERE h.ranking_date = ?
                  AND h.ranking_position > ?
                  AND h.ranking_position <= 100
                ORDER BY h.ranking_position
                LIMIT ?
                """, (resultSet, rowNumber) -> map(resultSet), rankingDate, afterPosition, limit);
    }

    /** 사용자 ID로 해당 날짜의 순위를 조회한다. */
    @Override
    public Optional<RankingResult> findUserRanking(LocalDate rankingDate, UUID userId) {
        return jdbcTemplate.query("""
                SELECT h.ranking_position, h.user_id, p.nickname, h.total_asset,
                       h.profit_rate, h.trade_count, h.ranking_date
                FROM ranking_history h
                JOIN ranking_participants p ON p.user_id = h.user_id
                WHERE h.ranking_date = ? AND h.user_id = ?
                """, (resultSet, rowNumber) -> map(resultSet), rankingDate, userId)
                .stream().findFirst();
    }

    /** JDBC 결과 한 행을 조회 DTO로 변환한다. */
    private RankingResult map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new RankingResult(
                resultSet.getInt("ranking_position"),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("nickname"),
                resultSet.getLong("total_asset"),
                resultSet.getBigDecimal("profit_rate"),
                resultSet.getInt("trade_count"),
                resultSet.getDate("ranking_date").toLocalDate());
    }
}
