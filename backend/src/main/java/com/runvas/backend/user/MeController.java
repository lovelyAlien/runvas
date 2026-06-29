package com.runvas.backend.user;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.user.dto.UpdateMeRequest;
import com.runvas.backend.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	@GetMapping("/me")
	public UserResponse getMe() {
		return UserResponse.from(findCurrentUser());
	}

	@PatchMapping("/me")
	@Transactional
	public UserResponse updateMe(@Valid @RequestBody UpdateMeRequest request) {
		User user = findCurrentUser();
		if (request.nickname() != null) {
			user.setNickname(request.nickname());
		}
		if (request.profileImageUrl() != null) {
			user.setProfileImageUrl(request.profileImageUrl());
		}
		if (request.bio() != null) {
			user.setBio(request.bio());
		}
		return UserResponse.from(user);
	}

	private User findCurrentUser() {
		return userRepository.findById(currentUserProvider.requireUserId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다"));
	}
}
