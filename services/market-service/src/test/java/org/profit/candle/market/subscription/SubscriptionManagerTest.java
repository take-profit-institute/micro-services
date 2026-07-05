package org.profit.candle.market.subscription;

import org.junit.jupiter.api.Test;
import org.profit.candle.market.websocket.RealtimeRegistrationListener;
import org.profit.candle.market.websocket.RealtimeSubscriptionPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionManagerTest {

    /** REG/REMOVE 호출을 기록하는 가짜 포트. */
    private static final class FakePort implements RealtimeSubscriptionPort {
        boolean connected;
        final List<Set<String>> registers = new ArrayList<>();
        final List<Set<String>> unregisters = new ArrayList<>();

        @Override public boolean isConnected() { return connected; }
        @Override public void register(Collection<String> symbols) { registers.add(new HashSet<>(symbols)); }
        @Override public void unregister(Collection<String> symbols) { unregisters.add(new HashSet<>(symbols)); }
        @Override public void setRegistrationListener(RealtimeRegistrationListener listener) { }
    }

    private SubscriptionManager manager(FakePort port, String... seeds) {
        return new SubscriptionManager(port, List.of(seeds));
    }

    @Test
    void onConnectionReady_returnsSeededDesiredSet() {
        FakePort port = new FakePort();
        SubscriptionManager manager = manager(port, "005930", "000660");
        port.connected = true;

        Collection<String> desired = manager.onConnectionReady();

        assertThat(desired).containsExactlyInAnyOrder("005930", "000660");
    }

    @Test
    void acquireViewer_whenConnected_registersOnlyNewSymbol() {
        FakePort port = new FakePort();
        SubscriptionManager manager = manager(port, "005930");
        port.connected = true;
        manager.onConnectionReady(); // registered = {005930}

        manager.acquireViewer(List.of("035420"));

        assertThat(port.registers).containsExactly(Set.of("035420"));
        assertThat(port.unregisters).isEmpty();
    }

    @Test
    void acquireViewer_forAlreadyDesiredSymbol_doesNotReRegister() {
        FakePort port = new FakePort();
        SubscriptionManager manager = manager(port, "005930");
        port.connected = true;
        manager.onConnectionReady();

        manager.acquireViewer(List.of("005930")); // 이미 seed 로 desired

        assertThat(port.registers).isEmpty();
    }

    @Test
    void viewer_isRefCounted_removeOnlyWhenLastReleased() {
        FakePort port = new FakePort();
        SubscriptionManager manager = manager(port); // seed 없음
        port.connected = true;
        manager.onConnectionReady();

        manager.acquireViewer(List.of("035420"));
        manager.acquireViewer(List.of("035420")); // 두 번째 뷰어
        manager.releaseViewer(List.of("035420")); // 아직 뷰어 1명 남음

        assertThat(port.registers).containsExactly(Set.of("035420"));
        assertThat(port.unregisters).isEmpty();

        manager.releaseViewer(List.of("035420")); // 마지막 뷰어 이탈

        assertThat(port.unregisters).containsExactly(Set.of("035420"));
    }

    @Test
    void union_wishlistDeactivate_keepsSymbolWhileViewerHoldsIt() {
        FakePort port = new FakePort();
        SubscriptionManager manager = manager(port, "005930"); // wishlist 시드
        port.connected = true;
        manager.onConnectionReady();

        manager.acquireViewer(List.of("005930"));   // 뷰어도 보고 있음
        manager.deactivateWishlist("005930");       // wishlist 에서 빠져도

        assertThat(port.unregisters).isEmpty();      // 뷰어가 잡고 있어 유지

        manager.releaseViewer(List.of("005930"));   // 뷰어까지 이탈해야
        assertThat(port.unregisters).containsExactly(Set.of("005930"));
    }

    @Test
    void whenDisconnected_demandAccumulates_andRegistersOnNextConnect() {
        FakePort port = new FakePort();
        SubscriptionManager manager = manager(port, "005930");
        port.connected = false;

        manager.acquireViewer(List.of("035420")); // 미연결 → 프레임 없음

        assertThat(port.registers).isEmpty();

        port.connected = true;
        Collection<String> desired = manager.onConnectionReady();

        assertThat(desired).containsExactlyInAnyOrder("005930", "035420");
    }

    @Test
    void onConnectionLost_clearsRegistered_soReconnectReRegistersAll() {
        FakePort port = new FakePort();
        SubscriptionManager manager = manager(port, "005930");
        port.connected = true;
        manager.onConnectionReady();

        manager.onConnectionLost();
        port.connected = false;

        // 재연결
        port.connected = true;
        assertThat(manager.onConnectionReady()).containsExactly("005930");
    }
}
