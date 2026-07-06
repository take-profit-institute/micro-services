package org.profit.candle.market.subscription;

import java.util.Collection;

/**
 * viewing 수요 입력 경계. 뷰어 팬아웃(QuoteStreamBroker)이 구독 소유자에게 수요를 알린다.
 * 좁은 인터페이스로 두어 브로커를 SubscriptionManager 없이 단위 테스트할 수 있다.
 */
public interface ViewerDemand {

    void acquireViewer(Collection<String> symbols);

    void releaseViewer(Collection<String> symbols);
}
