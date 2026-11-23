package com.example.hibernatelab.g5_fetch_n_plus_one;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g5_fetch_n_plus_one.f0_defaults.F0PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G5, biến thể F0 (mặc định JPA, không ghi {@code fetch}): S01–S02.
 *
 * <p>Câu hỏi xuyên suốt: {@code @ManyToOne} mặc định là EAGER, vậy "EAGER" nghĩa là gì về số câu SQL?
 * Bài học chính: EAGER chỉ hứa "sẽ tải ngay", KHÔNG hứa "tải bằng JOIN". Tải bằng JOIN hay bằng thêm SELECT
 * tuỳ cách ta lấy entity ({@code find} hay JPQL).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G5DefaultFetchTest extends AbstractScenarioTest {

	private static final String F0 = "g5_f0_defaults";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { F0 };
	}

	/**
	 * G5-S01 — F0: {@code em.find(order)} với {@code @ManyToOne} EAGER mặc định
	 *
	 * <p>Mapping: {@code PurchaseOrder.customer} là OWNING side, KHÔNG ghi {@code fetch} (mặc định EAGER).
	 * <p><b>Câu hỏi</b>: chỉ xin một order, Hibernate chạy bao nhiêu câu SQL và customer có được tải không?
	 * <p><b>Kết luận</b>: 1 SELECT duy nhất, có {@code left join customer}. {@code find()} biết trước phải lấy customer nên
	 * nối luôn vào cùng câu. Customer là object thật (không phải proxy) và đã được khởi tạo.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S01 | F0: find(order) với @ManyToOne EAGER mặc định → 1 SELECT có left join customer")
	void s01_findOrderLoadsEagerCustomerWithOneJoin() {
		// ── GIVEN: 5 customer × 2 order × 2 item, 3 payment (xem G5Fixtures) ──
		G5Fixtures.seed(jdbc, F0);
		Long orderId = G5Fixtures.firstOrderId();

		// ── WHEN: find một order ──
		sql.reset();
		inTx(() -> {
			F0PurchaseOrder orderA = em.find(F0PurchaseOrder.class, orderId);
			// [LIFECYCLE] order MANAGED; customer cũng đã MANAGED và đã khởi tạo (EAGER)
			assertState(em, orderA, MANAGED);
			assertState(em, orderA.getCustomer(), MANAGED);
			assertThat(Hibernate.isInitialized(orderA.getCustomer()))
				.as("@ManyToOne mặc định là EAGER nên customer đã được tải sẵn ngay sau find()")
				.isTrue();
			// [FLUSH] không có thay đổi nào nên không có SQL ghi
			// [TX END] commit
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("left join");
		sql.assertSelects("customer", 1);

		// ── THEN: DB thật ──
		db.print(F0 + ".purchase_order");
		assertThat(db.countOf(F0 + ".purchase_order")).as("find chỉ đọc nên seed không bị đụng").isEqualTo(G5Fixtures.ORDER_COUNT);
	}

	/**
	 * G5-S02 — F0: JPQL {@code select o from PurchaseOrder o}, EAGER sinh N+1
	 *
	 * <p>Mapping: như S01.
	 * <p><b>Câu hỏi</b>: lấy cả 10 order bằng JPQL thì EAGER có còn "nối luôn" như {@code find()} không?
	 * <p><b>Kết luận</b>: không. JPQL được dịch NGUYÊN VĂN sang SQL (không tự thêm JOIN), nên 1 SELECT order, rồi Hibernate
	 * thấy 10 order trỏ tới 5 customer chưa có trong persistence context và chạy thêm 5 SELECT (mỗi customer khác nhau 1 lần).
	 * Đây chính là N+1. EAGER vì thế là "bẫy": trông như tiện, thực ra mất kiểm soát số câu SQL.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S02 | F0: JPQL select order với @ManyToOne EAGER → 1 SELECT order + 5 SELECT customer (N+1)")
	void s02_jpqlOnEagerManyToOneCausesNPlusOne() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F0);

		// ── WHEN: JPQL lấy mọi order ──
		sql.reset();
		inTx(() -> {
			List<F0PurchaseOrder> orders = em
				.createQuery("select o from G5F0_PurchaseOrder o", F0PurchaseOrder.class)
				.getResultList();
			// [LIFECYCLE] cả 10 order MANAGED, và customer của chúng đã được tải (EAGER) dù ta chưa hề đụng tới
			assertThat(orders).as("seed có 10 order").hasSize(G5Fixtures.ORDER_COUNT);
			assertState(em, orders.get(0), MANAGED);
			assertState(em, orders.get(0).getCustomer(), MANAGED);
			// [FLUSH] không có thay đổi nên không có SQL ghi
			// [TX END] commit
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("purchase_order", 1);
		sql.assertSelects("customer", G5Fixtures.CUSTOMER_COUNT);
		sql.assertTotal(1 + G5Fixtures.CUSTOMER_COUNT);
		sql.assertNoStatementContains("join");

		// ── THEN: DB thật ──
		db.print(F0 + ".customer");
		assertThat(db.countOf(F0 + ".customer")).as("DB có 5 customer, khớp 5 SELECT lẻ").isEqualTo(G5Fixtures.CUSTOMER_COUNT);
	}
}
