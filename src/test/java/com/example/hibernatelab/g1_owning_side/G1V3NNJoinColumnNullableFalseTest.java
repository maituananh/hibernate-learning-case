package com.example.hibernatelab.g1_owning_side;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.hibernatelab.g1_owning_side.v3nn_o2m_uni_joincolumn_nn.Customer;
import com.example.hibernatelab.g1_owning_side.v3nn_o2m_uni_joincolumn_nn.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Biến thể V3-NN: như V3 nhưng {@code @JoinColumn(nullable = false)}. Chỉ có G1-S10 (🔬 ➕), gồm S10a và S10b:
 * {@code nullable = false} có làm Hibernate ghi FK ngay trong câu INSERT, bỏ được UPDATE bù không? Câu trả lời phụ thuộc THỨ TỰ gọi.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G1V3NNJoinColumnNullableFalseTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g1_v3nn_o2m_uni_joincolumn_nn";
	private static final String PURCHASE_ORDER = SCHEMA + ".purchase_order";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/**
	 * G1-S10a — V3-NN 🔬: persist order TRƯỚC khi add vào collection → INSERT mang {@code customer_id = NULL} → DB từ chối
	 *
	 * <p>Mapping: {@code Customer.orders} OWNING side, {@code @OneToMany @JoinColumn(name = "customer_id", nullable = false)}.
	 * <p><b>Câu hỏi</b>: Cột đã NOT NULL, Hibernate sẽ đưa {@code customer_id} vào INSERT. Nhưng nó lấy giá trị đó
	 * ở thời điểm nào: lúc {@code persist(order)} hay lúc flush?
	 * <p><b>Kết luận</b>: Lúc {@code persist(order)}, order chưa nằm trong collection nào nên Hibernate chưa biết
	 * customer là ai và bind NULL. Câu INSERT có cột {@code customer_id} nhưng giá trị NULL → PostgreSQL từ chối
	 * (khác V1-NN ở G1-S04b: ở đó Hibernate chặn trước). Hibernate không kiểm tra not-null cho khoá "ngầm" này.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S10a | V3-NN: persist order trước khi add vào collection → INSERT customer_id = NULL, DB từ chối")
	void s10a_persistingOrderBeforeAddingToCollectionViolatesNotNull() {
		// ── GIVEN: DDL của V3-NN có cột NOT NULL ──
		assertThat(schema.columnNamed(SCHEMA, "purchase_order", "customer_id").nullable())
			.as("nullable = false → cột customer_id NOT NULL")
			.isFalse();

		// ── WHEN: persist customer và order, rồi MỚI add order vào collection (sai thứ tự) ──
		sql.reset();
		Throwable failure = catchThrowable(() -> inTx(() -> {
			Customer customerAn = new Customer("An");
			PurchaseOrder orderA1 = new PurchaseOrder("A-001");
			em.persist(customerAn);
			em.persist(orderA1); // lúc này Hibernate chụp giá trị INSERT: order chưa thuộc collection nào → customer_id = NULL
			customerAn.getOrders().add(orderA1); // quá muộn
			// [FLUSH] lúc commit: INSERT purchase_order (customer_id = NULL) → PostgreSQL từ chối
		}));

		// ── THEN: lỗi + SQL + DB thật ──
		System.out.println("[EXCEPTION] " + failure.getClass().getName());
		assertThat(failure)
			.as("DB từ chối NULL ở cột NOT NULL, Spring dịch thành DataIntegrityViolationException")
			.isInstanceOf(DataIntegrityViolationException.class);
		sql.print();
		sql.assertInserts("purchase_order", 1); // câu INSERT đã được gửi (rồi bị DB từ chối)
		assertThat(db.countOf(PURCHASE_ORDER)).as("transaction rollback nên không có order nào").isZero();
	}

	/**
	 * G1-S10b — V3-NN 🔬: đúng thứ tự thì INSERT có FK, NHƯNG 2 câu UPDATE bù vẫn còn (thừa)
	 *
	 * <p>Mapping: như S10a.
	 * <p><b>Câu hỏi</b>: Đổi thứ tự (persist customer → add order vào collection → persist order) thì INSERT có FK.
	 * Vậy UPDATE bù của V3 (S09b) có biến mất không?
	 * <p><b>Kết luận</b>: Không. {@code nullable = false} chỉ giúp FK có mặt ngay trong INSERT (nếu đúng thứ tự); Hibernate
	 * vẫn gửi 2 UPDATE {@code set customer_id = ?} thừa (cùng giá trị). Tổng SQL KHÔNG ít hơn V3, lại thêm bẫy thứ tự
	 * (S10a) → muốn bỏ UPDATE thừa hãy dùng hai chiều với {@code mappedBy} (V4): 1 INSERT là đủ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S10b | V3-NN: đúng thứ tự thì INSERT có FK nhưng vẫn còn 2 UPDATE thừa")
	void s10b_correctOrderPutsForeignKeyInInsertButRedundantUpdatesRemain() {
		// ── GIVEN: DB trống ──

		// ── WHEN: persist customer → add order vào collection → persist order (đúng thứ tự) ──
		sql.reset();
		Customer customerAn = inTxReturning(() -> {
			Customer customer = new Customer("An");
			PurchaseOrder orderA1 = new PurchaseOrder("A-001");
			PurchaseOrder orderA2 = new PurchaseOrder("A-002");
			em.persist(customer);
			customer.getOrders().add(orderA1);
			customer.getOrders().add(orderA2);
			em.persist(orderA1); // order đã thuộc collection → Hibernate biết customer_id ngay lúc này
			em.persist(orderA2);
			// [LIFECYCLE] cả hai order: TRANSIENT → MANAGED
			assertState(em, orderA1, MANAGED);
			assertState(em, orderA2, MANAGED);
			// [FLUSH] lúc commit: INSERT customer, 2 INSERT purchase_order (đã có customer_id), rồi 2 UPDATE thừa
			return customer;
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("purchase_order", 2);
		sql.assertUpdates("purchase_order", 2); // vẫn còn UPDATE: nullable = false không bỏ được chúng

		// ── THEN: DB thật ──
		db.print(PURCHASE_ORDER);
		List<Map<String, Object>> orderRows = db.rowsOf(PURCHASE_ORDER);
		assertThat(orderRows).as("2 order đã được lưu").hasSize(2);
		assertThat(orderRows.get(0).get("customer_id")).as("FK cuối cùng trỏ về An").isEqualTo(customerAn.getId());
		assertThat(orderRows.get(1).get("customer_id")).as("FK cuối cùng trỏ về An").isEqualTo(customerAn.getId());
	}
}
