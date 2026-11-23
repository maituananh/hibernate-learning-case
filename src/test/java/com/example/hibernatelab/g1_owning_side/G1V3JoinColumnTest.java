package com.example.hibernatelab.g1_owning_side;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g1_owning_side.v3_o2m_uni_joincolumn.Customer;
import com.example.hibernatelab.g1_owning_side.v3_o2m_uni_joincolumn.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Biến thể V3: {@code @OneToMany @JoinColumn(name = "customer_id")} MỘT CHIỀU ở {@code Customer}.
 * FK nằm ở bảng con nhưng được GHI từ phía cha. Gồm S09 (DDL và INSERT + UPDATE) và S11 (bỏ order khỏi collection).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G1V3JoinColumnTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g1_v3_o2m_uni_joincolumn";
	private static final String PURCHASE_ORDER = SCHEMA + ".purchase_order";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/**
	 * G1-S09a — {@code @OneToMany + @JoinColumn} một chiều: DDL không có bảng nối, FK ở bảng con và nullable
	 *
	 * <p>Mapping: {@code Customer.orders} là OWNING side duy nhất ({@code @OneToMany @JoinColumn(name = "customer_id")}).
	 * <p><b>Câu hỏi</b>: Thêm {@code @JoinColumn} thì DDL khác V2 (bảng nối) thế nào?
	 * <p><b>Kết luận</b>: Không còn bảng nối. Cột {@code customer_id} nằm ở {@code purchase_order} (bảng con), nullable,
	 * có FK tới {@code customer(id)}, dù class {@code PurchaseOrder} không hề có field {@code customer}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S09a | V3 @OneToMany + @JoinColumn: không có bảng nối, purchase_order.customer_id nullable")
	void s09a_joinColumnPutsNullableForeignKeyInChildTable() {
		// ── GIVEN: không cần dữ liệu, chỉ xem DDL ──

		// ── THEN: DDL ──
		schema.printDdl(SCHEMA);

		assertThat(schema.tablesOf(SCHEMA))
			.as("có @JoinColumn thì KHÔNG có bảng nối: chỉ còn 4 bảng nghiệp vụ")
			.containsExactly("customer", "order_item", "product", "purchase_order");
		assertThat(schema.columnNamed(SCHEMA, "purchase_order", "customer_id").nullable())
			.as("không ghi nullable = false nên cột cho phép NULL (cần thiết vì order được INSERT trước khi UPDATE FK)")
			.isTrue();
		assertThat(schema.foreignKeyOnColumn(SCHEMA, "purchase_order", "customer_id").targetTable())
			.as("FK từ purchase_order trỏ về customer")
			.isEqualTo(SCHEMA + ".customer");
	}

	/**
	 * G1-S09b — Thêm 2 order qua collection của V3 🔬: INSERT (customer_id = NULL) rồi UPDATE bù FK
	 *
	 * <p>Mapping: như S09a.
	 * <p><b>Câu hỏi</b>: Cột FK nằm trong bảng con nhưng chỉ phía cha biết quan hệ. Hibernate ghi FK bằng cách nào?
	 * <p><b>Kết luận</b>: INSERT từng order với {@code customer_id = NULL}, rồi UPDATE {@code set customer_id = ?} cho
	 * từng order: 2 INSERT + 2 UPDATE. Phía "một" không nằm trong bảng con nên phải UPDATE bù.
	 * <p>Xác minh: Hibernate 7.4.15.Final (thứ tự và số câu thực tế xem docs/RESULTS.md)
	 */
	@Test
	@DisplayName("G1-S09b | V3 thêm 2 order: 2 INSERT (customer_id = NULL) + 2 UPDATE bù customer_id")
	void s09b_addingOrdersInsertsThenUpdatesTheForeignKey() {
		// ── GIVEN: DB trống ──

		// ── WHEN: một transaction persist customer, 2 order, rồi add order vào collection ──
		sql.reset();
		Customer customerAn = inTxReturning(() -> {
			Customer customer = new Customer("An");
			PurchaseOrder orderA1 = new PurchaseOrder("A-001");
			PurchaseOrder orderA2 = new PurchaseOrder("A-002");
			em.persist(customer);
			em.persist(orderA1);
			em.persist(orderA2);
			// [LIFECYCLE] cả ba MANAGED (G1 không cascade nên phải persist từng entity)
			assertState(em, customer, MANAGED);
			assertState(em, orderA1, MANAGED);

			customer.getOrders().add(orderA1);
			customer.getOrders().add(orderA2);
			// [FLUSH] lúc commit: INSERT customer, 2 INSERT purchase_order (chưa có FK), rồi 2 UPDATE set customer_id
			return customer;
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("customer", 1);
		sql.assertInserts("purchase_order", 2);
		sql.assertUpdates("purchase_order", 2);

		// ── THEN: DB thật ──
		db.print(PURCHASE_ORDER);
		List<Map<String, Object>> orderRows = db.rowsOf(PURCHASE_ORDER);
		assertThat(orderRows).as("2 order đã được lưu").hasSize(2);
		assertThat(orderRows.get(0).get("customer_id"))
			.as("sau UPDATE bù, FK của order đầu trỏ về An")
			.isEqualTo(customerAn.getId());
		assertThat(orderRows.get(1).get("customer_id"))
			.as("sau UPDATE bù, FK của order thứ hai trỏ về An")
			.isEqualTo(customerAn.getId());
	}

	/**
	 * G1-S11 — V3: bỏ order khỏi collection chỉ đặt FK về NULL, dòng order vẫn còn
	 *
	 * <p>Mapping: {@code Customer.orders} OWNING side, {@code @JoinColumn}, không cascade, không orphanRemoval.
	 * <p><b>Câu hỏi</b>: Gỡ một order khỏi {@code customer.getOrders()} thì order có bị xoá không?
	 * <p><b>Kết luận</b>: Không. Hibernate chỉ {@code UPDATE purchase_order set customer_id = NULL}. Dòng order vẫn
	 * còn trong DB thành order "mồ côi" (không thuộc customer nào). Muốn xoá hẳn phải dùng orphanRemoval (nhóm G3).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S11 | V3 gỡ order khỏi collection: UPDATE customer_id = NULL, dòng order vẫn còn")
	void s11_removingFromCollectionOnlyClearsTheForeignKey() {
		// ── GIVEN: An có 2 order, FK đã được ghi ──
		Long customerId = inTxReturning(() -> {
			Customer customer = new Customer("An");
			PurchaseOrder orderA1 = new PurchaseOrder("A-001");
			PurchaseOrder orderA2 = new PurchaseOrder("A-002");
			em.persist(customer);
			em.persist(orderA1);
			em.persist(orderA2);
			customer.getOrders().add(orderA1);
			customer.getOrders().add(orderA2);
			return customer.getId();
		});
		assertThat(db.countOf(PURCHASE_ORDER)).as("điều kiện đầu: 2 order").isEqualTo(2);

		// ── WHEN: tải customer rồi gỡ order ở vị trí 0 khỏi collection ──
		sql.reset();
		inTx(() -> {
			Customer customer = em.find(Customer.class, customerId);
			assertState(em, customer, MANAGED);
			customer.getOrders().remove(0); // chạm vào collection LAZY → SELECT order của customer
			// [FLUSH] lúc commit: UPDATE purchase_order set customer_id = NULL cho order bị gỡ
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("purchase_order", 1);
		sql.assertDeletes("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(PURCHASE_ORDER);
		assertThat(db.countOf(PURCHASE_ORDER)).as("không có orphanRemoval nên không order nào bị xoá").isEqualTo(2);
		long ordersWithoutCustomer = 0;
		for (Map<String, Object> row : db.rowsOf(PURCHASE_ORDER)) {
			if (row.get("customer_id") == null) {
				ordersWithoutCustomer++;
			}
		}
		assertThat(ordersWithoutCustomer)
			.as("đúng 1 order trở thành mồ côi (customer_id = NULL), order còn lại vẫn thuộc An")
			.isEqualTo(1);
	}
}
