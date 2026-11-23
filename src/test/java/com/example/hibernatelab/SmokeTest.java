package com.example.hibernatelab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G0-SMOKE: kiểm tra hạ tầng, chưa liên quan tới mapping.
 *
 * <p>Câu hỏi: Spring context có khởi động được và nối được tới PostgreSQL thật không?
 * <p>Kết luận: nếu test này xanh thì Testcontainers (hoặc compose khi {@code -Pinspect}) hoạt động,
 * và câu SQL phải hiện ra trên terminal (chứng minh {@code showStandardStreams} trong build.gradle có tác dụng).
 */
class SmokeTest extends AbstractScenarioTest {

	// Smoke test không dùng bảng nào nên không có schema cần dọn.
	@Override
	protected String[] schemasUnderTest() {
		return new String[] {};
	}

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("G0-SMOKE | Context khởi động và nối được PostgreSQL thật")
	void s01_contextStartsAndPostgresResponds() {
		// ── WHEN ──
		// Câu SQL này được JdbcTemplate log ra (DEBUG) nên phải thấy trên terminal.
		String version = jdbc.queryForObject("select version()", String.class);

		// ── THEN: DB thật ──
		assertThat(version)
			.as("DB phải là PostgreSQL thật (không phải H2), vì DDL/constraint của H2 khác Postgres")
			.contains("PostgreSQL");
	}
}
