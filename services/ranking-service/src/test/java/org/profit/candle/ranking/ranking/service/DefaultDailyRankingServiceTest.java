package org.profit.candle.ranking.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.profit.candle.ranking.ranking.client.PortfolioSnapshotClient;
import org.profit.candle.ranking.ranking.client.PortfolioSnapshotItem;
import org.profit.candle.ranking.ranking.client.PortfolioSnapshotPage;
import org.profit.candle.ranking.ranking.dto.DailyRankingRow;
import org.profit.candle.ranking.ranking.dto.RankingParticipantCandidate;
import org.profit.candle.ranking.ranking.entity.ParticipantStatus;
import org.profit.candle.ranking.ranking.exception.RankingErrorCode;
import org.profit.candle.ranking.ranking.exception.RankingException;
import org.profit.candle.ranking.ranking.repository.DailyRankingRepository;

class DefaultDailyRankingServiceTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 7, 3);

    /** 5회 미만 또는 비활성 사용자·계좌가 최종 순위에서 제외되는지 검증한다. */
    @Test
    void finalizeDailyRankingFiltersIneligibleParticipants() {
        UUID eligibleUser = userId(1);
        FakePortfolioSnapshotClient client = new FakePortfolioSnapshotClient(Map.of(
                "", page(List.of(
                        snapshot(eligibleUser, 10_000L, "12.34"),
                        snapshot(userId(2), 20_000L, "30.00"),
                        snapshot(userId(3), 30_000L, "40.00")), "")));
        FakeDailyRankingRepository repository = new FakeDailyRankingRepository(List.of(
                participant(eligibleUser, 5, ParticipantStatus.ACTIVE, ParticipantStatus.ACTIVE),
                participant(userId(2), 4, ParticipantStatus.ACTIVE, ParticipantStatus.ACTIVE),
                participant(userId(3), 8, ParticipantStatus.SUSPENDED, ParticipantStatus.ACTIVE)));

        var result = new DefaultDailyRankingService(client, repository).finalizeDailyRanking(RANKING_DATE);

        assertThat(result.rankedUserCount()).isEqualTo(1);
        assertThat(repository.savedRankings).extracting(DailyRankingRow::userId)
                .containsExactly(eligibleUser);
    }

    /** 수익률·거래 횟수·사용자 ID 순서로 결정적인 순위가 만들어지는지 검증한다. */
    @Test
    void finalizeDailyRankingUsesDeterministicOrder() {
        UUID highestRate = userId(9);
        UUID lowerUserId = userId(2);
        UUID higherUserId = userId(3);
        UUID fewerTrades = userId(4);
        FakePortfolioSnapshotClient client = new FakePortfolioSnapshotClient(Map.of(
                "", page(List.of(
                        snapshot(highestRate, 10_000L, "20.00004"),
                        snapshot(lowerUserId, 10_000L, "10.0000")), "next"),
                "next", page(List.of(
                        snapshot(higherUserId, 10_000L, "10.0000"),
                        snapshot(fewerTrades, 10_000L, "10.0000")), "")));
        FakeDailyRankingRepository repository = new FakeDailyRankingRepository(List.of(
                activeParticipant(highestRate, 5),
                activeParticipant(lowerUserId, 7),
                activeParticipant(higherUserId, 7),
                activeParticipant(fewerTrades, 5)));

        new DefaultDailyRankingService(client, repository).finalizeDailyRanking(RANKING_DATE);

        assertThat(repository.savedRankings).extracting(DailyRankingRow::userId)
                .containsExactly(highestRate, lowerUserId, higherUserId, fewerTrades);
        assertThat(repository.savedRankings).extracting(DailyRankingRow::position)
                .containsExactly(1, 2, 3, 4);
        assertThat(repository.savedRankings.getFirst().profitRate())
                .isEqualByComparingTo("20.0000");
        assertThat(client.requestedPageSizes).containsOnly(500);
    }

    /** 대상자가 없어도 0명 결과가 저장소로 전달되어 완료일을 기록할 수 있는지 검증한다. */
    @Test
    void finalizeDailyRankingRecordsAnEmptyCompletedRun() {
        FakePortfolioSnapshotClient client = new FakePortfolioSnapshotClient(Map.of(
                "", page(List.of(), "")));
        FakeDailyRankingRepository repository = new FakeDailyRankingRepository(List.of());

        var result = new DefaultDailyRankingService(client, repository).finalizeDailyRanking(RANKING_DATE);

        assertThat(result.rankedUserCount()).isZero();
        assertThat(repository.savedDate).isEqualTo(RANKING_DATE);
        assertThat(repository.savedRankings).isEmpty();
    }

    /** #105 응답에 같은 사용자가 중복되면 잘못된 스냅샷으로 거절하는지 검증한다. */
    @Test
    void finalizeDailyRankingRejectsDuplicatePortfolioSnapshots() {
        UUID duplicateUser = userId(1);
        FakePortfolioSnapshotClient client = new FakePortfolioSnapshotClient(Map.of(
                "", page(List.of(
                        snapshot(duplicateUser, 10_000L, "1.0"),
                        snapshot(duplicateUser, 10_000L, "1.0")), "")));
        FakeDailyRankingRepository repository = new FakeDailyRankingRepository(List.of());

        assertThatThrownBy(() ->
                new DefaultDailyRankingService(client, repository).finalizeDailyRanking(RANKING_DATE))
                .isInstanceOf(RankingException.class)
                .satisfies(exception -> assertThat(((RankingException) exception).errorCode())
                        .isEqualTo(RankingErrorCode.INVALID_PORTFOLIO_SNAPSHOT));
    }

    /** 테스트에서 #105의 cursor pagination을 대신한다. */
    private static final class FakePortfolioSnapshotClient implements PortfolioSnapshotClient {
        private final Map<String, PortfolioSnapshotPage> pages;
        private final List<Integer> requestedPageSizes = new ArrayList<>();

        private FakePortfolioSnapshotClient(Map<String, PortfolioSnapshotPage> pages) {
            this.pages = new LinkedHashMap<>(pages);
        }

        @Override
        public PortfolioSnapshotPage listDailySnapshots(LocalDate snapshotDate, String pageToken, int pageSize) {
            requestedPageSizes.add(pageSize);
            return pages.get(pageToken);
        }
    }

    /** 테스트에서 계산 결과 저장을 메모리로 대신한다. */
    private static final class FakeDailyRankingRepository implements DailyRankingRepository {
        private final List<RankingParticipantCandidate> participants;
        private LocalDate savedDate;
        private List<DailyRankingRow> savedRankings = List.of();

        private FakeDailyRankingRepository(List<RankingParticipantCandidate> participants) {
            this.participants = participants;
        }

        @Override
        public List<RankingParticipantCandidate> findParticipants() {
            return participants;
        }

        @Override
        public void replaceDailyRanking(LocalDate rankingDate, List<DailyRankingRow> rankings) {
            this.savedDate = rankingDate;
            this.savedRankings = List.copyOf(rankings);
        }
    }

    /** 테스트용 Portfolio 스냅샷 페이지를 만든다. */
    private static PortfolioSnapshotPage page(List<PortfolioSnapshotItem> items, String nextPageToken) {
        return new PortfolioSnapshotPage(items, nextPageToken);
    }

    /** 테스트용 Portfolio 스냅샷을 만든다. */
    private static PortfolioSnapshotItem snapshot(UUID userId, long totalAsset, String profitRate) {
        return new PortfolioSnapshotItem(userId, totalAsset, new BigDecimal(profitRate));
    }

    /** 사용자·계좌가 활성 상태인 테스트 참가자를 만든다. */
    private static RankingParticipantCandidate activeParticipant(UUID userId, int tradeCount) {
        return participant(userId, tradeCount, ParticipantStatus.ACTIVE, ParticipantStatus.ACTIVE);
    }

    /** 상태를 직접 지정할 수 있는 테스트 참가자를 만든다. */
    private static RankingParticipantCandidate participant(
            UUID userId,
            int tradeCount,
            ParticipantStatus userStatus,
            ParticipantStatus accountStatus) {
        return new RankingParticipantCandidate(
                userId, tradeCount, userStatus, accountStatus);
    }

    /** 정렬 결과를 쉽게 예측할 수 있는 테스트 UUID를 만든다. */
    private static UUID userId(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
