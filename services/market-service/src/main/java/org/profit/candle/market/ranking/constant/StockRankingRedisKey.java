package org.profit.candle.market.ranking.constant;

public final class StockRankingRedisKey {

    public static final String RISING = "market:stock-ranking:rising";
    public static final String FALLING = "market:stock-ranking:falling";
    public static final String VOLUME_SPIKE = "market:stock-ranking:volume-spike";
    public static final String POPULAR = "market:stock-ranking:popular";
    private StockRankingRedisKey() {
    }
}
