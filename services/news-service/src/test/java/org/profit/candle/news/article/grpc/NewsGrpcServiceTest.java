package org.profit.candle.news.article.grpc;

import io.grpc.stub.StreamObserver;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.profit.candle.news.article.dto.StockNewsArticleResult;
import org.profit.candle.news.article.dto.StockNewsResult;
import org.profit.candle.news.article.service.NewsArticleQueryService;
import org.profit.candle.proto.news.v1.GetStockNewsRequest;
import org.profit.candle.proto.news.v1.GetStockNewsResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NewsGrpcServiceTest {
    @Test
    void shouldReturnAtMostThreeNewsInLatestOrderByStockCode() {
        NewsGrpcService service = new NewsGrpcService(stockCode -> new StockNewsResult(List.of(
                article("latest", "2026-07-03T03:00:00Z", "2026-07-03T03:10:00Z"),
                article("middle", "2026-07-03T02:00:00Z", "2026-07-03T02:10:00Z"),
                article("oldest", "2026-07-03T01:00:00Z", "2026-07-03T01:10:00Z")
        )));
        RecordingObserver<GetStockNewsResponse> observer = new RecordingObserver<>();

        service.getStockNews(GetStockNewsRequest.newBuilder()
                .setStockCode("005930")
                .build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.value.getArticlesCount()).isEqualTo(3);
        assertThat(observer.value.getArticlesList())
                .extracting(org.profit.candle.proto.news.v1.NewsArticle::getTitle)
                .containsExactly("latest", "middle", "oldest");
        assertThat(observer.value.getArticlesList())
                .extracting(article -> article.getPublishedAt().getSeconds())
                .containsExactly(
                        Instant.parse("2026-07-03T03:00:00Z").getEpochSecond(),
                        Instant.parse("2026-07-03T02:00:00Z").getEpochSecond(),
                        Instant.parse("2026-07-03T01:00:00Z").getEpochSecond()
                );
    }

    @Test
    void shouldReturnEmptyArrayWhenNoNewsExists() {
        NewsArticleQueryService queryService = stockCode -> new StockNewsResult(List.of());
        NewsGrpcService service = new NewsGrpcService(queryService);
        RecordingObserver<GetStockNewsResponse> observer = new RecordingObserver<>();

        service.getStockNews(GetStockNewsRequest.newBuilder()
                .setStockCode("005930")
                .build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.value.getArticlesCount()).isZero();
    }

    @Test
    void shouldReturnInternalWhenQueryFails() {
        NewsGrpcService service = new NewsGrpcService(stockCode -> {
            throw new IllegalStateException("database unavailable");
        });
        RecordingObserver<GetStockNewsResponse> observer = new RecordingObserver<>();

        service.getStockNews(GetStockNewsRequest.newBuilder()
                .setStockCode("005930")
                .build(), observer);

        StatusRuntimeException error = (StatusRuntimeException) observer.error;
        assertThat(Status.fromThrowable(error).getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    private static StockNewsArticleResult article(String title, String publishedAt, String createdAt) {
        return new StockNewsArticleResult(
                UUID.randomUUID(),
                title,
                title + " summary",
                "https://news.example.com/" + title,
                "NAVER",
                Instant.parse(publishedAt),
                Instant.parse(createdAt)
        );
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
