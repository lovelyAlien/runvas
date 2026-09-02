package com.runvas.backend.community;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BlockController {

	private final BlockService blockService;

	@PostMapping("/api/blocks/{userId}")
	public ResponseEntity<BlockService.BlockResponse> block(@PathVariable String userId) {
		BlockService.Result result = blockService.block(userId);
		HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(result.response());
	}

	@DeleteMapping("/api/blocks/{userId}")
	public ResponseEntity<Void> unblock(@PathVariable String userId) {
		blockService.unblock(userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/blocks")
	public Map<String, Object> list() {
		BlockService.ListResult result = blockService.listByUser();
		return Map.of("blocks", result.blocks(), "pageInfo", result.pageInfo());
	}
}
