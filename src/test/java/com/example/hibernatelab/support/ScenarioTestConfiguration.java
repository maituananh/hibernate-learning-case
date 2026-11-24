package com.example.hibernatelab.support;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cấu hình dùng chung cho mọi test: đăng ký các helper thành bean và quyết định entity nào được nạp.
 *
 * <p><b>@EntityScan liệt kê TƯỜNG MINH từng package entity</b> (thay vì quét cả {@code com.example.hibernatelab}).
 * Lý do: quét cả gốc sẽ nhặt luôn entity {@code [BOOT-FAIL]} trong {@code src/test/.../bootfail}
 * (cố ý sai mapping) và làm cả suite không khởi động. Mỗi phase thêm package mới vào danh sách này.
 * KHÔNG BAO GIỜ thêm package {@code bootfail} vào đây.
 */
@TestConfiguration(proxyBeanMethods = false)
@EntityScan(basePackages = {
	"com.example.hibernatelab.support.selftest", // entity tự kiểm thử hạ tầng (schema support_selftest)
	"com.example.hibernatelab.g0_lifecycle", // Phase 2: G0
	"com.example.hibernatelab.g1_owning_side", // Phase 3: G1 (9 biến thể, mỗi biến thể một sub-package và một schema)
	"com.example.hibernatelab.g2_cascade", // Phase 4: G2 (10 biến thể C0…C8 + C4-DEEP, mỗi biến thể một sub-package và một schema)
	"com.example.hibernatelab.g3_orphan_removal", // Phase 5: G3 (3 biến thể O0, O1, O2, mỗi biến thể một sub-package và một schema)
	"com.example.hibernatelab.g4_update_child_via_parent", // Phase 6: G4 (6 biến thể U0…U5, mỗi biến thể một sub-package và một schema)
	"com.example.hibernatelab.g5_fetch_n_plus_one", // Phase 7: G5 (6 biến thể F0, F1, F1S, F2, F3, F4, mỗi biến thể một sub-package và một schema)
	"com.example.hibernatelab.g6_one_to_one", // Phase 8: G6 (7 biến thể V1…V7, mỗi biến thể một sub-package và một schema)
	"com.example.hibernatelab.g7_many_to_many", // Phase 9: G7 (9 biến thể M1…M9, mỗi biến thể một sub-package và một schema)
	"com.example.hibernatelab.g8_composite_fk", // Phase 10: G8 (J1, J1P, J2, J3, J4, mỗi biến thể một sub-package và một schema)
	"com.example.hibernatelab.g9_integrated", // Phase 11: G9 (tình huống tổng hợp S01…S11, mỗi tình huống một sub-package và một schema; có thêm service)
	"com.example.hibernatelab.g10_query" // Phase 13: G10 (truy vấn & persistence context: Q1 baseline dùng chung cho mọi tình huống, Q2 thêm @Version cho S06)
})
public class ScenarioTestConfiguration {

	@Bean
	SqlCaptor sqlCaptor() {
		return new SqlCaptor();
	}

	/**
	 * Cắm SqlCaptor vào Hibernate. Truyền thẳng đối tượng (không phải tên class) để Hibernate dùng
	 * ĐÚNG instance này, nhờ đó test đọc được danh sách SQL.
	 */
	@Bean
	HibernatePropertiesCustomizer statementInspectorCustomizer(SqlCaptor sqlCaptor) {
		return properties -> properties.put("hibernate.session_factory.statement_inspector", sqlCaptor);
	}

	@Bean
	SqlAssert sqlAssert(SqlCaptor sqlCaptor) {
		return new SqlAssert(sqlCaptor);
	}

	@Bean
	DbReader dbReader(JdbcTemplate jdbcTemplate) {
		return new DbReader(jdbcTemplate);
	}

	@Bean
	DbCleaner dbCleaner(JdbcTemplate jdbcTemplate) {
		return new DbCleaner(jdbcTemplate);
	}

	@Bean
	SchemaInspector schemaInspector(JdbcTemplate jdbcTemplate) {
		return new SchemaInspector(jdbcTemplate);
	}
}
