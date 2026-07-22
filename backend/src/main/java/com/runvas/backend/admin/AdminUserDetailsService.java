package com.runvas.backend.admin;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository adminAccountRepository;

    public AdminUserDetailsService(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AdminAccount account = adminAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown admin account: " + username));
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities("ROLE_ADMIN")
                .build();
    }
}
