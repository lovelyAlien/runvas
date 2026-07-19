package com.runvas.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false, length = 100)
    private String providerUserId;

    @Column(length = 320)
    private String email;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(length = 1000)
    private String profileImageUrl;

    @Column(length = 160)
    private String bio;

    @Column(nullable = false)
    private int runningPaceSecPerKm = 360;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant deletedAt;

    protected User() {
    }

    public static User createKakaoUser(String providerUserId, String email, String nickname, String profileImageUrl) {
        User user = new User();
        user.provider = AuthProvider.KAKAO;
        user.providerUserId = providerUserId;
        user.email = email;
        user.nickname = nickname == null || nickname.isBlank() ? "Runvas Runner" : nickname;
        user.profileImageUrl = profileImageUrl;
        user.bio = null;
        return user;
    }

    public static User createDevUser(String nickname) {
        User user = new User();
        user.provider = AuthProvider.DEV;
        user.providerUserId = nickname;
        user.email = null;
        user.nickname = nickname;
        user.profileImageUrl = null;
        user.bio = null;
        return user;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AuthProvider getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public String getBio() { return bio; }
    public int getRunningPaceSecPerKm() { return runningPaceSecPerKm; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markWithdrawn() {
        this.deletedAt = Instant.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public void updateProfile(String nickname, String profileImageUrl, String bio, Integer runningPaceSecPerKm) {
        if (nickname != null) this.nickname = nickname;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
        if (bio != null) this.bio = bio;
        if (runningPaceSecPerKm != null) this.runningPaceSecPerKm = runningPaceSecPerKm;
    }
}
