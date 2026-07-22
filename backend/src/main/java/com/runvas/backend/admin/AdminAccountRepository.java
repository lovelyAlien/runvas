package com.runvas.backend.admin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, String> {

    Optional<AdminAccount> findByUsername(String username);
}
