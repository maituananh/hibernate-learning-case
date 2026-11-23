package com.example.hibernatelab.support;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Lớp cha của MỌI test tình huống. Chỉ chứa hạ tầng, KHÔNG chứa dữ liệu mẫu (GIVEN luôn nằm trong test method).
 *
 * <p>Điều quan trọng: lớp con KHÔNG được gắn {@code @Transactional}. Mỗi bước tự mở transaction bằng
 * {@link #inTx(Runnable)} để thấy rõ lúc nào entity chuyển MANAGED → DETACHED, lúc nào flush, lúc nào commit.
 *
 * <p>Mọi lớp con dùng chung đúng một cấu hình ({@code @Import} bên dưới) nên Spring chỉ khởi động
 * một context và một container Postgres cho cả suite. Đừng thêm {@code @Import}/{@code @ActiveProfiles}
 * riêng cho từng lớp con.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({ PostgresTestcontainer.class, ScenarioTestConfiguration.class })
public abstract class AbstractScenarioTest {

	/** EntityManager dùng chung của Spring: chỉ có persistence context khi đang ở trong inTx(...). */
	@PersistenceContext
	protected EntityManager em;

	/** Số câu SQL Hibernate đã chạy: reset(), print(), assertInserts(...). */
	@Autowired
	protected SqlAssert sql;

	/** Đọc dữ liệu THẬT bằng JDBC (không qua cache cấp 1). */
	@Autowired
	protected DbReader db;

	/** Đọc cấu trúc bảng thật (cột, PK, FK, unique) để assert DDL. */
	@Autowired
	protected SchemaInspector schema;

	@Autowired
	private DbCleaner dbCleaner;

	@Autowired
	private PlatformTransactionManager transactionManager;

	/**
	 * Các schema mà test class này đụng tới; chúng được TRUNCATE trước mỗi test.
	 * Trả về mảng rỗng nếu test không dùng bảng nào (ví dụ smoke test).
	 */
	protected abstract String[] schemasUnderTest();

	/** Dọn dữ liệu TRƯỚC mỗi test (không dọn sau, để còn xem được dữ liệu), rồi xoá SQL đã ghi. */
	@BeforeEach
	void cleanDatabaseAndResetSql() {
		String[] schemas = schemasUnderTest();
		if (schemas.length > 0) {
			dbCleaner.truncateAllTablesIn(schemas);
		}
		sql.reset();
	}

	/** Chạy {@code work} trong MỘT transaction mới: hết lambda là flush + commit, persistence context đóng. */
	protected void inTx(Runnable work) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
	}

	/** Như {@link #inTx(Runnable)} nhưng trả về giá trị (thường là entity đã persist, lúc này đã DETACHED, hoặc id). */
	protected <T> T inTxReturning(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	/**
	 * Chạy {@code work} trên một transaction và connection KHÁC hẳn (REQUIRES_NEW; transaction hiện tại bị tạm treo).
	 * Dùng để đọc DB "như một người khác": chỉ thấy dữ liệu đã COMMIT, nên chứng minh được flush ≠ commit.
	 */
	protected <T> T inNewConnection(Supplier<T> work) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return template.execute(status -> work.get());
	}
}
