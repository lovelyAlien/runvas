package com.runvas.user.service;

import com.runvas.auth.service.TokenBlacklistService;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.User;
import com.runvas.user.domain.WithdrawalFeedback;
import com.runvas.user.domain.WithdrawalReason;
import com.runvas.user.repository.UserRepository;
import com.runvas.user.repository.WithdrawalFeedbackRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountWithdrawalService {

    private static final int MAX_REASON_DETAIL_LENGTH = 200;

    private final UserRepository userRepository;
    private final WithdrawalFeedbackRepository withdrawalFeedbackRepository;
    private final TokenBlacklistService tokenBlacklistService;

    public AccountWithdrawalService(
            UserRepository userRepository,
            WithdrawalFeedbackRepository withdrawalFeedbackRepository,
            TokenBlacklistService tokenBlacklistService
    ) {
        this.userRepository = userRepository;
        this.withdrawalFeedbackRepository = withdrawalFeedbackRepository;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Transactional
    public void withdraw(UUID userId, String token, WithdrawalReason reason, String reasonDetail) {
        if (reason == WithdrawalReason.OTHER && (reasonDetail == null || reasonDetail.isBlank())) {
            throw new RunvasException(ErrorCode.VALIDATION_ERROR, "reasonDetail is required when reason is OTHER");
        }
        if (reasonDetail != null && reasonDetail.length() > MAX_REASON_DETAIL_LENGTH) {
            throw new RunvasException(ErrorCode.VALIDATION_ERROR, "reasonDetail must be at most 200 characters");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED));

        if (!user.isDeleted()) {
            user.markWithdrawn();
            userRepository.save(user);
            withdrawalFeedbackRepository.save(WithdrawalFeedback.of(reason, reasonDetail));
        }

        tokenBlacklistService.blacklist(token);
    }
}
