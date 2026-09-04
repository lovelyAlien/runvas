package com.runvas.backend.community;

import com.runvas.backend.admin.AdminNotificationService;
import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.community.dto.PublicProfile;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockService {

	private final BlockRepository blockRepository;
	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;
	private final AdminNotificationService adminNotificationService;

	@Transactional
	public Result block(String targetUserIdPathValue) {
		String blockerId = currentUserProvider.requireUserId();
		String blockedId = parseUserId(targetUserIdPathValue);

		if (blockerId.equals(blockedId)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "자기 자신은 차단할 수 없습니다");
		}

		User blockedUser = userRepository.findById(UUID.fromString(blockedId))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자가 없습니다"));

		Block.BlockId blockId = new Block.BlockId(blockerId, blockedId);
		Optional<Block> existing = blockRepository.findById(blockId);
		Block block = existing.orElseGet(() -> blockRepository.save(new Block(blockerId, blockedId)));

		BlockResponse response = new BlockResponse(PublicProfile.from(blockedUser), block.getCreatedAt());
		if (existing.isEmpty()) {
			adminNotificationService.notifyBlock(blockerId, blockedId);
		}
		return new Result(response, existing.isEmpty());
	}

	@Transactional
	public void unblock(String targetUserIdPathValue) {
		String blockerId = currentUserProvider.requireUserId();
		String blockedId = parseUserId(targetUserIdPathValue);
		blockRepository.deleteById(new Block.BlockId(blockerId, blockedId));
	}

	@Transactional(readOnly = true)
	public ListResult listByUser() {
		String blockerId = currentUserProvider.requireUserId();
		List<BlockResponse> blocks = blockRepository.findByIdBlockerIdOrderByCreatedAtDesc(blockerId).stream()
				.flatMap(block -> userRepository.findById(UUID.fromString(block.getBlockedId())).stream()
						.map(user -> new BlockResponse(PublicProfile.from(user), block.getCreatedAt())))
				.toList();
		return new ListResult(blocks, new PageInfo(null));
	}

	// "user_" 접두를 제거하고 UUID 형식을 검증한다. 형식이 잘못됐거나 접두가 없어도 그냥 원문을
	// UUID로 파싱 시도하므로, 어차피 존재하지 않는 사용자와 동일하게 404로 수렴한다.
	private String parseUserId(String pathValue) {
		String raw = pathValue.startsWith("user_") ? pathValue.substring(5) : pathValue;
		try {
			UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			throw new ApiException(ErrorCode.NOT_FOUND, "사용자가 없습니다");
		}
		return raw;
	}

	public record BlockResponse(PublicProfile blockedUser, Instant createdAt) {}

	// isNew는 컨트롤러가 201/200을 분기하는 데만 쓰고 응답 JSON에는 포함하지 않는다
	// (ReportService.Result와 동일한 관례).
	public record Result(BlockResponse response, boolean isNew) {}

	public record ListResult(List<BlockResponse> blocks, PageInfo pageInfo) {}
}
