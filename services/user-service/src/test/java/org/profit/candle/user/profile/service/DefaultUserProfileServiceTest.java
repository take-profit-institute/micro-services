package org.profit.candle.user.profile.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.user.profile.dto.UpdateProfileCommand;
import org.profit.candle.user.profile.dto.UserPageResult;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.profit.candle.user.profile.dto.UserSearchQuery;
import org.profit.candle.user.profile.entity.UserProfileEntity;
import org.profit.candle.user.profile.event.OutboxWriter;
import org.profit.candle.user.profile.exception.UserErrorCode;
import org.profit.candle.user.profile.repository.UserProfileReader;
import org.profit.candle.user.profile.repository.UserProfileWriter;

import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultUserProfileServiceTest {

    @Mock UserProfileReader userProfileReader;
    @Mock UserProfileWriter userProfileWriter;
    @Mock OutboxWriter outboxWriter;
    @InjectMocks DefaultUserProfileService service;

    private static final String USER_ID = "user-1";

    @Test
    void getProfile_returnsResult() {
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", "nick", "url");
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));

        UserProfileResult result = service.getProfile(USER_ID);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.email()).isEqualTo("a@b.com");
        assertThat(result.nickname()).isEqualTo("nick");
        assertThat(result.deleted()).isFalse();
    }

    @Test
    void getProfile_notFound_throwsUserNotFound() {
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(USER_ID))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    void updateProfile_happyPath_savesAndWritesOutbox() {
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", "old", "old-url");
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(userProfileWriter.save(entity)).thenReturn(entity);

        UpdateProfileCommand command = new UpdateProfileCommand("new", "new-url");
        service.updateProfile(USER_ID, command);

        verify(userProfileWriter).save(entity);
        verify(outboxWriter).writeUserProfileUpdated(any(UserProfileResult.class));
    }

    @Test
    void updateProfile_notFound_throwsUserNotFound() {
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand("nick", "url")))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));

        verify(outboxWriter, never()).writeUserProfileUpdated(any());
    }

    @Test
    void updateProfile_nicknameTooLong_throwsNicknameTooLong() {
        String tooLong = "a".repeat(51);
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", null, null);
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand(tooLong, null)))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.NICKNAME_TOO_LONG));

        verify(outboxWriter, never()).writeUserProfileUpdated(any());
    }

    @Test
    void updateProfile_nicknameAtMaxLength_succeeds() {
        String maxLength = "a".repeat(50);
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", null, null);
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(userProfileWriter.save(entity)).thenReturn(entity);

        service.updateProfile(USER_ID, new UpdateProfileCommand(maxLength, null));

        verify(userProfileWriter).save(entity);
    }

    @Test
    void updateProfile_profileImageUrlTooLong_throwsUrlTooLong() {
        String tooLong = "x".repeat(501);
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", null, null);
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand(null, tooLong)))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.PROFILE_IMAGE_URL_TOO_LONG));
    }

    @Test
    void updateProfile_nullNicknameAndUrl_skipsValidationAndSaves() {
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", "nick", "url");
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(userProfileWriter.save(entity)).thenReturn(entity);

        service.updateProfile(USER_ID, new UpdateProfileCommand(null, null));

        verify(userProfileWriter).save(entity);
        verify(outboxWriter).writeUserProfileUpdated(any());
    }

    // ── 관리자 회원 목록 ─────────────────────────────────────────────

    @Test
    void listUsers_returnsPageWithZeroBasedPageNumber() {
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", "nick", "url");
        when(userProfileReader.search(anyString(), anyBoolean(), anyBoolean(), any(Pageable.class)))
                // total은 offset(40) + pageSize(20) 이상이어야 한다 — 그보다 작으면 PageImpl이
                // "마지막 페이지"로 보고 total을 offset+content.size()로 깎는다.
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(2, 20), 60));

        UserPageResult result = service.listUsers(new UserSearchQuery(null, null, 2, 20));

        assertThat(result.users()).singleElement()
                .satisfies(u -> assertThat(u.userId()).isEqualTo(USER_ID));
        assertThat(result.totalCount()).isEqualTo(60);
        assertThat(result.page()).isEqualTo(2); // 0-based 그대로 — 1-based 변환은 BFF 몫
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    void listUsers_sortsByNewestSignupWithStableTieBreaker() {
        when(userProfileReader.search(anyString(), anyBoolean(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listUsers(new UserSearchQuery(null, null, 0, 20));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userProfileReader).search(anyString(), anyBoolean(), anyBoolean(), pageable.capture());
        // created_at 동률에서 페이지 경계가 흔들리지 않으려면 tie-breaker가 반드시 있어야 한다.
        assertThat(pageable.getValue().getSort()).containsExactly(
                Sort.Order.desc("createdAt"), Sort.Order.asc("userId"));
    }

    @Test
    void listUsers_passesNormalizedFilters() {
        when(userProfileReader.search(anyString(), anyBoolean(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listUsers(new UserSearchQuery("  KimCoder ", true, 0, 20));

        verify(userProfileReader).search(eq("%kimcoder%"), eq(false), eq(true), any(Pageable.class));
    }
}
