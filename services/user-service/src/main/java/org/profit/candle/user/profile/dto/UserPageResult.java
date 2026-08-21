package org.profit.candle.user.profile.dto;

import java.util.List;

/**
 * 회원 목록 한 페이지. {@code page}는 요청과 같은 0-based 기준으로 돌려준다
 * (1-based 변환은 화면 계약을 소유한 BFF가 한다).
 */
public record UserPageResult(List<UserProfileResult> users, long totalCount, int page, int size) {
}
