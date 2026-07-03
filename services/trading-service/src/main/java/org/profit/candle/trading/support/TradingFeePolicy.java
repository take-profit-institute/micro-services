package org.profit.candle.trading.support;

import java.math.BigDecimal;

/**
 * 주문/예약 체결 수수료·세금 정책 상수.
 *
 * <p>order(시장가/지정가 즉시 체결)와 reservation(종가 배치 체결) 두 도메인이 공유한다.
 * 정책 변경 시 이 클래스만 수정하면 된다.</p>
 *
 * <pre>
 * 매수 수수료: 체결금액 × 0.015%
 * 매도 수수료: 체결금액 × 0.015%
 * 거래세:     체결금액 × 0.18% (매도만)
 * </pre>
 */
public final class TradingFeePolicy {

    private TradingFeePolicy() {}

    /** 매수/매도 공통 수수료율: 0.015% */
    public static final BigDecimal FEE_RATE = new BigDecimal("0.00015");

    /** 매도 거래세율: 0.18% */
    public static final BigDecimal TAX_RATE = new BigDecimal("0.0018");
}