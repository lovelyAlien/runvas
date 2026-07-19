package com.runvas.user.service;

import com.runvas.auth.service.TokenBlacklistService;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.User;
import com.runvas.user.domain.WithdrawalFeedback;
import com.runvas.user.domain.WithdrawalReason;
import com.runvas.user.repository.UserRepository;
import com.runvas.user.repository.WithdrawalFeedbackRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountWithdrawalServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final WithdrawalFeedbackRepository withdrawalFeedbackRepository = mock(WithdrawalFeedbackRepository.class);
    private final TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
    private final AccountWithdrawalService accountWithdrawalService =
            new AccountWithdrawalService(userRepository, withdrawalFeedbackRepository, tokenBlacklistService);

    private static User persistedUser() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Seoul Runner", null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void marksUserWithdrawnRecordsFeedbackAndBlacklistsToken() {
        User user = persistedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        accountWithdrawalService.withdraw(user.getId(), "token-1", WithdrawalReason.NOT_USING, null);

        assertThat(user.isDeleted()).isTrue();
        verify(userRepository).save(user);
        verify(withdrawalFeedbackRepository).save(any(WithdrawalFeedback.class));
        verify(tokenBlacklistService).blacklist("token-1");
    }

    @Test
    void rejectsOtherReasonWithoutDetail() {
        User user = persistedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accountWithdrawalService.withdraw(user.getId(), "token-1", WithdrawalReason.OTHER, null))
                .isInstanceOfSatisfying(RunvasException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(userRepository, never()).save(any());
        verify(tokenBlacklistService, never()).blacklist(any());
    }

    @Test
    void rejectsReasonDetailLongerThan200Characters() {
        User user = persistedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        String tooLong = "a".repeat(201);

        assertThatThrownBy(() -> accountWithdrawalService.withdraw(user.getId(), "token-1", WithdrawalReason.OTHER, tooLong))
                .isInstanceOfSatisfying(RunvasException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void secondWithdrawCallOnAlreadyDeletedUserOnlyBlacklistsToken() {
        User user = persistedUser();
        user.markWithdrawn();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        accountWithdrawalService.withdraw(user.getId(), "token-2", WithdrawalReason.NOT_USING, null);

        verify(userRepository, never()).save(any());
        verify(withdrawalFeedbackRepository, never()).save(any());
        verify(tokenBlacklistService).blacklist("token-2");
    }
}
