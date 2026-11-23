package com.example.hibernatelab.g1_owning_side;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g1_owning_side.v6_bidi_owner_parent.Customer;
import com.example.hibernatelab.g1_owning_side.v6_bidi_owner_parent.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Biến thể V6 (➕): owning side nằm ở phía "một". {@code Customer.orders} có {@code @JoinColumn} (ghi FK),
 * còn {@code PurchaseOrder.customer} bị tắt quyền ghi ({@code insertable = false, updatable = false}).
 * Đây là V4 lật ngược. Gồm G1-S16 (a, b).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G1V6OwnerParentTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g1_v6_bidi_owner_parent";
	private static final String PURCHASE_ORDER = SCHEMA + ".purchase_order";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/**
	 * G1-S16a — V6: chỉ {@code order.setCustomer(c)} → FK KHÔNG được ghi
	 *
	 * <p>Mapping: {@code Customer.orders} OWNING side ({@code @JoinColumn}); {@code PurchaseOrder.customer} chỉ đọc
	 * ({@code insertable = false, updatable = false}).
	 * <p><b>Câu hỏi</b>: Thói quen quen thuộc của V4 ({@code order.setCustomer}) còn đúng ở V6 không?
	 * <p><b>Kết luận</b>: Không. Field {@code customer} bị tắt quyền ghi nên Hibernate bỏ qua: {@code customer_id = NULL}.
	 * Ở V6 "owning side" là phía có quyền ghi, tức collection ở {@code Customer}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S16a | V6 chỉ order.setCustomer (read-only): FK không được ghi")
	void s16a_settingTheReadOnlyManyToOneDoesNotWriteTheForeignKey() {
		// ── GIVEN: customer An đã có trong DB ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN: chỉ gán order.customer, không đụng collection ──
		sql.reset();
		PurchaseOrder orderA1 = inTxReturning(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			PurchaseOrder order = new PurchaseOrder("A-001");
			order.setCustomer(customerAn);
			em.persist(order);
			// [LIFECYCLE] order: TRANSIENT → MANAGED
			assertState(em, order, MANAGED);
			return order;
			// [FLUSH] lúc commit: INSERT purchase_order không có customer_id (field bị insertable = false)
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
		sql.assertUpdates("purchase_order", 0);
		db.print(PURCHASE_ORDER);
		assertThat(db.rowById(PURCHASE_ORDER, orderA1.getId()).get("customer_id"))
			.as("PurchaseOrder.customer chỉ đọc (insertable/updatable = false) nên FK không được ghi")
			.isNull();
	}

	/**
	 * G1-S16b — V6 🔬: chỉ {@code customer.getOrders().add(order)} → FK ĐƯỢC ghi
	 *
	 * <p>Mapping: như S16a.
	 * <p><b>Câu hỏi</b>: Add order vào collection (phía có quyền ghi) thì FK có được ghi không, bằng mấy câu SQL?
	 * <p><b>Kết luận</b>: Được. Giống V3: INSERT order (chưa có FK) rồi UPDATE {@code set customer_id}. Bài học: V6 là V4
	 * lật ngược, "owning side" luôn là phía có quyền ghi cột FK, không phụ thuộc phía nào nằm ở bảng nào.
	 * <p>Xác minh: Hibernate 7.4.15.Final (số câu SQL thực tế xem docs/RESULTS.md)
	 */
	@Test
	@DisplayName("G1-S16b | V6 chỉ customer.getOrders().add (owning): FK được ghi (INSERT rồi UPDATE)")
	void s16b_addingToTheOwningCollectionWritesTheForeignKey() {
		// ── GIVEN: customer An đã có trong DB ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN: chỉ add order vào collection, không gọi order.setCustomer ──
		sql.reset();
		PurchaseOrder orderA1 = inTxReturning(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			PurchaseOrder order = new PurchaseOrder("A-001");
			em.persist(order);
			customerAn.getOrders().add(order);
			// [LIFECYCLE] cả hai MANAGED
			assertState(em, order, MANAGED);
			return order;
			// [FLUSH] lúc commit: INSERT purchase_order, rồi UPDATE purchase_order set customer_id
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
		sql.assertUpdates("purchase_order", 1);
		db.print(PURCHASE_ORDER);
		assertThat(db.rowById(PURCHASE_ORDER, orderA1.getId()).get("customer_id"))
			.as("Customer.orders có @JoinColumn (owning side) nên FK được ghi dù order.customer vẫn null")
			.isEqualTo(customerId);
	}
}
