package org.profit.candle.market.subscription;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.websocket.RealtimeRegistrationListener;
import org.profit.candle.market.websocket.RealtimeSubscriptionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 키움 실시간 구독 심볼의 단일 소유자.
 *
 * desired = viewing(뷰어 ref-count) ∪ wishlist(활성 집합). 이 desired 를 키움에 실제 등록된 집합과
 * diff 해서 REG/REMOVE 만 보낸다. 연결이 없으면 desired 만 갱신하고, 다음 로그인 시
 * {@link #onConnectionReady()} 가 전체를 재등록한다.
 *
 * 수요원은 나중에 붙는다:
 * <ul>
 *   <li>viewing: {@link #acquireViewer}/{@link #releaseViewer} — Phase 4(StreamQuotes gRPC)</li>
 *   <li>wishlist: {@link #activateWishlist}/{@link #deactivateWishlist} — Phase 5(Kafka 이벤트)</li>
 * </ul>
 *
 * @see docs/REALTIME_QUOTE_PIPELINE.md 7. 구독 재조정 로직
 */
@Slf4j
@Component
public class SubscriptionManager implements RealtimeRegistrationListener, ViewerDemand {

    private final RealtimeSubscriptionPort port;

    private final Map<String, Integer> viewerCounts = new HashMap<>();
    private final Set<String> wishlistActive = new HashSet<>();
    private final Set<String> registered = new HashSet<>();

    public SubscriptionManager(
            RealtimeSubscriptionPort port,
            @Value("${market.ws.seed-symbols:005930,000660,035420}") List<String> seedSymbols) {
        this.port = port;
        // 시드는 로컬 개발용 폴백. 운영 구독은 wishlist Kafka 이벤트가 채운다.
        // 빈 프로퍼티는 [""] 로 바인딩되므로 공백을 걸러낸다.
        seedSymbols.stream()
                .filter(s -> s != null && !s.isBlank())
                .forEach(this.wishlistActive::add);
    }

    @PostConstruct
    void register() {
        port.setRegistrationListener(this);
    }

    /** viewing 수요 획득(뷰어가 상세를 열 때). ref-count 0→1 인 심볼만 새로 REG 된다. */
    @Override
    public synchronized void acquireViewer(Collection<String> symbols) {
        symbols.forEach(s -> viewerCounts.merge(s, 1, Integer::sum));
        reconcile();
    }

    /** viewing 수요 해제(뷰어가 상세를 닫을 때). ref-count 1→0 인 심볼만 REMOVE 된다. */
    @Override
    public synchronized void releaseViewer(Collection<String> symbols) {
        symbols.forEach(s -> viewerCounts.computeIfPresent(s, (k, v) -> v <= 1 ? null : v - 1));
        reconcile();
    }

    /** wishlist 수요 활성(해당 심볼을 가진 유저 0→1). */
    public synchronized void activateWishlist(String symbol) {
        if (wishlistActive.add(symbol)) {
            reconcile();
        }
    }

    /** wishlist 수요 비활성(해당 심볼을 가진 유저 1→0). */
    public synchronized void deactivateWishlist(String symbol) {
        if (wishlistActive.remove(symbol)) {
            reconcile();
        }
    }

    @Override
    public synchronized Collection<String> onConnectionReady() {
        // 재연결 → 키움 등록 소실. desired 전체를 registered 로 확정하고 클라이언트가 REG 한다.
        registered.clear();
        registered.addAll(desired());
        log.info("키움 실시간 재등록 대상 {}건", registered.size());
        return Set.copyOf(registered);
    }

    @Override
    public synchronized void onConnectionLost() {
        registered.clear();
    }

    private Set<String> desired() {
        Set<String> desired = new HashSet<>(viewerCounts.keySet());
        desired.addAll(wishlistActive);
        return desired;
    }

    /** 연결 중일 때만 증분 REG/REMOVE 를 보낸다. 미연결이면 desired 만 바뀌고 로그인 시 반영된다. */
    private void reconcile() {
        if (!port.isConnected()) {
            return;
        }
        Set<String> desired = desired();

        Set<String> toAdd = new HashSet<>(desired);
        toAdd.removeAll(registered);

        Set<String> toRemove = new HashSet<>(registered);
        toRemove.removeAll(desired);

        if (!toAdd.isEmpty()) {
            port.register(toAdd);
            registered.addAll(toAdd);
        }
        if (!toRemove.isEmpty()) {
            port.unregister(toRemove);
            registered.removeAll(toRemove);
        }
    }
}
