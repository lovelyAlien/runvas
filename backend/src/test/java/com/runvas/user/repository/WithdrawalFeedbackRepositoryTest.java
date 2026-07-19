package com.runvas.user.repository;

import com.runvas.user.domain.WithdrawalFeedback;
import com.runvas.user.domain.WithdrawalReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WithdrawalFeedbackRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    WithdrawalFeedbackRepository withdrawalFeedbackRepository;

    @Test
    void savesFeedbackWithoutAnyUserReference() {
        WithdrawalFeedback feedback = WithdrawalFeedback.of(WithdrawalReason.OTHER, "탈퇴 사유 직접입력");

        withdrawalFeedbackRepository.saveAndFlush(feedback);

        WithdrawalFeedback found = withdrawalFeedbackRepository.findById(feedback.getId()).orElseThrow();
        assertThat(found.getReasonCode()).isEqualTo(WithdrawalReason.OTHER);
        assertThat(found.getReasonDetail()).isEqualTo("탈퇴 사유 직접입력");
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
