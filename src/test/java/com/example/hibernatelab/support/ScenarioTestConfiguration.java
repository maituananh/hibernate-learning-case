package com.example.hibernatelab.support;

import java.net.URI;
import java.util.Properties;
import java.util.UUID;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

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
	"com.example.hibernatelab.g10_query", // Phase 13: G10 (truy vấn & persistence context: Q1 baseline dùng chung cho mọi tình huống, Q2 thêm @Version cho S06)
	"com.example.hibernatelab.g11_tx_lock", // Phase 14: G11 (transaction & khoá: T1 baseline có tồn kho + service, T2 thêm @Version, T3 hàng đợi job; có service layer),
	"com.example.hibernatelab.g12_advanced_model", // Phase 15: G12 (mô hình dữ liệu nâng cao: E1 embeddable, C1 element collection, H1/H2/H3 inheritance, N1 natural id, V1 converter/enum, F1 formula/restriction/filter)
	"com.example.hibernatelab.g13_performance", // Phase 16: G13a (hiệu năng & cache: B1/B2/B3 batch & id, P1 persistence context & StatelessSession, K1…K4 second-level cache)
	"com.example.hibernatelab.g14_lead" // Phase 18: G14 (sự cố tổng hợp I1…I4, mỗi sự cố hai package _buggy/_fixed và hai schema; có service)
})
public class ScenarioTestConfiguration {

	@Bean
	SqlCaptor sqlCaptor() {
		return new SqlCaptor();
	}

	/**
	 * Mỗi Spring context một {@link CacheManager} RIÊNG cho second-level cache (G13).
	 *
	 * <p>BẪY: nếu để Hibernate tự lấy, mọi SessionFactory trong cùng JVM dùng CHUNG một CacheManager mặc định. Suite có nhiều
	 * Spring context (cấu hình khác nhau); khi một context đóng nó đóng luôn CacheManager chung, các context còn lại sẽ gặp
	 * {@code IllegalStateException} ("cache closed") ngay lúc evict. URI khác nhau ⇒ provider tạo manager khác nhau.
	 */
	@Bean(destroyMethod = "close")
	CacheManager labCacheManager() {
		CachingProvider provider = Caching.getCachingProvider("com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider");
		return provider.getCacheManager(URI.create("urn:hibernate-lab:" + UUID.randomUUID()), provider.getDefaultClassLoader(),
			new Properties());
	}

	@Bean
	HibernatePropertiesCustomizer cacheManagerCustomizer(CacheManager labCacheManager) {
		return properties -> properties.put("hibernate.javax.cache.cache_manager", labCacheManager);
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

	/** Helper chạy transaction ở thread nền cho test song song (G11). */
	@Bean
	ConcurrentTx concurrentTx(PlatformTransactionManager transactionManager, JdbcTemplate jdbcTemplate) {
		return new ConcurrentTx(transactionManager, jdbcTemplate);
	}

	@Bean
	SchemaInspector schemaInspector(JdbcTemplate jdbcTemplate) {
		return new SchemaInspector(jdbcTemplate);
	}
}
