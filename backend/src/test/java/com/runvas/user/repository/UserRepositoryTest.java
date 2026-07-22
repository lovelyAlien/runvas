package com.runvas.user.repository;

import com.runvas.backend.admin.DailyCountProjection;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
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
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    UserRepository userRepository;

    @Test
    void findsUserByProviderAndProviderUserId() {
        User user = User.createKakaoUser(
                "12345",
                "runner@example.com",
                "Seoul Runner",
                null
        );
        userRepository.saveAndFlush(user);

        Optional<User> found = userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "12345");

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("Seoul Runner");
    }

    @Test
    void searchFindsUserByNicknameOrEmailIgnoringCase() {
        userRepository.saveAndFlush(User.createKakaoUser("k1", "seoul@example.com", "서울러너", null));
        userRepository.saveAndFlush(User.createKakaoUser("k2", "busan@example.com", "부산러너", null));

        org.springframework.data.domain.Page<User> found =
                userRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "서울", "서울", org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().get(0).getNickname()).isEqualTo("서울러너");
    }

    @Test
    void countDailySinceGroupsUsersByCreationDate() {
        userRepository.saveAndFlush(User.createKakaoUser("k3", "a@example.com", "가입자A", null));
        userRepository.saveAndFlush(User.createKakaoUser("k4", "b@example.com", "가입자B", null));

        java.util.List<DailyCountProjection> counts =
                userRepository.countDailySince(java.time.Instant.now().minusSeconds(3600));

        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).getCnt()).isEqualTo(2L);
    }
}
