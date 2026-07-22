package com.runvas.backend.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
public class AdminSecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    DaoAuthenticationProvider adminAuthenticationProvider(
            AdminUserDetailsService adminUserDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationSuccessHandler adminLoginSuccessHandler(AdminAccountRepository adminAccountRepository) {
        return (request, response, authentication) -> {
            adminAccountRepository.findByUsername(authentication.getName())
                    .ifPresent(account -> {
                        account.recordLogin();
                        adminAccountRepository.save(account);
                    });
            response.sendRedirect(request.getContextPath() + "/admin/dashboard");
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider adminAuthenticationProvider,
            AuthenticationSuccessHandler adminLoginSuccessHandler) throws Exception {
        return http
                .securityMatcher("/admin/**")
                .authenticationProvider(adminAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(adminLoginSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login")
                        .permitAll()
                )
                .build();
    }
}
