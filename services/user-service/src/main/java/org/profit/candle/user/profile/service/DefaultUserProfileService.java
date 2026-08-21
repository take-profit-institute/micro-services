package org.profit.candle.user.profile.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.user.profile.dto.UpdateProfileCommand;
import org.profit.candle.user.profile.dto.UserPageResult;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.profit.candle.user.profile.dto.UserSearchQuery;
import org.profit.candle.user.profile.entity.UserProfileEntity;
import org.profit.candle.user.profile.event.OutboxWriter;
import org.profit.candle.user.profile.exception.UserErrorCode;
import org.profit.candle.user.profile.exception.UserException;
import org.profit.candle.user.profile.repository.UserProfileReader;
import org.profit.candle.user.profile.repository.UserProfileWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultUserProfileService implements UserProfileService {

    private final UserProfileReader userProfileReader;
    private final UserProfileWriter userProfileWriter;
    private final OutboxWriter outboxWriter;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResult getProfile(String userId) {
        UserProfileEntity profile = userProfileReader.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        return UserProfileResult.from(profile);
    }

    @Override
    @Transactional
    public UserProfileResult updateProfile(String userId, UpdateProfileCommand command) {
        UserProfileEntity profile = userProfileReader.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        profile.updateProfile(command.nickname(), command.profileImageUrl());
        UserProfileResult result = UserProfileResult.from(userProfileWriter.save(profile));
        outboxWriter.writeUserProfileUpdated(result);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPageResult listUsers(UserSearchQuery query) {
        // 최신 가입순 — 관리자가 "방금 들어온 회원"부터 보는 게 기본 관심사다.
        // created_at 동률(같은 초에 유입된 배치성 가입)에서 페이지 경계가 흔들리지 않도록
        // user_id를 tie-breaker로 둔다. 이게 없으면 같은 회원이 두 페이지에 걸쳐 보이거나 누락된다.
        PageRequest pageable = PageRequest.of(
                query.page(),
                query.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("userId")));

        Page<UserProfileEntity> page = userProfileReader.search(
                query.pattern(), query.allStatuses(), query.deletedFlag(), pageable);

        return new UserPageResult(
                page.getContent().stream().map(UserProfileResult::from).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }
}
