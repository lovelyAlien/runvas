package com.runvas.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAccountSeederTest {

    private final AdminAccountRepository adminAccountRepository = mock(AdminAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void seedsAccountWhenCredentialsPresentAndTableEmpty() {
        when(adminAccountRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");
        AdminAccountSeeder seeder =
                new AdminAccountSeeder(adminAccountRepository, passwordEncoder, "operator", "secret");

        seeder.run(null);

        verify(adminAccountRepository, times(1)).save(any(AdminAccount.class));
    }

    @Test
    void doesNothingWhenSeedCredentialsMissing() {
        AdminAccountSeeder seeder =
                new AdminAccountSeeder(adminAccountRepository, passwordEncoder, "", "");

        seeder.run(null);

        verify(adminAccountRepository, never()).save(any(AdminAccount.class));
    }

    @Test
    void doesNothingWhenAccountsAlreadyExist() {
        when(adminAccountRepository.count()).thenReturn(1L);
        AdminAccountSeeder seeder =
                new AdminAccountSeeder(adminAccountRepository, passwordEncoder, "operator", "secret");

        seeder.run(null);

        verify(adminAccountRepository, never()).save(any(AdminAccount.class));
    }
}
