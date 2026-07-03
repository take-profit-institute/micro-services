package org.profit.candle.news.article.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.news.article.dto.StockNewsArticleResult;
import org.profit.candle.news.article.dto.StockNewsResult;
import org.profit.candle.news.article.service.NewsArticleQueryService;
import org.profit.candle.proto.news.v1.GetStockNewsRequest;
import org.profit.candle.proto.news.v1.GetStockNewsResponse;
import org.profit.candle.proto.news.v1.NewsArticle;
import org.profit.candle.proto.news.v1.NewsServiceGrpc;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsGrpcService extends NewsServiceGrpc.NewsServiceImplBase {
    private final NewsArticleQueryService newsArticleQueryService;

    @Override
    public void getStockNews(
            GetStockNewsRequest request,
            StreamObserver<GetStockNewsResponse> observer
    ) {
        try {
            String stockCode = requireStockCode(request.getStockCode());
            StockNewsResult result = newsArticleQueryService.getStockNews(stockCode);
            GetStockNewsResponse.Builder response = GetStockNewsResponse.newBuilder();
            result.articles().forEach(article -> response.addArticles(toProto(article)));
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription("NEWS_INVALID_STOCK_CODE")
                    .asRuntimeException());
        } catch (RuntimeException e) {
            log.error("GetStockNews failed. stock_code={}", request.getStockCode(), e);
            observer.onError(Status.INTERNAL
                    .withDescription("NEWS_INTERNAL_ERROR")
                    .asRuntimeException());
        }
    }

    private static String requireStockCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("stock_code is required");
        }
        return value.trim();
    }

    private static NewsArticle toProto(StockNewsArticleResult result) {
        NewsArticle.Builder builder = NewsArticle.newBuilder()
                .setId(result.id().toString())
                .setTitle(result.title())
                .setUrl(result.url())
                .setCreatedAt(toTimestamp(result.createdAt()));
        if (result.contentSummary() != null) {
            builder.setContentSummary(result.contentSummary());
        }
        if (result.source() != null) {
            builder.setSource(result.source());
        }
        if (result.publishedAt() != null) {
            builder.setPublishedAt(toTimestamp(result.publishedAt()));
        }
        return builder.build();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
