package org.profit.candle.news.collector;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.news.article.entity.Article;
import org.profit.candle.news.article.repository.ArticleJpaRepository;
import org.profit.candle.news.log.entity.CollectionLog;
import org.profit.candle.news.log.entity.CollectionStatusType;
import org.profit.candle.news.log.repository.CollectionLogJpaRepository;
import org.profit.candle.news.mapping.repository.ArticleStockMappingJpaRepository;
import org.profit.candle.news.naver.client.NaverNewsClient;
import org.profit.candle.news.naver.dto.NaverNewsItem;
import org.profit.candle.news.naver.dto.NaverNewsSearchRequest;
import org.profit.candle.news.naver.dto.NaverNewsSearchResult;
import org.profit.candle.news.naver.exception.NaverNewsApiException;
import org.profit.candle.news.stock.GrpcStockClient;
import org.profit.candle.news.stock.StockClient;
import org.profit.candle.news.stock.StockGrpcProperties;
import org.profit.candle.news.stock.StockSnapshot;
import org.profit.candle.news.target.entity.CollectionTarget;
import org.profit.candle.news.target.repository.CollectionTargetJpaRepository;
import org.profit.candle.proto.stock.v1.GetStockRequest;
import org.profit.candle.proto.stock.v1.GetStockResponse;
import org.profit.candle.proto.stock.v1.Stock;
import org.profit.candle.proto.stock.v1.StockDetail;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.SERVER_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsCollectionServiceTest {
    @Mock
    private CollectionTargetJpaRepository targetRepository;

    @Mock
    private StockClient stockClient;

    @Mock
    private NaverNewsClient naverNewsClient;

    @Mock
    private ArticleJpaRepository articleRepository;

    @Mock
    private ArticleStockMappingJpaRepository mappingRepository;

    @Mock
    private CollectionLogJpaRepository logRepository;

    private NewsCollectionService service;

    @BeforeEach
    void setUp() {
        NewsCollectionWriter writer = new NewsCollectionWriter(
                articleRepository,
                mappingRepository,
                logRepository
        );
        service = new NewsCollectionService(
                targetRepository,
                stockClient,
                naverNewsClient,
                writer
        );
    }

    @Test
    void shouldSaveSameUrlNewsOnlyOnceAndAvoidDuplicateStockMapping() {
        CollectionTarget target = target("005930");
        when(targetRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(target));
        when(stockClient.getStock("005930")).thenReturn(stock("005930", "삼성전자"));
        when(stockClient.getStock("005930")).thenReturn(stock("005930", "Samsung Electronics"));
        when(naverNewsClient.search(any(NaverNewsSearchRequest.class))).thenReturn(new NaverNewsSearchResult(
                2,
                1,
                2,
                List.of(
                        item("뉴스1", "https://news.example.com/a"),
                        item("뉴스1 중복", "https://news.example.com/a")
                )
        ));

        Map<String, Article> articlesByUrl = new HashMap<>();
        Set<String> mappings = new HashSet<>();
        when(articleRepository.findByUrl(any())).thenAnswer(invocation ->
                Optional.ofNullable(articlesByUrl.get(invocation.getArgument(0, String.class))));
        when(articleRepository.insertIgnore(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            String url = invocation.getArgument(2, String.class);
            if (articlesByUrl.containsKey(url)) {
                return 0;
            }
            Article article = Article.create(
                    invocation.getArgument(0, String.class),
                    invocation.getArgument(1, String.class),
                    url,
                    invocation.getArgument(3, String.class),
                    invocation.getArgument(4, Instant.class)
            );
            setField(article, "id", UUID.randomUUID());
            articlesByUrl.put(article.getUrl(), article);
            return 1;
        });
        when(mappingRepository.insertIgnore(any(UUID.class), eq("005930"), any())).thenAnswer(invocation -> {
            String key = mappingKey(
                    invocation.getArgument(0, UUID.class),
                    invocation.getArgument(1, String.class)
            );
            return mappings.add(key) ? 1 : 0;
        });

        NewsCollectionResult result = service.collectActiveTargets();

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(articlesByUrl).hasSize(1);
        assertThat(mappings).hasSize(1);
        verify(articleRepository, times(2)).insertIgnore(any(), any(), any(), any(), any());
        verify(mappingRepository, times(2)).insertIgnore(any(UUID.class), eq("005930"), any());
    }

    @Test
    void shouldContinueWhenArticleAndMappingAlreadyExistByUniqueConstraint() {
        CollectionTarget target = target("005930");
        Article existingArticle = Article.create(
                "existing",
                "summary",
                "https://news.example.com/a",
                "NAVER",
                Instant.parse("2026-07-03T00:00:00Z")
        );
        setField(existingArticle, "id", UUID.randomUUID());
        when(targetRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(target));
        when(stockClient.getStock("005930")).thenReturn(stock("005930", "Samsung Electronics"));
        when(naverNewsClient.search(any(NaverNewsSearchRequest.class))).thenReturn(new NaverNewsSearchResult(
                1,
                1,
                1,
                List.of(item("news", "https://news.example.com/a"))
        ));
        when(articleRepository.insertIgnore(any(), any(), any(), any(), any())).thenReturn(0);
        when(articleRepository.findByUrl("https://news.example.com/a")).thenReturn(Optional.of(existingArticle));
        when(mappingRepository.insertIgnore(existingArticle.getId(), "005930", "Samsung Electronics")).thenReturn(0);

        NewsCollectionResult result = service.collectActiveTargets();

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failCount()).isZero();
    }

    @Test
    void shouldRecordFailLogWhenStockGrpcLookupFails() {
        when(targetRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(target("005930")));
        when(stockClient.getStock("005930")).thenThrow(new IllegalStateException("stock unavailable"));
        List<CollectionLog> logs = captureLogs();

        NewsCollectionResult result = service.collectActiveTargets();

        assertThat(result.failCount()).isEqualTo(1);
        assertThat(logs).singleElement()
                .extracting(CollectionLog::getStatus)
                .isEqualTo(CollectionStatusType.fail);
        assertThat(logs.get(0).getMessage())
                .contains("stock_code=005930")
                .contains("reason=STOCK_LOOKUP_FAILED")
                .doesNotContain("stock unavailable");
        verify(naverNewsClient, never()).search(any());
    }

    @Test
    void shouldRecordFailLogWhenNaverApiFails() {
        when(targetRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(target("005930")));
        when(stockClient.getStock("005930")).thenReturn(stock("005930", "삼성전자"));
        when(naverNewsClient.search(any(NaverNewsSearchRequest.class)))
                .thenThrow(new NaverNewsApiException(SERVER_ERROR, new IllegalStateException("secret response body")));
        List<CollectionLog> logs = captureLogs();

        NewsCollectionResult result = service.collectActiveTargets();

        assertThat(result.failCount()).isEqualTo(1);
        assertThat(logs).singleElement()
                .extracting(CollectionLog::getStatus)
                .isEqualTo(CollectionStatusType.fail);
        assertThat(logs.get(0).getMessage())
                .contains("stock_code=005930")
                .contains("reason=NAVER_SERVER_ERROR")
                .doesNotContain("secret response body");
    }

    @Test
    void shouldRecordPartialFailLogWhenOnlySomeTargetsFail() {
        when(targetRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(
                target("005930"),
                target("000660")
        ));
        when(stockClient.getStock("005930")).thenReturn(stock("005930", "삼성전자"));
        when(stockClient.getStock("000660")).thenThrow(new IllegalStateException("stock unavailable"));
        when(naverNewsClient.search(any(NaverNewsSearchRequest.class))).thenReturn(new NaverNewsSearchResult(
                0,
                1,
                0,
                List.of()
        ));
        List<CollectionLog> logs = captureLogs();

        NewsCollectionResult result = service.collectActiveTargets();

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failCount()).isEqualTo(1);
        assertThat(logs).singleElement()
                .extracting(CollectionLog::getStatus)
                .isEqualTo(CollectionStatusType.partial_fail);
    }

    @Test
    void grpcStockClientShouldCallStockServiceWithAllowFallbackFalse() {
        CapturingChannel channel = new CapturingChannel(GetStockResponse.newBuilder()
                .setStock(StockDetail.newBuilder()
                        .setStock(Stock.newBuilder()
                                .setCode("005930")
                                .setName("삼성전자")
                                .build())
                        .build())
                .build());
        GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
        when(channelFactory.createChannel("stock-service")).thenReturn(channel);
        GrpcStockClient client = new GrpcStockClient(
                channelFactory,
                new StockGrpcProperties(Duration.ofSeconds(3))
        );

        client.getStock("005930");

        assertThat(channel.requests).singleElement()
                .satisfies(request -> {
                    assertThat(request.getCode()).isEqualTo("005930");
                    assertThat(request.getAllowFallback()).isFalse();
                });
        assertThat(channel.hasDeadline).isTrue();
    }

    @Test
    void shouldSaveOnlyNewsThatContainsStockNameInTitleOrDescription() {
        CollectionTarget target = target("005930");
        when(targetRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(target));
        when(stockClient.getStock("005930")).thenReturn(stock("005930", "Samsung Electronics"));
        when(naverNewsClient.search(any(NaverNewsSearchRequest.class))).thenReturn(new NaverNewsSearchResult(
                3,
                1,
                3,
                List.of(
                        new NaverNewsItem(
                                "Samsung Electronics earnings",
                                "https://original.example.com/title",
                                "https://naver.example.com/title",
                                "market news",
                                Instant.parse("2026-07-03T00:00:00Z")
                        ),
                        new NaverNewsItem(
                                "Semiconductor update",
                                "https://original.example.com/description",
                                "https://naver.example.com/description",
                                "Samsung Electronics expands investment",
                                Instant.parse("2026-07-03T00:00:00Z")
                        ),
                        new NaverNewsItem(
                                "Unrelated market news",
                                "https://original.example.com/unrelated",
                                "https://naver.example.com/unrelated",
                                "general index update",
                                Instant.parse("2026-07-03T00:00:00Z")
                        )
                )
        ));

        Map<String, Article> articlesByUrl = new HashMap<>();
        when(articleRepository.findByUrl(any())).thenAnswer(invocation ->
                Optional.ofNullable(articlesByUrl.get(invocation.getArgument(0, String.class))));
        when(articleRepository.insertIgnore(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            String url = invocation.getArgument(2, String.class);
            Article article = Article.create(
                    invocation.getArgument(0, String.class),
                    invocation.getArgument(1, String.class),
                    url,
                    invocation.getArgument(3, String.class),
                    invocation.getArgument(4, Instant.class)
            );
            setField(article, "id", UUID.randomUUID());
            articlesByUrl.put(article.getUrl(), article);
            return 1;
        });
        when(mappingRepository.insertIgnore(any(UUID.class), eq("005930"), any())).thenReturn(1);

        NewsCollectionResult result = service.collectActiveTargets();

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(articlesByUrl).containsOnlyKeys(
                "https://original.example.com/title",
                "https://original.example.com/description"
        );
        verify(articleRepository, times(2)).insertIgnore(any(), any(), any(), any(), any());
        verify(mappingRepository, times(2)).insertIgnore(any(UUID.class), eq("005930"), any());
    }

    private List<CollectionLog> captureLogs() {
        List<CollectionLog> logs = new ArrayList<>();
        when(logRepository.save(any(CollectionLog.class))).thenAnswer(invocation -> {
            CollectionLog log = invocation.getArgument(0, CollectionLog.class);
            logs.add(log);
            return log;
        });
        return logs;
    }

    private static NaverNewsItem item(String title, String url) {
        return new NaverNewsItem(
                title + " Samsung Electronics ?쇱꽦?꾩옄",
                url,
                url,
                title + " Samsung Electronics ?쇱꽦?꾩옄",
                Instant.parse("2026-07-03T00:00:00Z")
        );
    }

    private static StockSnapshot stock(String code, String name) {
        return new StockSnapshot(code, name, "KOSPI", null, 0L, 0L, "LISTED");
    }

    private static CollectionTarget target(String stockCode) {
        try {
            Constructor<CollectionTarget> constructor = CollectionTarget.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            CollectionTarget target = constructor.newInstance();
            setField(target, "id", UUID.randomUUID());
            setField(target, "stockCode", stockCode);
            return target;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static String mappingKey(UUID articleId, String stockCode) {
        return articleId + ":" + stockCode;
    }

    private static final class CapturingChannel extends ManagedChannel {
        private final GetStockResponse response;
        private final List<GetStockRequest> requests = new ArrayList<>();
        private boolean hasDeadline;

        private CapturingChannel(GetStockResponse response) {
            this.response = response;
        }

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor,
                CallOptions callOptions
        ) {
            hasDeadline = callOptions.getDeadline() != null;
            return new ClientCall<>() {
                private Listener<ResponseT> listener;

                @Override
                public void start(Listener<ResponseT> listener, Metadata headers) {
                    this.listener = listener;
                }

                @Override
                public void request(int numMessages) {
                }

                @Override
                public void cancel(String message, Throwable cause) {
                }

                @Override
                public void halfClose() {
                    listener.onMessage((ResponseT) response);
                    listener.onClose(io.grpc.Status.OK, new Metadata());
                }

                @Override
                public void sendMessage(RequestT message) {
                    requests.add((GetStockRequest) message);
                }
            };
        }

        @Override
        public String authority() {
            return "test";
        }

        @Override
        public ManagedChannel shutdown() {
            return this;
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public ManagedChannel shutdownNow() {
            return this;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
