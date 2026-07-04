package org.profit.candle.market.websocket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 키움 실시간 등록의 grp_no 샤딩 할당기.
 *
 * 그룹당 등록 심볼 수 상한이 있어(정확한 값은 키움 문서 확인 필요, 보수적으로 설정), 심볼을 여러
 * grp_no 에 나눠 담는다. 증분 REG/REMOVE 가 올바른 그룹을 타도록 심볼→그룹 할당을 안정적으로
 * 유지하고, 빈 슬롯을 재사용해 그룹 수를 최소화한다.
 *
 * 스레드 안전하지 않다 — 호출자(KiwoomWebSocketClient)가 동기화한다.
 */
final class SubscriptionGroups {

    private final int maxPerGroup;
    private final Map<String, String> groupBySymbol = new HashMap<>();
    private final Map<String, List<String>> symbolsByGroup = new LinkedHashMap<>();
    private int nextGroup = 1;

    SubscriptionGroups(int maxPerGroup) {
        this.maxPerGroup = Math.max(1, maxPerGroup);
    }

    /** 심볼을 그룹에 배정한다. 이미 배정된 건 건너뛴다. 반환: grp_no → 이번에 새로 배정된 심볼(REG 용). */
    Map<String, List<String>> assign(Collection<String> symbols) {
        Map<String, List<String>> added = new LinkedHashMap<>();
        for (String symbol : symbols) {
            if (groupBySymbol.containsKey(symbol)) {
                continue;
            }
            String group = groupWithSpace();
            groupBySymbol.put(symbol, group);
            symbolsByGroup.get(group).add(symbol);
            added.computeIfAbsent(group, k -> new ArrayList<>()).add(symbol);
        }
        return added;
    }

    /** 심볼 배정을 해제한다. 반환: grp_no → 이번에 빠진 심볼(REMOVE 용). */
    Map<String, List<String>> release(Collection<String> symbols) {
        Map<String, List<String>> removed = new LinkedHashMap<>();
        for (String symbol : symbols) {
            String group = groupBySymbol.remove(symbol);
            if (group == null) {
                continue;
            }
            List<String> members = symbolsByGroup.get(group);
            if (members != null) {
                members.remove(symbol);
                removed.computeIfAbsent(group, k -> new ArrayList<>()).add(symbol);
                if (members.isEmpty()) {
                    symbolsByGroup.remove(group);
                }
            }
        }
        return removed;
    }

    /** 전체 배정 스냅샷: grp_no → 심볼. 재연결 후 전체 재등록에 쓴다. */
    Map<String, List<String>> snapshot() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        symbolsByGroup.forEach((group, members) -> out.put(group, List.copyOf(members)));
        return out;
    }

    void reset() {
        groupBySymbol.clear();
        symbolsByGroup.clear();
        nextGroup = 1;
    }

    private String groupWithSpace() {
        for (Map.Entry<String, List<String>> entry : symbolsByGroup.entrySet()) {
            if (entry.getValue().size() < maxPerGroup) {
                return entry.getKey();
            }
        }
        String group = String.valueOf(nextGroup++);
        symbolsByGroup.put(group, new ArrayList<>());
        return group;
    }
}
