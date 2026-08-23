package com.runvas.backend.admin;

import com.runvas.backend.community.Post;
import com.runvas.backend.community.PostRepository;
import com.runvas.backend.community.Report;
import com.runvas.backend.community.ReportReason;
import com.runvas.backend.community.ReportRepository;
import com.runvas.backend.community.ReportStatus;
import com.runvas.backend.community.ReportTargetType;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminReportControllerTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("runvas.jwt.secret", () -> "dev-secret-dev-secret-dev-secret-dev-secret");
		registry.add("runvas.jwt.expiration-seconds", () -> "3600");
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	PostRepository postRepository;

	@Autowired
	ReportRepository reportRepository;

	@Test
	@WithMockUser(username = "operator", roles = "ADMIN")
	void reportsListShowsPendingReportWithContentPreview() throws Exception {
		Post post = postRepository.saveAndFlush(new Post("author-1", "신고당할 게시글", "본문", null, Set.of()));
		reportRepository.saveAndFlush(
				new Report("reporter-1", ReportTargetType.POST, post.getId(), ReportReason.SPAM, null));

		mockMvc.perform(get("/admin/reports"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("신고당할 게시글")));
	}

	@Test
	@WithMockUser(username = "operator", roles = "ADMIN")
	void resolveDeletesPostAndRedirects() throws Exception {
		Post post = postRepository.saveAndFlush(new Post("author-1", "삭제될 게시글", "본문", null, Set.of()));
		Report report = reportRepository.saveAndFlush(
				new Report("reporter-1", ReportTargetType.POST, post.getId(), ReportReason.SPAM, null));

		mockMvc.perform(post("/admin/reports/" + report.getId() + "/resolve").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/reports"));

		assertThat(postRepository.existsById(post.getId())).isFalse();
		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus())
				.isEqualTo(ReportStatus.RESOLVED);
	}

	@Test
	@WithMockUser(username = "operator", roles = "ADMIN")
	void dismissKeepsPostAndMarksDismissed() throws Exception {
		Post post = postRepository.saveAndFlush(new Post("author-1", "유지될 게시글", "본문", null, Set.of()));
		Report report = reportRepository.saveAndFlush(
				new Report("reporter-1", ReportTargetType.POST, post.getId(), ReportReason.SPAM, null));

		mockMvc.perform(post("/admin/reports/" + report.getId() + "/dismiss").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/reports"));

		assertThat(postRepository.existsById(post.getId())).isTrue();
		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus())
				.isEqualTo(ReportStatus.DISMISSED);
	}

	@Test
	void listRedirectsWhenNotAuthenticated() throws Exception {
		mockMvc.perform(get("/admin/reports"))
				.andExpect(status().is3xxRedirection());
	}
}
