package org.profit.candle.ranking.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.profit.candle.ranking.ranking.cache.RankingCache;
import org.profit.candle.ranking.ranking.dto.RankingResult;
import org.profit.candle.ranking.ranking.exception.RankingErrorCode;
import org.profit.candle.ranking.ranking.exception.RankingException;
import org.profit.candle.ranking.ranking.repository.RankingQueryRepository;

class DefaultRankingQueryServiceTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 7, 3);

    /** Redis miss 시 DB 결과를 반환하고 TOP 100 캐시를 재생성하는지 검증한다. */
    @Test
    void listRankingsFallsBackToDatabaseAndRebuildsCache() {
        FakeRankingRepository repository = new FakeRankingRepository(rankings(3));
        FakeRankingCache cache = new FakeRankingCache();

        var page = new DefaultRankingQueryService(repository, cache).listRankings(2, "");

        assertThat(page.rankings()).extracting(RankingResult::position).containsExactly(1, 2);
        assertThat(page.nextPageToken()).isNotBlank();
        assertThat(cache.latestDate).isEqualTo(RANKING_DATE);
        assertThat(cache.topRankings).hasSize(3);
    }

    /** 첫 페이지 cursor가 같은 날짜의 다음 순위부터 안정적으로 조회하는지 검증한다. */
    @Test
    void listRankingsUsesStableCursor() {
        FakeRankingRepository repository = new FakeRankingRepository(rankings(4));
        FakeRankingCache cache = new FakeRankingCache();
        DefaultRankingQueryService service = new DefaultRankingQueryService(repository, cache);

        var firstPage = service.listRankings(2, "");
        var secondPage = service.listRankings(2, firstPage.nextPageToken());

        assertThat(secondPage.rankings()).extracting(RankingResult::position).containsExactly(3, 4);
        assertThat(secondPage.nextPageToken()).isEmpty();
    }

    /** Redis 장애가 발생해도 DB에서 TOP 랭킹을 반환하는지 검증한다. */
    @Test
    void listRankingsFallsBackWhenRedisIsUnavailable() {
        FakeRankingRepository repository = new FakeRankingRepository(rankings(2));
        FakeRankingCache cache = new FakeRankingCache();
        cache.unavailable = true;

        var page = new DefaultRankingQueryService(repository, cache).listRankings(20, "");

        assertThat(page.rankings()).hasSize(2);
    }

    /** 내 순위가 없을 때 Ranking 전용 NOT_FOUND 오류를 반환하는지 검증한다. */
    @Test
    void getMyRankingReturnsNotFound() {
        FakeRankingRepository repository = new FakeRankingRepository(rankings(1));
        FakeRankingCache cache = new FakeRankingCache();

        assertThatThrownBy(() -> new DefaultRankingQueryService(repository, cache)
                .getMyRanking(userId(99)))
                .isInstanceOf(RankingException.class)
                .satisfies(exception -> assertThat(((RankingException) exception).errorCode())
                        .isEqualTo(RankingErrorCode.RANKING_NOT_FOUND));
    }

    /** 테스트에 사용할 순위 목록을 만든다. */
    private List<RankingResult> rankings(int count) {
        List<RankingResult> rankings = new ArrayList<>();
        for (int position = 1; position <= count; position++) {
            rankings.add(new RankingResult(
                    position,
                    userId(position),
                    "user-" + position,
                    100_000L * position,
                    new BigDecimal(20 - position + ".0000"),
                    10 - position,
                    RANKING_DATE));
        }
        return List.copyOf(rankings);
    }

    /** 테스트 순서를 예측할 수 있는 UUID를 만든다. */
    private UUID userId(int suffix) {
        return UUID.fromString("70000000-0000-4000-8000-%012d".formatted(suffix));
    }

    /** DB 조회를 메모리 목록으로 대신한다. */
    private static final class FakeRankingRepository implements RankingQueryRepository {
        private final List<RankingResult> rankings;

        private FakeRankingRepository(List<RankingResult> rankings) {
            this.rankings = rankings;
        }

        @Override
        public Optional<LocalDate> findLatestCompletedDate() {
            return Optional.of(RANKING_DATE);
        }

        @Override
        public List<RankingResult> findRankings(LocalDate rankingDate, int afterPosition, int limit) {
            return rankings.stream()
                    .filter(ranking -> ranking.position() > afterPosition)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<RankingResult> findUserRanking(LocalDate rankingDate, UUID userId) {
            return rankings.stream().filter(ranking -> ranking.userId().equals(userId)).findFirst();
        }
    }

    /** Redis hit·miss·장애를 메모리 상태로 대신한다. */
    private static final class FakeRankingCache implements RankingCache {
        private LocalDate latestDate;
        private List<RankingResult> topRankings = List.of();
        private RankingResult userRanking;
        private boolean unavailable;

        @Override
        public Optional<LocalDate> findLatestDate() {
            checkAvailable();
            return Optional.ofNullable(latestDate);
        }

        @Override
        public void putLatestDate(LocalDate rankingDate) {
            checkAvailable();
            latestDate = rankingDate;
        }

        @Override
        public Optional<List<RankingResult>> findTopRankings(LocalDate rankingDate) {
            checkAvailable();
            return topRankings.isEmpty() ? Optional.empty() : Optional.of(topRankings);
        }

        @Override
        public void putTopRankings(LocalDate rankingDate, List<RankingResult> rankings) {
            checkAvailable();
            topRankings = List.copyOf(rankings);
        }

        @Override
        public Optional<RankingResult> findUserRanking(LocalDate rankingDate, UUID userId) {
            checkAvailable();
            return Optional.ofNullable(userRanking);
        }

        @Override
        public void putUserRanking(RankingResult ranking) {
            checkAvailable();
            userRanking = ranking;
        }

        /** Redis 장애 테스트를 위해 설정된 경우 예외를 발생시킨다. */
        private void checkAvailable() {
            if (unavailable) {
                throw new IllegalStateException("redis unavailable");
            }
        }
    }
}
