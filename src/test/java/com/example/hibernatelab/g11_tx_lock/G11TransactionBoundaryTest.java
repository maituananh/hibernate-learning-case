package com.example.hibernatelab.g11_tx_lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.UnexpectedRollbackException;

import com.example.hibernatelab.g11_tx_lock.t1_baseline.InnerService;
import com.example.hibernatelab.g11_tx_lock.t1_baseline.OuterService;
import com.example.hibernatelab.g11_tx_lock.t1_baseline.ReadOnlyService;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G11, phần ranh giới transaction: {@code readOnly} (S01), propagation (S02, S03), rollback rule (S04).
 *
 * <p>Ý chính: {@code @Transactional} không phải phép màu. Nó quyết định AI sở hữu transaction, KHI NÀO rollback,
 * và persistence context có chung hay không. Các service nằm ở {@code src/main/.../g11_tx_lock/t1_baseline}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G11TransactionBoundaryTest extends AbstractScenarioTest {

	private static final String PRODUCT = G11Fixtures.T1 + ".product";
	private static final String AUDIT_LOG = G11Fixtures.T1 + ".audit_log";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ReadOnlyService readOnlyService;
	@Autowired
	private OuterService outerService;
	@Autowired
	private InnerService innerService;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { G11Fixtures.T1 };
	}

	/**
	 * G11-S01a — T1: {@code readOnly = true} tắt dirty checking/flush, nên sửa entity không sinh UPDATE 🔬
	 *
	 * <p>Mapping: T1 baseline. <b>Câu hỏi</b>: trong {@code @Transactional(readOnly = true)}, sửa field của entity MANAGED
	 * có UPDATE không?
	 * <p><b>Kết luận</b>: không. Spring đặt flush mode của session sang MANUAL, nên lúc commit Hibernate không flush;
	 * trong bộ nhớ entity đã đổi nhưng DB giữ nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S01a | readOnly = true → sửa entity MANAGED không UPDATE (trong bộ nhớ đổi, DB không đổi)")
	void s01a_readOnlySkipsDirtyChecking() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);

		// ── WHEN ──
		sql.reset();
		int stockInMemory = readOnlyService.decreaseStockButReadOnly(G11Fixtures.PRODUCT_ID);
		// [TX END] method kết thúc → commit; flush mode MANUAL nên KHÔNG flush

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates(PRODUCT, 0);

		// ── THEN: DB thật ──
		assertThat(stockInMemory).as("trong bộ nhớ entity đã bị trừ 1").isEqualTo(9);
		assertThat(db.rowById(PRODUCT, G11Fixtures.PRODUCT_ID).get("stock")).as("DB không đổi vì không flush").isEqualTo(10);
	}

	/**
	 * G11-S01b — T1: {@code save(new)} trong transaction chỉ đọc thất bại ngay ở {@code nextval}, trước cả INSERT 🔬
	 *
	 * <p><b>Câu hỏi</b>: {@code repository.save(new …)} trong {@code readOnly = true} có INSERT không, hay Postgres báo lỗi?
	 * <p><b>Kết luận</b>: Postgres báo lỗi, nhưng KHÔNG phải ở INSERT. {@code readOnly} làm Spring đặt connection ở chế độ
	 * "read only" của Postgres; id kiểu SEQUENCE cần {@code select nextval(...)} ngay lúc {@code persist}, và Postgres cấm
	 * {@code nextval()} trong transaction chỉ đọc. Giả thuyết "flush mode MANUAL nên không INSERT" sai vì lỗi đến sớm hơn flush.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S01b | readOnly = true + save(new) → Postgres từ chối nextval(), không có INSERT nào")
	void s01b_saveNewInReadOnlyFailsAtNextval() {
		// ── WHEN ──
		sql.reset();
		assertThatThrownBy(() -> readOnlyService.saveNewInReadOnly("created-in-read-only"))
			// [TX END] exception thoát khỏi method @Transactional → Spring rollback
			.as("id SEQUENCE cần nextval(), Postgres cấm trong transaction read-only")
			.isInstanceOf(JpaSystemException.class)
			.hasMessageContaining("read-only transaction");

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts(AUDIT_LOG, 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(AUDIT_LOG)).as("không dòng nào được ghi").isZero();
	}

	/**
	 * G11-S02 — T1: A nuốt exception của B (cùng REQUIRED) vẫn không commit được
	 *
	 * <p><b>Câu hỏi</b>: A gọi B (REQUIRED), B ném RuntimeException, A {@code catch} rồi đi tiếp: A commit được không?
	 * <p><b>Kết luận</b>: không. B tham gia CÙNG transaction với A; khi exception đi qua proxy của B, Spring đánh dấu
	 * transaction chung là rollback-only. Lúc A commit, Spring thấy cờ đó và ném {@code UnexpectedRollbackException};
	 * cả "outer" lẫn "inner-fail" đều mất.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S02 | A (REQUIRED) nuốt lỗi của B (REQUIRED) → UnexpectedRollbackException, mất cả hai")
	void s02_swallowedInnerFailureStillRollsBackOuter() {
		// ── WHEN ──
		assertThatThrownBy(() -> outerService.callInnerAndSwallowFailure())
			// [TX END] commit của A bị từ chối vì transaction chung đã rollback-only
			.as("B đã đánh dấu rollback-only trên transaction chung, A có catch cũng vô ích")
			.isInstanceOf(UnexpectedRollbackException.class);

		// ── THEN: DB thật ──
		assertThat(db.countOf(AUDIT_LOG)).as("rollback cả transaction: không còn dòng nhật ký nào").isZero();
	}

	/**
	 * G11-S03a — T1: {@code REQUIRES_NEW} commit độc lập với transaction ngoài
	 *
	 * <p><b>Câu hỏi</b>: B (REQUIRES_NEW) ghi "inner" rồi A ghi "outer" và rollback: dòng nào còn?
	 * <p><b>Kết luận</b>: "inner" còn (B có transaction và connection riêng, đã commit), "outer" mất.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S03a | REQUIRES_NEW: B commit dù A rollback")
	void s03a_requiresNewCommitsEvenWhenOuterRollsBack() {
		// ── WHEN ──
		assertThatThrownBy(() -> outerService.writeThenFailAfterRequiresNew())
			.as("A ném lỗi sau khi B đã commit")
			.isInstanceOf(IllegalStateException.class);

		// ── THEN: DB thật ──
		db.print(AUDIT_LOG);
		assertThat(db.countOf(AUDIT_LOG)).as("chỉ còn 1 dòng").isEqualTo(1);
		assertThat(db.rowsOf(AUDIT_LOG).get(0).get("message")).as("dòng còn lại là của B (REQUIRES_NEW)").isEqualTo("inner");
	}

	/**
	 * G11-S03b — T1: {@code REQUIRES_NEW} có persistence context RIÊNG, entity của A không phải instance của B
	 *
	 * <p><b>Câu hỏi</b>: entity A đã tải (MANAGED trong PC của A) có là cùng instance khi B {@code find} cùng id không?
	 * <p><b>Kết luận</b>: không. B mở EntityManager mới nên {@code find} ra instance KHÁC, và PC của B không chứa entity của A.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S03b | REQUIRES_NEW → persistence context riêng, entity của A là instance khác")
	void s03b_requiresNewHasItsOwnPersistenceContext() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);

		// ── WHEN ──
		String sameAndContains = outerService.loadProductThenAskInnerRequiresNew(G11Fixtures.PRODUCT_ID);

		// ── THEN ──
		assertThat(sameAndContains).as("định dạng \"sameInstance|contains\": cả hai đều false vì PC của B tách biệt").isEqualTo("false|false");
	}

	/**
	 * G11-S04a — T1: checked exception KHÔNG làm Spring rollback (mặc định)
	 *
	 * <p><b>Câu hỏi</b>: method {@code @Transactional} sửa tồn kho rồi ném {@code Exception} (checked): rollback không?
	 * <p><b>Kết luận</b>: không, Spring COMMIT. Mặc định chỉ {@code RuntimeException} và {@code Error} gây rollback
	 * (kế thừa quy tắc của EJB). Tồn kho đã bị trừ dù method "thất bại".
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S04a | checked exception → mặc định COMMIT (stock vẫn bị trừ)")
	void s04a_checkedExceptionCommitsByDefault() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);

		// ── WHEN ──
		assertThatThrownBy(() -> innerService.decreaseStockThenThrowChecked(G11Fixtures.PRODUCT_ID))
			.as("exception checked vẫn thoát ra cho caller")
			.hasMessage("checked exception");

		// ── THEN: DB thật ──
		assertThat(db.rowById(PRODUCT, G11Fixtures.PRODUCT_ID).get("stock")).as("transaction đã COMMIT nên stock = 9").isEqualTo(9);
	}

	/**
	 * G11-S04b — T1: {@code rollbackFor = Exception.class} làm checked exception rollback
	 *
	 * <p><b>Câu hỏi</b>: cùng việc như S04a nhưng khai báo {@code rollbackFor}: kết quả?
	 * <p><b>Kết luận</b>: ROLLBACK, stock giữ nguyên 10. Quy tắc nên là: luôn khai báo {@code rollbackFor} nếu method ném checked exception.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S04b | rollbackFor = Exception.class → checked exception ROLLBACK (stock giữ nguyên)")
	void s04b_rollbackForMakesCheckedExceptionRollback() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);

		// ── WHEN ──
		assertThatThrownBy(() -> innerService.decreaseStockThenThrowCheckedWithRollbackFor(G11Fixtures.PRODUCT_ID))
			.as("exception checked vẫn thoát ra cho caller")
			.hasMessage("checked exception");

		// ── THEN: DB thật ──
		assertThat(db.rowById(PRODUCT, G11Fixtures.PRODUCT_ID).get("stock")).as("transaction đã ROLLBACK nên stock vẫn 10").isEqualTo(10);
	}
}
