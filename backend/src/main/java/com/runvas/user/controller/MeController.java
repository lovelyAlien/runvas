package com.runvas.user.controller;

import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.global.security.RunvasPrincipal;
import com.runvas.user.domain.User;
import com.runvas.user.dto.MeResponse;
import com.runvas.user.dto.UpdateMeRequest;
import com.runvas.user.dto.UserResponse;
import com.runvas.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}
