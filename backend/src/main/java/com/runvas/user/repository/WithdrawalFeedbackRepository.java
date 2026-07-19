package com.runvas.user.repository;

import com.runvas.user.domain.WithdrawalFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalFeedbackRepository extends JpaRepository<WithdrawalFeedback, String> {
}
