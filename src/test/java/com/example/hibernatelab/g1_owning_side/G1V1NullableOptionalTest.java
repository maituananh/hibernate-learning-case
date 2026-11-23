package com.example.hibernatelab.g1_owning_side;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.hibernate.PropertyValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.Customer;
import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G1-S04: so sánh ba cách khai báo cho phép / cấm {@code customer = null} trên {@code @ManyToOne}
 * (phần lớn kết quả ở đây KHÁC giả thuyết ban đầu trong docs/02-SCENARIOS, xem docs/RESULTS.md):
 * V1 (mặc định), V1-NN ({@code @JoinColumn(nullable = false)}) và V1-OPT ({@code @ManyToOne(optional = false)}).
 *
 * <p>Ba biến thể có CÙNG tên class {@code PurchaseOrder} (khác package), nên ở đây V1 được import bình thường
 * còn V1-NN và V1-OPT viết đầy đủ tên package (fully-qualified) để người đọc thấy rõ đang dùng biến thể nào.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G1V1NullableOptionalTest extends AbstractScenarioTest {

	private static final String V1_SCHEMA = "g1_v1_m2o_uni";
	private static final String V1NN_SCHEMA = "g1_v1nn_m2o_nullable_false";
	private static final String V1OPT_SCHEMA = "g1_v1opt_m2o_optional_false";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { V1_SCHEMA, V1NN_SCHEMA, V1OPT_SCHEMA };
	}

	/**
	 * G1-S04a — V1 (không ghi gì): order không có customer vẫn lưu được, và SELECT dùng LEFT JOIN
	 *
	 * <p>Mapping: V1, {@code PurchaseOrder.customer} OWNING side, {@code @ManyToOne} mặc định (optional = true).
	 * <p><b>Câu hỏi</b>: Không ghi {@code nullable}/{@code optional} thì order có được phép không có customer?
	 * <p><b>Kết luận</b>: Được. INSERT thành công với {@code customer_id = NULL}. Vì quan hệ có thể NULL nên khi
	 * tải order (EAGER) Hibernate phải dùng {@code left join}, nếu không order không customer sẽ biến mất khỏi kết quả.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S04a | V1 mặc định: order không customer vẫn lưu được; find dùng LEFT JOIN")
	void s04a_v1DefaultAllowsNullCustomerAndUsesLeftJoin() {
		// ── GIVEN: không có customer nào ──
		assertThat(schema.columnNamed(V1_SCHEMA, "purchase_order", "customer_id").nullable())
			.as("điều kiện đầu (DDL): cột customer_id của V1 cho phép NULL")
			.isTrue();

		// ── WHEN 1: persist order với customer = null ──
		sql.reset();
		PurchaseOrder orderNoCustomer = inTxReturning(() -> {
			PurchaseOrder order = new PurchaseOrder("A-001");
			em.persist(order);
			// [LIFECYCLE] order: TRANSIENT → MANAGED. customer vẫn null, Hibernate không phàn nàn
			assertState(em, order, MANAGED);
			return order;
			// [FLUSH] lúc commit: INSERT purchase_order với customer_id = NULL
		});

		// ── THEN: SQL + DB thật (INSERT) ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
		db.print(V1_SCHEMA + ".purchase_order");
		assertThat(db.rowById(V1_SCHEMA + ".purchase_order", orderNoCustomer.getId()).get("customer_id"))
			.as("customer = null nên customer_id = NULL, DB cho phép vì cột nullable")
			.isNull();

		// ── WHEN 2: tải lại order bằng find (customer là EAGER mặc định) ──
		sql.reset();
		inTx(() -> em.find(PurchaseOrder.class, orderNoCustomer.getId()));

		// ── THEN: SQL (SELECT) ──
		sql.print();
		sql.assertSomeStatementContains("left join");
	}

	/**
	 * G1-S04b — V1-NN ({@code nullable = false}) 🔬: Hibernate CŨNG chặn trước, không đợi database
	 *
	 * <p>Mapping: V1-NN, {@code @ManyToOne @JoinColumn(name = "customer_id", nullable = false)}.
	 * <p><b>Câu hỏi</b>: {@code nullable = false} chỉ là chỉ dẫn cho DDL (để DB từ chối) hay Hibernate cũng kiểm tra?
	 * <p><b>Kết luận</b>: Hibernate cũng kiểm tra. Nó đọc {@code nullable = false} để coi property là bắt buộc, nên
	 * ném {@code PropertyValueException} ngay lúc persist, TRƯỚC khi gửi SQL (0 INSERT). Kết quả giống V1-OPT ở
	 * S04c, và (S04e) cũng giống ở loại JOIN khi tải. Giả thuyết ban đầu (DB từ chối) KHÔNG đúng ở
	 * Hibernate 7.4.15, xem docs/RESULTS.md.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S04b | V1-NN nullable=false: Hibernate cũng chặn trước khi gửi INSERT (0 INSERT)")
	void s04b_v1nnNullableFalseIsAlsoRejectedByHibernateBeforeSql() {
		// ── GIVEN: DDL của V1-NN có cột NOT NULL ──
		assertThat(schema.columnNamed(V1NN_SCHEMA, "purchase_order", "customer_id").nullable())
			.as("nullable = false được dịch thành NOT NULL trong DDL")
			.isFalse();

		// ── WHEN: persist order với customer = null ──
		sql.reset();
		Throwable failure = catchThrowable(() -> inTx(() -> {
			com.example.hibernatelab.g1_owning_side.v1nn_m2o_nullable_false.PurchaseOrder order = new com.example.hibernatelab.g1_owning_side.v1nn_m2o_nullable_false.PurchaseOrder(
				"A-001");
			em.persist(order);
			// Hibernate kiểm tra not-null ngay ở persist/flush và ném lỗi trước khi gửi INSERT
		}));

		// ── THEN: lỗi + SQL + DB thật ──
		System.out.println("[EXCEPTION] " + failure.getClass().getName() + ": " + failure.getMessage());
		assertThat(failure)
			.as("nullable = false khiến Hibernate coi customer là bắt buộc và tự từ chối")
			.isInstanceOf(PropertyValueException.class);
		sql.print();
		sql.assertInserts("purchase_order", 0); // DB chưa kịp từ chối vì Hibernate đã chặn
		assertThat(db.countOf(V1NN_SCHEMA + ".purchase_order")).as("không có dòng nào được ghi").isZero();
	}

	/**
	 * G1-S04c — V1-OPT ({@code optional = false}): HIBERNATE chặn trước, không gửi INSERT
	 *
	 * <p>Mapping: V1-OPT, {@code @ManyToOne(optional = false) @JoinColumn(name = "customer_id")}.
	 * <p><b>Câu hỏi</b>: {@code optional = false} xử lý order không customer thế nào, có khác V1-NN (S04b) không?
	 * <p><b>Kết luận</b>: Hibernate ném {@code PropertyValueException} TRƯỚC khi gửi SQL: 0 INSERT. Cùng DDL NOT NULL
	 * và cùng cách từ chối như V1-NN (S04b); điều khác biệt thật sự là so với V1 mặc định (S04a).
	 * <p>Xác minh: Hibernate 7.4.15.Final. Kiểu exception xem docs/RESULTS.md.
	 */
	@Test
	@DisplayName("G1-S04c | V1-OPT optional=false: HIBERNATE chặn trước khi gửi INSERT")
	void s04c_v1optOptionalFalseIsRejectedByHibernateBeforeSql() {
		// ── GIVEN: DDL của V1-OPT cũng NOT NULL ──
		assertThat(schema.columnNamed(V1OPT_SCHEMA, "purchase_order", "customer_id").nullable())
			.as("optional = false cũng làm cột NOT NULL trong DDL")
			.isFalse();

		// ── WHEN: persist order với customer = null ──
		sql.reset();
		Throwable failure = catchThrowable(() -> inTx(() -> {
			com.example.hibernatelab.g1_owning_side.v1opt_m2o_optional_false.PurchaseOrder order = new com.example.hibernatelab.g1_owning_side.v1opt_m2o_optional_false.PurchaseOrder(
				"A-001");
			em.persist(order);
			// [FLUSH] Hibernate kiểm tra not-null ở persist/flush và ném lỗi trước khi gửi INSERT
		}));

		// ── THEN: lỗi + SQL + DB thật ──
		System.out.println("[EXCEPTION] " + failure.getClass().getName() + ": " + failure.getMessage());
		assertThat(failure)
			.as("optional = false: Hibernate từ chối order thiếu customer")
			.isInstanceOf(PropertyValueException.class);
		sql.print();
		sql.assertInserts("purchase_order", 0); // khác V1-NN: chưa có câu INSERT nào được gửi
		assertThat(db.countOf(V1OPT_SCHEMA + ".purchase_order")).as("không có dòng nào được ghi").isZero();
	}

	/**
	 * G1-S04d — V1-OPT: vì quan hệ luôn có giá trị nên SELECT dùng INNER JOIN
	 *
	 * <p>Mapping: V1-OPT như S04c.
	 * <p><b>Câu hỏi</b>: Biết quan hệ không bao giờ NULL thì Hibernate tải order khác đi thế nào?
	 * <p><b>Kết luận</b>: Dùng {@code join} (inner) thay vì {@code left join} của V1 (S04a): Hibernate biết mọi order
	 * đều có customer nên không cần giữ dòng thiếu. {@code optional} vừa ảnh hưởng DDL vừa ảnh hưởng hành vi runtime.
	 * <p>Xác minh: Hibernate 7.4.15.Final (Hibernate 6+ viết inner join là {@code join}, không có chữ inner)
	 */
	@Test
	@DisplayName("G1-S04d | V1-OPT optional=false: find dùng INNER JOIN (khác LEFT JOIN của V1)")
	void s04d_v1optOptionalFalseUsesInnerJoin() {
		// ── GIVEN: một customer và một order của V1-OPT đã có trong DB ──
		Long orderId = inTxReturning(() -> {
			com.example.hibernatelab.g1_owning_side.v1opt_m2o_optional_false.Customer customerAn = new com.example.hibernatelab.g1_owning_side.v1opt_m2o_optional_false.Customer(
				"An");
			em.persist(customerAn);
			com.example.hibernatelab.g1_owning_side.v1opt_m2o_optional_false.PurchaseOrder order = new com.example.hibernatelab.g1_owning_side.v1opt_m2o_optional_false.PurchaseOrder(
				"A-001");
			order.setCustomer(customerAn);
			em.persist(order);
			return order.getId();
		});

		// ── WHEN: tải order bằng find (customer EAGER) ──
		sql.reset();
		inTx(() -> em.find(com.example.hibernatelab.g1_owning_side.v1opt_m2o_optional_false.PurchaseOrder.class, orderId));

		// ── THEN: SQL ──
		sql.print();
		sql.assertSomeStatementContains("join");
		sql.assertNoStatementContains("left join");
	}

	/**
	 * G1-S04e — V1-NN 🔬: {@code nullable = false} CŨNG đổi sang INNER JOIN (giống {@code optional = false})
	 *
	 * <p>Mapping: V1-NN, {@code @ManyToOne} (optional mặc định true) + {@code @JoinColumn(nullable = false)}.
	 * <p><b>Câu hỏi</b>: Chỉ khai báo {@code nullable = false} ở {@code @JoinColumn} (không đụng {@code optional}),
	 * Hibernate tải order bằng {@code left join} hay {@code join}?
	 * <p><b>Kết luận</b>: {@code join} (inner), giống V1-OPT ở S04d. Hibernate 7.4.15 suy ra "quan hệ bắt buộc" từ
	 * CẢ HAI cách khai báo, nên {@code nullable = false} KHÔNG chỉ là chỉ dẫn cho DDL như tài liệu hay nói.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S04e | V1-NN nullable=false: find cũng dùng INNER JOIN (giống optional=false)")
	void s04e_v1nnNullableFalseAlsoUsesInnerJoin() {
		// ── GIVEN: một customer và một order của V1-NN đã có trong DB ──
		Long orderId = inTxReturning(() -> {
			com.example.hibernatelab.g1_owning_side.v1nn_m2o_nullable_false.Customer customerAn = new com.example.hibernatelab.g1_owning_side.v1nn_m2o_nullable_false.Customer(
				"An");
			em.persist(customerAn);
			com.example.hibernatelab.g1_owning_side.v1nn_m2o_nullable_false.PurchaseOrder order = new com.example.hibernatelab.g1_owning_side.v1nn_m2o_nullable_false.PurchaseOrder(
				"A-001");
			order.setCustomer(customerAn);
			em.persist(order);
			return order.getId();
		});

		// ── WHEN: tải order bằng find (customer EAGER) ──
		sql.reset();
		inTx(() -> em.find(com.example.hibernatelab.g1_owning_side.v1nn_m2o_nullable_false.PurchaseOrder.class, orderId));

		// ── THEN: SQL ──
		sql.print();
		sql.assertSomeStatementContains("join");
		sql.assertNoStatementContains("left join");
	}
}
