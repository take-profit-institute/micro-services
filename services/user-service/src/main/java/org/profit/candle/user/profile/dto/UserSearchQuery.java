package org.profit.candle.user.profile.dto;

/**
 * 관리자 콘솔의 회원 목록 조회 조건.
 *
 * <p>gRPC 요청은 proto3 기본값(빈 문자열/0)으로 "미지정"이 들어오므로, 정규화는 어댑터가 아니라
 * 이 레코드가 책임진다. 페이지 크기 상한을 두는 이유는 관리자 화면 실수(size=100000)로
 * 전체 테이블을 한 번에 끌어오는 일을 막기 위해서다.
 */
public record UserSearchQuery(String keyword, Boolean deleted, int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public UserSearchQuery {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /**
     * 검색어를 소문자 LIKE 패턴으로 바꾼다. 검색어가 없으면 {@code "%"} — email이 NOT NULL이라
     * 모든 행이 매칭되므로 별도 분기 없이 "전체 조회"가 된다.
     */
    public String pattern() {
        return keyword == null ? "%" : "%" + keyword.toLowerCase() + "%";
    }

    /** 탈퇴 여부 필터 미지정 여부. 쿼리에서 null 파라미터를 피하려고 boolean 두 개로 풀어 넘긴다. */
    public boolean allStatuses() {
        return deleted == null;
    }

    /** {@link #allStatuses()}가 false일 때만 의미 있는 값. */
    public boolean deletedFlag() {
        return Boolean.TRUE.equals(deleted);
    }
}
