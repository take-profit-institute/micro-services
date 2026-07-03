package org.profit.candle.ranking.ranking.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.ranking.ranking.cache.RankingCache;
import org.profit.candle.ranking.ranking.dto.RankingPage;
import org.profit.candle.ranking.ranking.dto.RankingResult;
import org.profit.candle.ranking.ranking.exception.RankingErrorCode;
import org.profit.candle.ranking.ranking.exception.RankingException;
import org.profit.candle.ranking.ranking.repository.RankingQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultRankingQueryService implements RankingQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RankingQueryRepository repository;
    private final RankingCache cache;

    /** Redis 우선, 장애·miss 시 DB fallback으로 TOP 랭킹을 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public RankingPage listRankings(int requestedPageSize, String pageToken) {
        int pageSize = normalizePageSize(requestedPageSize);
        Cursor cursor = decodeCursor(pageToken);
        LocalDate rankingDate = cursor == null ? latestDate() : cursor.rankingDate();
        int afterPosition = cursor == null ? 0 : cursor.position();

        List<RankingResult> candidates = findCachedTop(rankingDate)
                .map(rankings -> rankings.stream()
                        .filter(ranking -> ranking.position() > afterPosition)
                        .limit(pageSize + 1L)
                        .toList())
                .orElseGet(() -> repository.findRankings(rankingDate, afterPosition, pageSize + 1));
        boolean hasNext = candidates.size() > pageSize;
        List<RankingResult> page = candidates.stream().limit(pageSize).toList();
        String nextToken = hasNext ? encodeCursor(rankingDate, page.getLast().position()) : "";
        return new RankingPage(page, nextToken);
    }

    /** Redis 사용자 캐시를 먼저 조회하고 없거나 장애면 DB에서 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public RankingResult getMyRanking(UUID userId) {
        LocalDate rankingDate = latestDate();
        Optional<RankingResult> cached = safeCache(() -> cache.findUserRanking(rankingDate, userId));
        if (cached.isPresent()) {
            return cached.get();
        }
        RankingResult ranking = repository.findUserRanking(rankingDate, userId)
                .orElseThrow(() -> new RankingException(RankingErrorCode.RANKING_NOT_FOUND));
        safeCacheWrite(() -> cache.putUserRanking(ranking));
        return ranking;
    }

    /** DB의 마지막 완료일을 기준으로 누락되거나 오래된 Redis 날짜를 복구한다. */
    private LocalDate latestDate() {
        LocalDate rankingDate = repository.findLatestCompletedDate()
                .orElseThrow(() -> new RankingException(RankingErrorCode.RANKING_NOT_FOUND));
        Optional<LocalDate> cached = safeCache(cache::findLatestDate);
        if (cached.isEmpty() || !cached.get().equals(rankingDate)) {
            safeCacheWrite(() -> cache.putLatestDate(rankingDate));
        }
        return rankingDate;
    }

    /** 최신 TOP 100 캐시를 조회하고 miss면 DB 결과로 재생성한다. */
    private Optional<List<RankingResult>> findCachedTop(LocalDate rankingDate) {
        Optional<List<RankingResult>> cached = safeCache(() -> cache.findTopRankings(rankingDate));
        if (cached.isPresent()) {
            return cached;
        }
        List<RankingResult> rankings = repository.findRankings(rankingDate, 0, MAX_PAGE_SIZE);
        safeCacheWrite(() -> cache.putTopRankings(rankingDate, rankings));
        return Optional.of(rankings);
    }

    /** page size 기본값과 최대값을 적용한다. */
    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** cursor가 없으면 첫 페이지, 있으면 날짜와 마지막 순위를 복원한다. */
    private Cursor decodeCursor(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(pageToken), StandardCharsets.UTF_8);
            String[] values = decoded.split(":", 2);
            int position = Integer.parseInt(values[1]);
            if (position < 0 || position >= MAX_PAGE_SIZE) {
                throw new IllegalArgumentException();
            }
            return new Cursor(LocalDate.parse(values[0]), position);
        } catch (Exception exception) {
            throw new RankingException(RankingErrorCode.INVALID_PAGE_TOKEN, exception);
        }
    }

    /** 날짜와 마지막 순위를 URL-safe cursor로 만든다. */
    private String encodeCursor(LocalDate rankingDate, int position) {
        String value = rankingDate + ":" + position;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Redis 장애를 cache miss로 바꿔 DB fallback을 허용한다. */
    private <T> Optional<T> safeCache(java.util.function.Supplier<Optional<T>> operation) {
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Redis 쓰기 장애가 정상 DB 조회를 실패시키지 않도록 격리한다. */
    private void safeCacheWrite(Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException ignored) {
            // DB 조회 결과를 정상 반환하고 다음 요청에서 캐시 복구를 다시 시도한다.
        }
    }

    private record Cursor(LocalDate rankingDate, int position) {}
}
