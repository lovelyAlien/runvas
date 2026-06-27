package com.runvas.backend.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
	Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId);
}
