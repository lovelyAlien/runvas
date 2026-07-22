package com.runvas.user.repository;

import com.runvas.backend.admin.DailyCountProjection;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    List<User> findByDeletedAtLessThanEqual(Instant threshold);

    Page<User> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String nickname, String email, Pageable pageable);

    @Query("select cast(u.createdAt as date) as day, count(u) as cnt from User u "
            + "where u.createdAt >= :since group by cast(u.createdAt as date)")
    List<DailyCountProjection> countDailySince(@Param("since") Instant since);
}
