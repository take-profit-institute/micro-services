package org.profit.candle.ranking.ranking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ranking_participants")
public class RankingParticipant {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "nickname", nullable = false, length = 100)
    private String nickname;

    @Column(name = "trade_count", nullable = false)
    private int tradeCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false, length = 20)
    private ParticipantStatus userStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private ParticipantStatus accountStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA가 엔티티를 조회할 때 사용한다. */
    protected RankingParticipant() {}

    /** 프로필 이벤트를 처음 받은 사용자의 기본 투영 데이터를 생성한다. */
    private RankingParticipant(UUID userId, String nickname, Instant occurredAt) {
        this.userId = userId;
        this.nickname = nickname;
        this.tradeCount = 0;
        this.userStatus = ParticipantStatus.UNKNOWN;
        this.accountStatus = ParticipantStatus.UNKNOWN;
        this.createdAt = occurredAt;
        this.updatedAt = occurredAt;
    }

    /** 프로필 정보로 새로운 랭킹 참가자를 생성한다. */
    public static RankingParticipant fromProfile(UUID userId, String nickname, Instant occurredAt) {
        return new RankingParticipant(userId, nickname, occurredAt);
    }

    /** 현재 정보보다 오래되지 않은 이벤트일 때 닉네임을 갱신한다. */
    public void updateNickname(String nickname, Instant occurredAt) {
        if (occurredAt.isBefore(updatedAt)) {
            return;
        }
        this.nickname = nickname;
        this.updatedAt = occurredAt;
    }

    /** 사용자 식별자를 반환한다. */
    public UUID userId() {
        return userId;
    }

    /** 랭킹에 표시할 닉네임을 반환한다. */
    public String nickname() {
        return nickname;
    }

    /** 누적 체결 횟수를 반환한다. */
    public int tradeCount() {
        return tradeCount;
    }

    /** 사용자의 현재 활동 상태를 반환한다. */
    public ParticipantStatus userStatus() {
        return userStatus;
    }

    /** 거래 계좌의 현재 활동 상태를 반환한다. */
    public ParticipantStatus accountStatus() {
        return accountStatus;
    }
}
