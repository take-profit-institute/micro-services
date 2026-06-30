package org.profit.candle.market.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name="stocks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stoks_code", columnNames = "code")
        },
        indexes = {
                @Index(name="idx_stocks_name", columnList = "name"),
                @Index(name = "idx_stocks_deleted_at", columnList = "deleted_at")
        }
)
@Getter
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
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Stock(String code, String name, String market) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String name, String market) {
        this.name = name;
        this.market = market;
        this.updatedAt = LocalDateTime.now();
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }
}