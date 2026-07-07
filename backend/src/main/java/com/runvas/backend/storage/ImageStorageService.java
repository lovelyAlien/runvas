package com.runvas.backend.storage;

import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// 클라우드 인프라가 아직 없어서 로컬 디스크에 저장한다 (YAGNI — S3 등은 필요해지면 도입).
@Service
public class ImageStorageService {

	private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
	private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

	private final Path uploadDir;
	private final String baseUrl;

	public ImageStorageService(
			@Value("${runvas.upload.dir:./uploads}") String uploadDir,
			@Value("${runvas.upload.base-url:http://localhost:8921}") String baseUrl) {
		this.uploadDir = Path.of(uploadDir);
		this.baseUrl = baseUrl;
	}

	// 검증 후 {uploadDir}/course-comments/{courseId}/{UUID}.{ext}에 저장하고, 공개 접근 가능한 URL을 반환한다.
	public String store(String courseId, MultipartFile file) {
		validate(file);

		String extension = extractExtension(file.getOriginalFilename());
		String fileName = UUID.randomUUID() + "." + extension;
		Path targetDir = uploadDir.resolve("course-comments").resolve(courseId);
		Path targetPath = targetDir.resolve(fileName);

		try {
			Files.createDirectories(targetDir);
			try (InputStream inputStream = file.getInputStream()) {
				Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new ApiException(ErrorCode.INTERNAL_ERROR, "이미지 저장에 실패했습니다");
		}

		return baseUrl + "/uploads/course-comments/" + courseId + "/" + fileName;
	}

	// 댓글 수정/삭제 시 기존 이미지를 정리한다. 이미 없는 파일이면 조용히 무시한다(idempotent).
	public void delete(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return;
		}
		String marker = "/uploads/";
		int index = imageUrl.indexOf(marker);
		if (index < 0) {
			return;
		}
		String relativePath = imageUrl.substring(index + marker.length());
		Path targetPath = uploadDir.resolve(relativePath);
		try {
			Files.deleteIfExists(targetPath);
		} catch (IOException e) {
			log.warn("이미지 파일 삭제에 실패했습니다: {}", targetPath, e);
		}
	}

	private void validate(MultipartFile file) {
		if (file.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "이미지 파일이 비어 있습니다");
		}
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "이미지 용량은 5MB를 초과할 수 없습니다");
		}
		String extension = extractExtension(file.getOriginalFilename());
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 이미지 형식입니다 (jpg, jpeg, png, webp만 허용)");
		}
	}

	private String extractExtension(String originalFilename) {
		if (originalFilename == null || !originalFilename.contains(".")) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "이미지 파일 형식을 확인할 수 없습니다");
		}
		String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
		return extension.toLowerCase(Locale.ROOT);
	}
}
