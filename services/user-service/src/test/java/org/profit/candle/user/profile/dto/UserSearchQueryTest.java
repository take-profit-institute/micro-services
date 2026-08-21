package org.profit.candle.user.profile.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSearchQueryTest {

    @Test
    void blankKeyword_becomesMatchAllPattern() {
        // gRPC proto3는 "미지정"을 빈 문자열로 보낸다 → 전체 조회로 해석돼야 한다.
        assertThat(new UserSearchQuery("", null, 0, 20).pattern()).isEqualTo("%");
        assertThat(new UserSearchQuery("   ", null, 0, 20).pattern()).isEqualTo("%");
        assertThat(new UserSearchQuery(null, null, 0, 20).pattern()).isEqualTo("%");
    }

    @Test
    void keyword_isTrimmedLowercasedAndWrapped() {
        assertThat(new UserSearchQuery("  KimCoder  ", null, 0, 20).pattern()).isEqualTo("%kimcoder%");
    }

    @Test
    void size_fallsBackToDefaultAndIsCapped() {
        assertThat(new UserSearchQuery(null, null, 0, 0).size()).isEqualTo(UserSearchQuery.DEFAULT_SIZE);
        assertThat(new UserSearchQuery(null, null, 0, -5).size()).isEqualTo(UserSearchQuery.DEFAULT_SIZE);
        assertThat(new UserSearchQuery(null, null, 0, 100_000).size()).isEqualTo(UserSearchQuery.MAX_SIZE);
        assertThat(new UserSearchQuery(null, null, 0, 37).size()).isEqualTo(37);
    }

    @Test
    void negativePage_isClampedToZero() {
        assertThat(new UserSearchQuery(null, null, -3, 20).page()).isZero();
    }

    @Test
    void deletedFilter_unspecifiedMeansAllStatuses() {
        UserSearchQuery all = new UserSearchQuery(null, null, 0, 20);
        assertThat(all.allStatuses()).isTrue();

        UserSearchQuery active = new UserSearchQuery(null, false, 0, 20);
        assertThat(active.allStatuses()).isFalse();
        assertThat(active.deletedFlag()).isFalse();

        UserSearchQuery withdrawn = new UserSearchQuery(null, true, 0, 20);
        assertThat(withdrawn.allStatuses()).isFalse();
        assertThat(withdrawn.deletedFlag()).isTrue();
    }
}
