package org.profit.candle.market.stream;

import org.junit.jupiter.api.Test;
import org.profit.candle.market.subscription.ViewerDemand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteStreamBrokerTest {

    private static final class FakeStream implements LiveQuoteStream {
        boolean open = true;
        Runnable onClose;
        final List<org.profit.candle.proto.market.v1.LiveQuote> received = new ArrayList<>();

        @Override public boolean isOpen() { return open; }
        @Override public void send(org.profit.candle.proto.market.v1.LiveQuote q) { received.add(q); }
        @Override public void onClose(Runnable handler) { this.onClose = handler; }
    }

    private static final class FakeDemand implements ViewerDemand {
        final List<Collection<String>> acquired = new ArrayList<>();
        final List<Collection<String>> released = new ArrayList<>();

        @Override public void acquireViewer(Collection<String> s) { acquired.add(new ArrayList<>(s)); }
        @Override public void releaseViewer(Collection<String> s) { released.add(new ArrayList<>(s)); }
    }

    private org.profit.candle.market.dto.LiveQuote quote(String symbol) {
        return new org.profit.candle.market.dto.LiveQuote(
                symbol, 299000L, 13000L, 4.55, 288500L, 15442974L, "2",
                Instant.parse("2026-07-03T01:47:09Z"));
    }

    // grace=0 → 해제가 동기적으로 일어나 테스트가 결정적이다
    private QuoteStreamBroker broker(FakeDemand demand) {
        return new QuoteStreamBroker(demand, 0L);
    }

    @Test
    void subscribe_acquiresViewerDemand() {
        FakeDemand demand = new FakeDemand();
        broker(demand).subscribe(List.of("005930"), new FakeStream());

        assertThat(demand.acquired).containsExactly(List.of("005930"));
    }

    @Test
    void publish_routesOnlyToSubscribersOfThatSymbol() {
        FakeDemand demand = new FakeDemand();
        QuoteStreamBroker broker = broker(demand);
        FakeStream samsung = new FakeStream();
        FakeStream hynix = new FakeStream();
        broker.subscribe(List.of("005930"), samsung);
        broker.subscribe(List.of("000660"), hynix);

        broker.publish(quote("005930"));

        assertThat(samsung.received).hasSize(1);
        assertThat(samsung.received.get(0).getSymbol()).isEqualTo("005930");
        assertThat(samsung.received.get(0).getOpenPrice()).isEqualTo(288500L);
        assertThat(hynix.received).isEmpty();
    }

    @Test
    void publish_toSymbolWithNoSubscribers_isNoop() {
        QuoteStreamBroker broker = broker(new FakeDemand());
        broker.publish(quote("999999")); // 예외 없이 무시
    }

    @Test
    void publish_skipsClosedStreams() {
        QuoteStreamBroker broker = broker(new FakeDemand());
        FakeStream closed = new FakeStream();
        closed.open = false;
        broker.subscribe(List.of("005930"), closed);

        broker.publish(quote("005930"));

        assertThat(closed.received).isEmpty();
    }

    @Test
    void onClose_releasesViewerDemand_andStopsFanout() {
        FakeDemand demand = new FakeDemand();
        QuoteStreamBroker broker = broker(demand);
        FakeStream stream = new FakeStream();
        broker.subscribe(List.of("005930"), stream);

        stream.onClose.run(); // 클라이언트 취소 시뮬레이션

        assertThat(demand.released).containsExactly(List.of("005930"));

        broker.publish(quote("005930"));
        assertThat(stream.received).isEmpty(); // 더 이상 팬아웃되지 않음
    }
}
