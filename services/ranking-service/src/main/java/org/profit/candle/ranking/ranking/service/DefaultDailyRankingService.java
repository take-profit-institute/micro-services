package org.profit.candle.ranking.ranking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.ranking.ranking.client.PortfolioSnapshotClient;
import org.profit.candle.ranking.ranking.client.PortfolioSnapshotItem;
import org.profit.candle.ranking.ranking.client.PortfolioSnapshotPage;
import org.profit.candle.ranking.ranking.dto.DailyRankingResult;
import org.profit.candle.ranking.ranking.dto.DailyRankingRow;
import org.profit.candle.ranking.ranking.dto.RankingParticipantCandidate;
import org.profit.candle.ranking.ranking.exception.RankingErrorCode;
import org.profit.candle.ranking.ranking.exception.RankingException;
import org.profit.candle.ranking.ranking.repository.DailyRankingRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultDailyRankingService implements DailyRankingService {

    private static final int PORTFOLIO_PAGE_SIZE = 500;
    private static final int PROFIT_RATE_SCALE = 4;

    private static final Comparator<DailyRankingRow> RANKING_ORDER =
            Comparator.comparing(DailyRankingRow::profitRate).reversed()
                    .thenComparing(DailyRankingRow::tradeCount, Comparator.reverseOrder())
                    .thenComparing(DailyRankingRow::userId);

    private final PortfolioSnapshotClient portfolioSnapshotClient;
    private final DailyRankingRepository dailyRankingRepository;

    /** #105 전체 페이지와 참가자 투영을 결합해 결정적인 일별 순위를 저장한다. */
    @Override
    public DailyRankingResult finalizeDailyRanking(LocalDate rankingDate) {
        Map<UUID, PortfolioSnapshotItem> snapshots = loadSnapshots(rankingDate);
        List<DailyRankingRow> rankings = dailyRankingRepository.findParticipants().stream()
                .filter(RankingParticipantCandidate::eligible)
                .filter(participant -> snapshots.containsKey(participant.userId()))
                .map(participant -> toUnrankedRow(participant, snapshots.get(participant.userId())))
                .sorted(RANKING_ORDER)
                .toList();

        List<DailyRankingRow> positionedRankings = new ArrayList<>(rankings.size());
        for (int index = 0; index < rankings.size(); index++) {
            DailyRankingRow ranking = rankings.get(index);
            positionedRankings.add(new DailyRankingRow(
                    index + 1,
                    ranking.userId(),
                    ranking.totalAsset(),
                    ranking.profitRate(),
                    ranking.tradeCount()));
        }

        dailyRankingRepository.replaceDailyRanking(rankingDate, positionedRankings);
        return new DailyRankingResult(rankingDate, positionedRankings.size());
    }

    /** #105 cursor를 끝까지 순회하며 사용자별 EOD 스냅샷을 수집한다. */
    private Map<UUID, PortfolioSnapshotItem> loadSnapshots(LocalDate rankingDate) {
        Map<UUID, PortfolioSnapshotItem> snapshots = new HashMap<>();
        Set<String> visitedPageTokens = new HashSet<>();
        String pageToken = "";

        while (true) {
            PortfolioSnapshotPage page = portfolioSnapshotClient.listDailySnapshots(
                    rankingDate, pageToken, PORTFOLIO_PAGE_SIZE);
            for (PortfolioSnapshotItem item : page.items()) {
                validateSnapshot(item);
                if (snapshots.putIfAbsent(item.userId(), item) != null) {
                    throw new RankingException(RankingErrorCode.INVALID_PORTFOLIO_SNAPSHOT);
                }
            }
            if (!page.hasNext()) {
                return snapshots;
            }
            if (!visitedPageTokens.add(page.nextPageToken())) {
                throw new RankingException(RankingErrorCode.INVALID_PORTFOLIO_SNAPSHOT);
            }
            pageToken = page.nextPageToken();
        }
    }

    /** Portfolio 값의 필수 조건을 검사한다. */
    private void validateSnapshot(PortfolioSnapshotItem item) {
        if (item.userId() == null || item.cumulativeReturnRate() == null || item.totalAsset() < 0) {
            throw new RankingException(RankingErrorCode.INVALID_PORTFOLIO_SNAPSHOT);
        }
    }

    /** 참가자 정보와 Portfolio 수익률을 정렬 전 Ranking 행으로 결합한다. */
    private DailyRankingRow toUnrankedRow(
            RankingParticipantCandidate participant,
            PortfolioSnapshotItem snapshot) {
        BigDecimal profitRate = snapshot.cumulativeReturnRate()
                .setScale(PROFIT_RATE_SCALE, RoundingMode.HALF_UP);
        return new DailyRankingRow(
                0,
                participant.userId(),
                snapshot.totalAsset(),
                profitRate,
                participant.tradeCount());
    }
}
