package org.profit.candle.market.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name="ticks")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String stockCode;
    private Long currentPrice;
    private Long priceChange;
    private BigDecimal priceChangeRate;
    private String priceChangeSign;
    private Long tradingVolume;
    private OffsetDateTime tickedAt;
    private OffsetDateTime collectedAt;
}
