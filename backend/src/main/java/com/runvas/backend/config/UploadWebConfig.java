package com.runvas.backend.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// runvas.upload.dir에 저장된 파일을 /uploads/** 경로로 정적 서빙한다 (댓글 첨부 이미지 등).
@Configuration
public class UploadWebConfig implements WebMvcConfigurer {

	private final String uploadDir;

	public UploadWebConfig(@Value("${runvas.upload.dir:./uploads}") String uploadDir) {
		this.uploadDir = uploadDir;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
		registry.addResourceHandler("/uploads/**").addResourceLocations(location);
	}
}
