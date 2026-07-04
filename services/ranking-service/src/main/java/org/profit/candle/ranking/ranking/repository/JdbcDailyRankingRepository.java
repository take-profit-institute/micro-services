package org.profit.candle.ranking.ranking.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.ranking.ranking.dto.DailyRankingRow;
import org.profit.candle.ranking.ranking.dto.RankingParticipantCandidate;
import org.profit.candle.ranking.ranking.entity.ParticipantStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcDailyRankingRepository implements DailyRankingRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    /** 참가자 투영 테이블을 일별 랭킹 계산용 DTO로 조회한다. */
    @Override
    public List<RankingParticipantCandidate> findParticipants() {
        return jdbcTemplate.query("""
                SELECT user_id, trade_count, user_status, account_status
                FROM ranking_participants
                """, (resultSet, rowNumber) -> new RankingParticipantCandidate(
                UUID.fromString(resultSet.getString("user_id")),
                resultSet.getInt("trade_count"),
                ParticipantStatus.valueOf(resultSet.getString("user_status")),
                ParticipantStatus.valueOf(resultSet.getString("account_status"))));
    }

    /** 같은 날짜의 기존 결과를 지운 뒤 새 계산 결과와 완료 표식을 원자적으로 저장한다. */
    @Override
    @Transactional
    public void replaceDailyRanking(LocalDate rankingDate, List<DailyRankingRow> rankings) {
        jdbcTemplate.update("DELETE FROM ranking_history WHERE ranking_date = ?", rankingDate);
        jdbcTemplate.update("DELETE FROM ranking_snapshot WHERE snapshot_date = ?", rankingDate);

        jdbcTemplate.batchUpdate("""
                        INSERT INTO ranking_snapshot
                            (user_id, total_asset, profit_rate, trade_count, snapshot_date)
                        VALUES (?, ?, ?, ?, ?)
                        """, rankings, BATCH_SIZE, (statement, ranking) -> {
                    statement.setObject(1, ranking.userId());
                    statement.setLong(2, ranking.totalAsset());
                    statement.setBigDecimal(3, ranking.profitRate());
                    statement.setInt(4, ranking.tradeCount());
                    statement.setDate(5, Date.valueOf(rankingDate));
                });

        jdbcTemplate.batchUpdate("""
                        INSERT INTO ranking_history
                            (user_id, ranking_position, total_asset, profit_rate, trade_count, ranking_date)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, rankings, BATCH_SIZE, (statement, ranking) -> {
                    statement.setObject(1, ranking.userId());
                    statement.setInt(2, ranking.position());
                    statement.setLong(3, ranking.totalAsset());
                    statement.setBigDecimal(4, ranking.profitRate());
                    statement.setInt(5, ranking.tradeCount());
                    statement.setDate(6, Date.valueOf(rankingDate));
                });

        jdbcTemplate.update("""
                INSERT INTO ranking_runs (ranking_date, ranked_user_count, completed_at)
                VALUES (?, ?, now())
                ON CONFLICT (ranking_date)
                DO UPDATE SET ranked_user_count = EXCLUDED.ranked_user_count, completed_at = now()
                """, rankingDate, rankings.size());
    }
}
