package com.example.hibernatelab.g13_enhanced;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.support.DbCleaner;
import com.example.hibernatelab.support.DbReader;
import com.example.hibernatelab.support.SchemaInspector;
import com.example.hibernatelab.support.SqlAssert;
import com.example.hibernatelab.support.SqlCaptor;

/**
 * Cấu hình test của lab-enhanced: đăng ký các helper dùng chung (lấy từ testFixtures của project gốc).
 *
 * <p>{@code @EntityScan} chỉ quét {@code g13_enhanced}: gồm entity ĐÃ enhance (src/main) lẫn entity đối chứng
 * KHÔNG enhance ({@code control}, src/test). Entity của project gốc cũng nằm trên classpath (qua testFixtures)
 * nhưng không được quét, nên subproject này chỉ tạo đúng schema của nó.
 */
@TestConfiguration(proxyBeanMethods = false)
@EntityScan(basePackages = "com.example.hibernatelab.g13_enhanced")
public class EnhancedTestConfiguration {

	@Bean
	SqlCaptor sqlCaptor() {
		return new SqlCaptor();
	}

	/** Cắm SqlCaptor vào Hibernate (truyền đúng instance để test đọc được danh sách SQL). */
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
