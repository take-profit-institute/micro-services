package org.profit.candle.market.websocket;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionGroupsTest {

    @Test
    void assign_fillsGroupToLimit_thenOpensNewGroup() {
        SubscriptionGroups groups = new SubscriptionGroups(2);

        Map<String, List<String>> added = groups.assign(List.of("A", "B", "C"));

        assertThat(added).containsKeys("1", "2");
        assertThat(added.get("1")).containsExactly("A", "B");
        assertThat(added.get("2")).containsExactly("C");
    }

    @Test
    void assign_isIdempotentForAlreadyAssignedSymbols() {
        SubscriptionGroups groups = new SubscriptionGroups(10);
        groups.assign(List.of("A", "B"));

        Map<String, List<String>> added = groups.assign(List.of("A", "B", "C"));

        assertThat(added).containsOnlyKeys("1");
        assertThat(added.get("1")).containsExactly("C"); // 새로 배정된 것만 반환
    }

    @Test
    void release_returnsPerGroupRemovals_andFreesSlots() {
        SubscriptionGroups groups = new SubscriptionGroups(2);
        groups.assign(List.of("A", "B", "C")); // grp1={A,B}, grp2={C}

        Map<String, List<String>> removed = groups.release(List.of("A"));
        assertThat(removed).containsOnlyKeys("1");
        assertThat(removed.get("1")).containsExactly("A");

        // 빈 슬롯(grp1) 재사용
        Map<String, List<String>> added = groups.assign(List.of("D"));
        assertThat(added.get("1")).containsExactly("D");
    }

    @Test
    void release_dropsEmptyGroup() {
        SubscriptionGroups groups = new SubscriptionGroups(2);
        groups.assign(List.of("A", "B", "C")); // grp2={C}
        groups.release(List.of("C"));

        assertThat(groups.snapshot()).doesNotContainKey("2");
    }

    @Test
    void snapshot_reflectsCurrentAssignment() {
        SubscriptionGroups groups = new SubscriptionGroups(2);
        groups.assign(List.of("A", "B", "C"));

        assertThat(groups.snapshot()).containsOnlyKeys("1", "2");
        assertThat(groups.snapshot().get("1")).containsExactly("A", "B");
    }

    @Test
    void reset_clearsAllAndRestartsGroupNumbering() {
        SubscriptionGroups groups = new SubscriptionGroups(2);
        groups.assign(List.of("A", "B", "C"));
        groups.reset();

        Map<String, List<String>> added = groups.assign(List.of("X"));
        assertThat(added).containsOnlyKeys("1");
        assertThat(groups.snapshot()).containsOnlyKeys("1");
    }
}
