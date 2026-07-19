package com.runvas.user.repository;

import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    List<User> findByDeletedAtLessThanEqual(Instant threshold);
}
