package com.runvas.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// docs/data-model.md WithdrawalReason — 사용자 식별자를 전혀 포함하지 않는 익명 통계 기록.
// 계정이 나중에 하드 삭제되어도 이 테이블의 행은 영향받지 않는다.
@Entity
@Table(name = "withdrawal_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30)
    private WithdrawalReason reasonCode;

    @Column(name = "reason_detail", length = 200)
    private String reasonDetail;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private WithdrawalFeedback(WithdrawalReason reasonCode, String reasonDetail) {
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
    }

    public static WithdrawalFeedback of(WithdrawalReason reasonCode, String reasonDetail) {
        return new WithdrawalFeedback(reasonCode, reasonDetail);
    }
}
