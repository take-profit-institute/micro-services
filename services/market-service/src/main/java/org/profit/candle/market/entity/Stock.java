package org.profit.candle.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Entity
@Table(
        name="stocks",
        indexes = {
                @Index(name="idx_stocks_name", columnList = "name"),
                @Index(name = "idx_stocks_deleted_at", columnList = "deleted_at")
        }
)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //키움 종목코드
    @Column(nullable = false, length = 20)
    private String code;

    //종목명
    @Column(nullable = false, length = 100)
    private String name;

    //시장 구분
    @Column(length = 20)
    private String market;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Stock(String code, String name, String market) {
        Instant now = Instant.now();
        this.code = code;
        this.name = name;
        this.market = market;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String market) {
        this.name = name;
        this.market = market;
        this.updatedAt = Instant.now();
    }

    public void delete() {
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }
}
