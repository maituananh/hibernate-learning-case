package com.example.hibernatelab.g0_lifecycle;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.TRANSIENT;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G0: persist / flush / commit (SEQUENCE). Các tình huống S02… sẽ được thêm ở Phase 2.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class PersistFlushTest extends AbstractScenarioTest {

	private static final String CUSTOMER = "g0_lifecycle.customer";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { "g0_lifecycle" };
	}

	/**
	 * G0-S01 — persist() với SEQUENCE: có id ngay nhưng INSERT bị hoãn; flush ≠ commit
	 *
	 * <p>Mapping : g0_lifecycle.customer, id {@code @GeneratedValue(SEQUENCE)}, chưa có quan hệ nào
	 * <p><b>Câu hỏi</b>: Gọi {@code em.persist(customer)} thì câu INSERT chạy lúc nào? Và sau khi
	 * {@code em.flush()}, một connection khác đã thấy dòng đó chưa?
	 * <p><b>Kết luận</b>:
	 * <ol>
	 *   <li>{@code persist} chỉ xin id từ sequence và đưa entity vào persistence context: 0 INSERT.</li>
	 *   <li>{@code flush} mới gửi INSERT xuống DB, nhưng transaction chưa commit nên connection khác vẫn
	 *       thấy 0 dòng. Flush đẩy SQL xuống, commit mới làm dữ liệu hiện ra với người khác.</li>
	 *   <li>Sau commit, connection khác thấy 1 dòng; entity chuyển sang DETACHED vì persistence context đóng.</li>
	 * </ol>
	 * Xác minh: Hibernate 7.4.15.Final (do Spring Boot 4.2.0 quản lý)
	 */
	@Test
	@DisplayName("G0-S01 | persist() không INSERT ngay (SEQUENCE); flush ≠ commit")
	void s01_persistDoesNotInsertImmediately_flushIsNotCommit() {
		// ── GIVEN: bảng customer trống (DbCleaner đã TRUNCATE trước test) ──
		assertThat(db.countOf(CUSTOMER)).as("điều kiện đầu: chưa có khách hàng nào").isZero();

		// ── WHEN: trong MỘT transaction — persist, rồi flush, rồi (kết thúc lambda) commit ──
		sql.reset();
		Customer customerAn = inTxReturning(() -> {
			Customer customer = new Customer("An", "an@example.com");
			// [LIFECYCLE] customer: TRANSIENT (mới new, chưa có id, chưa nằm trong persistence context)
			assertState(em, customer, TRANSIENT);

			sql.mark("WHEN 1: persist()");
			em.persist(customer);
			// [LIFECYCLE] customer: TRANSIENT → MANAGED (id đã lấy từ sequence, CHƯA có INSERT nào)
			assertState(em, customer, MANAGED);

			// ── THEN (sau persist): SQL + DB thật ──
			assertThat(customer.getId()).as("persist với SEQUENCE: id có ngay vì Hibernate xin từ sequence").isNotNull();
			sql.assertInserts("customer", 0); // INSERT bị hoãn tới flush (nextval không tính)
			assertThat(db.countOf(CUSTOMER)).as("DB chưa có dòng nào vì INSERT chưa được gửi").isZero();

			sql.mark("WHEN 2: flush()");
			em.flush();
			// [FLUSH] gửi INSERT customer xuống DB. Entity vẫn MANAGED (flush không đổi trạng thái)
			assertState(em, customer, MANAGED);

			// ── THEN (sau flush): SQL + DB thật, nhìn từ hai connection ──
			sql.assertInserts("customer", 1);
			assertThat(db.countOf(CUSTOMER)).as("cùng transaction/connection thì thấy dòng vừa flush").isEqualTo(1);
			long seenByOtherConnection = inNewConnection(() -> db.countOf(CUSTOMER));
			assertThat(seenByOtherConnection)
				.as("flush ≠ commit: transaction khác (connection khác) chưa thấy dòng chưa commit")
				.isZero();

			sql.mark("WHEN 3: commit (hết lambda)");
			return customer;
		});
		// [FLUSH] commit: không còn gì để flush (đã flush ở trên) → không thêm INSERT/UPDATE nào
		// [TX END] persistence context đóng → customer: MANAGED → DETACHED
		assertState(em, customerAn, DETACHED);

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("customer", 1);
		sql.assertUpdates("customer", 0);

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		long seenAfterCommit = inNewConnection(() -> db.countOf(CUSTOMER));
		assertThat(seenAfterCommit).as("sau commit, connection khác đã thấy dòng").isEqualTo(1);
		Map<String, Object> row = db.rowById(CUSTOMER, customerAn.getId());
		assertThat(row.get("name")).as("dữ liệu ghi xuống DB đúng như đã persist").isEqualTo("An");
	}
}
