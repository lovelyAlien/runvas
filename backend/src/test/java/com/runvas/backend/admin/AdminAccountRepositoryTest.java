package com.runvas.backend.admin;

import java.util.Optional;
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
class AdminAccountRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    AdminAccountRepository adminAccountRepository;

    @Test
    void savesAndFindsAccountByUsername() {
        adminAccountRepository.saveAndFlush(new AdminAccount("operator", "hashed-value"));

        Optional<AdminAccount> found = adminAccountRepository.findByUsername("operator");

        assertThat(found).isPresent();
        assertThat(found.get().getPasswordHash()).isEqualTo("hashed-value");
        assertThat(found.get().getLastLoginAt()).isNull();
    }

    @Test
    void returnsEmptyWhenUsernameNotFound() {
        Optional<AdminAccount> found = adminAccountRepository.findByUsername("missing");

        assertThat(found).isEmpty();
    }
}
