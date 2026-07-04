package org.profit.candle.market.stream;

import com.google.protobuf.Timestamp;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.subscription.ViewerDemand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 라이브 시세 뷰어 팬아웃.
 *
 * 뷰어가 {@link #subscribe} 하면 심볼별 스트림 집합에 등록하고 viewing 수요를 획득한다. WS 수신부가
 * {@link #publish} 로 도메인 시세를 넣으면 해당 심볼 구독자에게만 proto 로 변환해 보낸다.
 *
 * 뷰어 이탈 시 수요 해제는 grace 지연을 둔다 — 상세 새로고침으로 잠깐 0명이 됐다가 곧 재구독하는
 * 경우의 REG/REMOVE 플래핑을 막는다. 지연 중 재구독이 들어오면 ref-count 가 0으로 떨어지지 않아
 * 해제가 상쇄된다.
 *
 * @see docs/REALTIME_QUOTE_PIPELINE.md 3. 컴포넌트 흐름
 */
@Slf4j
@Component
public class QuoteStreamBroker implements LiveQuoteSink {

    /** 한 뷰어의 스트림 + 구독 심볼. */
    public static final class Subscriber {
        private final LiveQuoteStream stream;
        private final List<String> symbols;

        Subscriber(LiveQuoteStream stream, List<String> symbols) {
            this.stream = stream;
            this.symbols = symbols;
        }
    }

    private final ViewerDemand viewerDemand;
    private final long graceMillis;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Set<Subscriber>> subscribersBySymbol = new ConcurrentHashMap<>();

    public QuoteStreamBroker(
            // @Lazy 로 client → broker → manager → client 빈 순환을 끊는다.
            // (KiwoomWebSocketClient 가 LiveQuoteSink=broker 를, broker 가 ViewerDemand=manager 를,
            //  manager 가 RealtimeSubscriptionPort=client 를 참조)
            @Lazy ViewerDemand viewerDemand,
            @Value("${market.ws.viewer-grace-ms:5000}") long graceMillis) {
        this.viewerDemand = viewerDemand;
        this.graceMillis = graceMillis;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "viewer-grace");
            t.setDaemon(true);
            return t;
        });
    }

    /** 뷰어 스트림을 등록하고 viewing 수요를 획득한다. 클라이언트 취소 시 자동 해제된다. */
    public Subscriber subscribe(List<String> symbols, LiveQuoteStream stream) {
        Subscriber sub = new Subscriber(stream, List.copyOf(symbols));
        sub.symbols.forEach(s ->
                subscribersBySymbol.computeIfAbsent(s, k -> ConcurrentHashMap.newKeySet()).add(sub));
        viewerDemand.acquireViewer(sub.symbols);
        stream.onClose(() -> unsubscribe(sub));
        return sub;
    }

    void unsubscribe(Subscriber sub) {
        sub.symbols.forEach(s -> {
            Set<Subscriber> set = subscribersBySymbol.get(s);
            if (set != null) {
                set.remove(sub);
            }
        });
        if (graceMillis <= 0) {
            viewerDemand.releaseViewer(sub.symbols);
        } else {
            scheduler.schedule(() -> viewerDemand.releaseViewer(sub.symbols),
                    graceMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void publish(org.profit.candle.market.dto.LiveQuote quote) {
        Set<Subscriber> subscribers = subscribersBySymbol.get(quote.symbol());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        org.profit.candle.proto.market.v1.LiveQuote proto = toProto(quote);
        for (Subscriber sub : subscribers) {
            if (sub.stream.isOpen()) {
                sub.stream.send(proto);
            }
        }
    }

    private org.profit.candle.proto.market.v1.LiveQuote toProto(org.profit.candle.market.dto.LiveQuote q) {
        return org.profit.candle.proto.market.v1.LiveQuote.newBuilder()
                .setSymbol(q.symbol())
                .setPrice(q.price())
                .setChange(q.change())
                .setChangeRate(q.changeRate())
                .setOpenPrice(q.openPrice())
                .setTradingVolume(q.tradingVolume())
                .setPriceChangeSign(q.priceChangeSign() == null ? "" : q.priceChangeSign())
                .setTs(toTimestamp(q.timestamp()))
                .build();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
