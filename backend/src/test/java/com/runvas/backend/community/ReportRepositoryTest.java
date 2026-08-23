package com.runvas.backend.community;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ReportRepositoryTest {

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
	ReportRepository reportRepository;

	@Test
	void pendingUniqueIndexPreventsDuplicatePendingReportForSameReporterAndTarget() {
		// Save first PENDING report
		reportRepository.saveAndFlush(new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null));

		// Attempt to save second PENDING report with same reporter, target type, and target id should fail
		Report duplicate = new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.ABUSIVE, null);
		assertThatThrownBy(() -> reportRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void newPendingReportAllowedAfterPreviousReportIsResolved() {
		// Save first PENDING report
		Report firstReport = reportRepository.saveAndFlush(
				new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null));

		// Resolve the first report
		firstReport.resolve();
		reportRepository.saveAndFlush(firstReport);

		// A new PENDING report for the same reporter, target type, and target id should be allowed
		Report secondReport = new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.ABUSIVE, null);
		Report savedSecondReport = reportRepository.saveAndFlush(secondReport);

		assertThat(savedSecondReport.getStatus()).isEqualTo(ReportStatus.PENDING);
	}

	@Test
	void findAllByTargetTypeAndTargetIdAndStatusReturnsOnlyMatchingPendingReports() {
		Report pending1 = reportRepository.saveAndFlush(
				new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null));
		Report pending2 = reportRepository.saveAndFlush(
				new Report("reporter-2", ReportTargetType.POST, "post-1", ReportReason.ABUSIVE, null));
		reportRepository.saveAndFlush(
				new Report("reporter-3", ReportTargetType.POST, "post-2", ReportReason.SPAM, null));

		List<Report> results = reportRepository.findAllByTargetTypeAndTargetIdAndStatus(
				ReportTargetType.POST, "post-1", ReportStatus.PENDING);

		assertThat(results).extracting(Report::getId).containsExactlyInAnyOrder(pending1.getId(), pending2.getId());
	}
}
