package com.example.hibernatelab.g1_owning_side;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g1_owning_side.v5_bidi_no_mappedby.Customer;
import com.example.hibernatelab.g1_owning_side.v5_bidi_no_mappedby.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Biến thể V5: hai chiều nhưng QUÊN {@code mappedBy}. Có HAI owning side độc lập:
 * {@code PurchaseOrder.customer} (ghi cột {@code purchase_order.customer_id}) và
 * {@code Customer.orders} (ghi bảng nối {@code customer_orders}). Gồm G1-S15 (a, b, c).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G1V5NoMappedByTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g1_v5_bidi_no_mappedby";
	private static final String PURCHASE_ORDER = SCHEMA + ".purchase_order";
	private static final String JOIN_TABLE = SCHEMA + ".customer_orders";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/**
	 * G1-S15a — V5: quên {@code mappedBy} → DDL có CẢ cột FK LẪN bảng nối
	 *
	 * <p>Mapping: {@code PurchaseOrder.customer} OWNING side #1; {@code Customer.orders} (không {@code mappedBy}) OWNING side #2.
	 * <p><b>Câu hỏi</b>: Khai báo hai chiều mà quên {@code mappedBy} thì Hibernate tạo cấu trúc bảng thế nào?
	 * <p><b>Kết luận</b>: Nó coi đây là HAI quan hệ một chiều riêng biệt, nên có cả {@code purchase_order.customer_id}
	 * (từ {@code @ManyToOne}) lẫn bảng nối {@code customer_orders} (từ {@code @OneToMany}). Cùng một ý nghĩa
	 * "order thuộc customer" được lưu ở HAI chỗ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S15a | V5 quên mappedBy: DDL có purchase_order.customer_id VÀ bảng nối customer_orders")
	void s15a_forgettingMappedByCreatesBothForeignKeyAndJoinTable() {
		// ── GIVEN: không cần dữ liệu, chỉ xem DDL ──

		// ── THEN: DDL ──
		schema.printDdl(SCHEMA);
		assertThat(schema.hasColumn(SCHEMA, "purchase_order", "customer_id"))
			.as("owning side #1 (@ManyToOne) tạo cột FK ở bảng con")
			.isTrue();
		assertThat(schema.tablesOf(SCHEMA))
			.as("owning side #2 (@OneToMany không mappedBy) tạo thêm bảng nối customer_orders")
			.contains("customer_orders");
	}

	/**
	 * G1-S15b — V5: set cả hai phía → dữ liệu bị ghi HAI lần
	 *
	 * <p>Mapping: như S15a.
	 * <p><b>Câu hỏi</b>: Dùng đúng thói quen hai chiều (set cả {@code order.setCustomer} lẫn {@code orders.add}) thì sao?
	 * <p><b>Kết luận</b>: Mỗi owning side tự ghi phần của mình: INSERT order (có FK {@code customer_id}) VÀ INSERT một
	 * dòng bảng nối. Cùng một sự thật bị lưu ở hai nơi, tốn thêm một câu INSERT so với V4.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S15b | V5 set cả hai phía: INSERT order (có FK) + INSERT bảng nối, ghi dữ liệu 2 lần")
	void s15b_settingBothSidesWritesTheRelationshipTwice() {
		// ── GIVEN: DB trống ──

		// ── WHEN: persist customer và order, set cả hai phía ──
		sql.reset();
		PurchaseOrder orderA1 = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			PurchaseOrder order = new PurchaseOrder("A-001");
			em.persist(customerAn);
			order.setCustomer(customerAn); // owning side #1 → cột customer_id
			customerAn.getOrders().add(order); // owning side #2 → bảng nối
			em.persist(order);
			// [LIFECYCLE] cả hai MANAGED
			assertState(em, customerAn, MANAGED);
			assertState(em, order, MANAGED);
			return order;
			// [FLUSH] lúc commit: INSERT customer, INSERT purchase_order (có customer_id), INSERT customer_orders
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
		sql.assertInserts("customer_orders", 1);

		// ── THEN: DB thật ──
		db.print(PURCHASE_ORDER);
		db.print(JOIN_TABLE);
		assertThat(db.rowById(PURCHASE_ORDER, orderA1.getId()).get("customer_id"))
			.as("owning side #1 đã ghi FK vào purchase_order")
			.isNotNull();
		assertThat(db.countOf(JOIN_TABLE))
			.as("owning side #2 ghi thêm một dòng bảng nối: cùng quan hệ bị lưu hai nơi")
			.isEqualTo(1);
	}

	/**
	 * G1-S15c — V5: chỉ set phía {@code @ManyToOne} → hai nguồn sự thật MÂU THUẪN nhau
	 *
	 * <p>Mapping: như S15a.
	 * <p><b>Câu hỏi</b>: Chỉ gọi {@code order.setCustomer(An)} (cách quen thuộc của V4) thì {@code customer.getOrders()} ra gì?
	 * <p><b>Kết luận</b>: FK = An (nên "order.customer = An"), nhưng bảng nối trống (nên "customer.orders = rỗng").
	 * Cùng một câu hỏi "order này của ai?" có hai đáp án khác nhau tuỳ đi đường nào. Đó là lý do phải có {@code mappedBy}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S15c | V5 chỉ setCustomer: order.customer = An nhưng customer.orders rỗng (mâu thuẫn)")
	void s15c_settingOnlyManyToOneLeavesTwoContradictingTruths() {
		// ── GIVEN: customer An và một order chỉ được nối bằng order.setCustomer ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			return customerAn.getId();
		});
		PurchaseOrder orderA1 = inTxReturning(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			PurchaseOrder order = new PurchaseOrder("A-001");
			order.setCustomer(customerAn); // chỉ owning side #1
			em.persist(order);
			return order;
		});

		// ── THEN: DB thật — nguồn sự thật số 1 (FK) và số 2 (bảng nối) ──
		db.print(PURCHASE_ORDER);
		db.print(JOIN_TABLE);
		assertThat(db.rowById(PURCHASE_ORDER, orderA1.getId()).get("customer_id"))
			.as("nguồn #1: FK nói order này thuộc An")
			.isEqualTo(customerId);
		assertThat(db.countOf(JOIN_TABLE))
			.as("nguồn #2: bảng nối trống, nó không biết order này thuộc ai")
			.isZero();

		// ── THEN: điều hướng từ phía customer trong một transaction mới ──
		inTx(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			assertThat(customerAn.getOrders())
				.as("customer.getOrders() đọc từ bảng nối (trống), nên KHÔNG thấy order dù FK đã trỏ về An")
				.isEmpty();
		});
	}
}
