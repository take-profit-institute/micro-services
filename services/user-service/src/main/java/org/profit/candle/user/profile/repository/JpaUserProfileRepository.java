package org.profit.candle.user.profile.repository;

import java.util.Optional;
import org.profit.candle.user.profile.entity.UserProfileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserProfileRepository
        extends JpaRepository<UserProfileEntity, String>, UserProfileReader, UserProfileWriter {

    @Override
    Optional<UserProfileEntity> findById(String userId);

    @Override
    boolean existsById(String userId);

    @Override
    boolean existsByNickname(String nickname);

    @Override
    UserProfileEntity save(UserProfileEntity profile);

    /**
     * 닉네임은 nullable이라 {@code LOWER(nickname) LIKE :pattern}이 NULL(=거짓)로 떨어진다.
     * email은 NOT NULL이므로 검색어가 없을 때({@code pattern = "%"}) 모든 행이 email 쪽에서 매칭된다.
     *
     * <p>모든 파라미터를 non-null로 받는 이유: {@code :param IS NULL} 형태는 PostgreSQL에서
     * 파라미터 타입 추론이 실패할 수 있어, 탈퇴 필터를 boolean 두 개(allStatuses/deleted)로 풀었다.
     */
    @Override
    @Query("""
            SELECT p FROM UserProfileEntity p
            WHERE (LOWER(p.email) LIKE :pattern OR LOWER(p.nickname) LIKE :pattern)
              AND (:allStatuses = TRUE OR p.deleted = :deleted)
            """)
    Page<UserProfileEntity> search(@Param("pattern") String pattern,
                                   @Param("allStatuses") boolean allStatuses,
                                   @Param("deleted") boolean deleted,
                                   Pageable pageable);
}
