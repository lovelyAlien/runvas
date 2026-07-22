package com.runvas.backend.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// 최초 배포 시 admin_accounts가 비어 있으면 환경변수로 계정 1건을 부트스트랩한다.
// docs/superpowers/specs/2026-07-21-admin-dashboard-design.md "초기 계정 부트스트랩" 참고.
@Component
public class AdminAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedUsername;
    private final String seedPassword;

    public AdminAccountSeeder(
            AdminAccountRepository adminAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${runvas.admin.seed-username:}") String seedUsername,
            @Value("${runvas.admin.seed-password:}") String seedPassword) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedUsername = seedUsername;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedUsername.isBlank() || seedPassword.isBlank()) {
            return;
        }
        if (adminAccountRepository.count() > 0) {
            return;
        }
        adminAccountRepository.save(new AdminAccount(seedUsername, passwordEncoder.encode(seedPassword)));
        log.info("Seeded initial admin account: {}", seedUsername);
    }
}
