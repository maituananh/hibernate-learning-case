package com.example.hibernatelab.g13_enhanced;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.hibernatelab.support.DbCleaner;
import com.example.hibernatelab.support.DbReader;
import com.example.hibernatelab.support.PostgresTestcontainer;
import com.example.hibernatelab.support.SqlAssert;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Lớp cha của test trong lab-enhanced. Là bản rút gọn của {@code AbstractScenarioTest} ở project gốc
 * (cố ý copy thay vì dùng chung: lớp gốc gắn chặt với danh sách entity của project gốc).
 *
 * <p>Quy ước giống project gốc: KHÔNG {@code @Transactional} trên test, mỗi bước tự mở transaction bằng {@link #inTx(Runnable)};
 * dọn dữ liệu TRƯỚC mỗi test; dữ liệu assert bằng JDBC chứ không qua EntityManager.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({ PostgresTestcontainer.class, EnhancedTestConfiguration.class })
public abstract class AbstractEnhancedTest {

	@PersistenceContext
	protected EntityManager em;

	@Autowired
	protected SqlAssert sql;

	@Autowired
	protected DbReader db;

	@Autowired
	private DbCleaner dbCleaner;

	@Autowired
	private PlatformTransactionManager transactionManager;

	/** Các schema test class này đụng tới; được TRUNCATE trước mỗi test. */
	protected abstract String[] schemasUnderTest();

	@BeforeEach
	void cleanDatabaseAndResetSql() {
		dbCleaner.truncateAllTablesIn(schemasUnderTest());
		sql.reset();
	}

	/** Chạy {@code work} trong MỘT transaction mới: hết lambda là flush + commit, persistence context đóng. */
	protected void inTx(Runnable work) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
	}

	protected <T> T inTxReturning(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}
}
