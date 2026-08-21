package org.profit.candle.user.profile.repository;

import java.util.Optional;
import org.profit.candle.user.profile.entity.UserProfileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserProfileReader {

    Optional<UserProfileEntity> findById(String userId);

    boolean existsById(String userId);

    boolean existsByNickname(String nickname);

    /**
     * 관리자 콘솔용 회원 검색.
     *
     * @param pattern     닉네임/이메일에 매칭할 소문자 LIKE 패턴. 전체 조회는 {@code "%"}
     * @param allStatuses true면 탈퇴 여부를 가리지 않는다({@code deleted} 무시)
     * @param deleted     {@code allStatuses}가 false일 때 매칭할 탈퇴 여부
     */
    Page<UserProfileEntity> search(String pattern, boolean allStatuses, boolean deleted, Pageable pageable);
}
