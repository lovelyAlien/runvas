package com.runvas.user.controller;

import com.runvas.backend.community.BookmarkService;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.global.security.RunvasPrincipal;
import com.runvas.user.domain.User;
import com.runvas.user.dto.MeResponse;
import com.runvas.user.dto.UpdateMeRequest;
import com.runvas.user.dto.UserResponse;
import com.runvas.user.dto.WithdrawRequest;
import com.runvas.user.repository.UserRepository;
import com.runvas.user.service.AccountWithdrawalService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserRepository userRepository;
    private final BookmarkService bookmarkService;
    private final AccountWithdrawalService accountWithdrawalService;

    public MeController(
            UserRepository userRepository,
            BookmarkService bookmarkService,
            AccountWithdrawalService accountWithdrawalService
    ) {
        this.userRepository = userRepository;
        this.bookmarkService = bookmarkService;
        this.accountWithdrawalService = accountWithdrawalService;
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal RunvasPrincipal principal) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(principal.userId())
                .map(user -> new MeResponse(UserResponse.from(user)))
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED));
    }

    @GetMapping("/me/bookmarked-courses")
    Map<String, Object> listBookmarkedCourses(@AuthenticationPrincipal RunvasPrincipal principal) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        BookmarkService.ListResult result = bookmarkService.listByUser();
        return Map.of("courses", result.courses(), "pageInfo", result.pageInfo());
    }

    @PatchMapping("/me")
    MeResponse updateMe(
            @AuthenticationPrincipal RunvasPrincipal principal,
            @RequestBody @Valid UpdateMeRequest request
    ) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED));
        user.updateProfile(request.nickname(), request.profileImageUrl(), request.bio(), request.runningPaceSecPerKm());
        userRepository.save(user);
        return new MeResponse(UserResponse.from(user));
    }

    @DeleteMapping("/me")
    ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal RunvasPrincipal principal,
            Authentication authentication,
            @RequestBody @Valid WithdrawRequest request
    ) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        String token = (String) authentication.getCredentials();
        accountWithdrawalService.withdraw(principal.userId(), token, request.reason(), request.reasonDetail());
        return ResponseEntity.noContent().build();
    }
}
