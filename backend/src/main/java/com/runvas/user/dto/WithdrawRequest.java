package com.runvas.user.dto;

import com.runvas.user.domain.WithdrawalReason;
import jakarta.validation.constraints.NotNull;

public record WithdrawRequest(@NotNull WithdrawalReason reason, String reasonDetail) {
}
