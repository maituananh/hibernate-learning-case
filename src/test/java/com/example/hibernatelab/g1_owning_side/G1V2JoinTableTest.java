package com.example.hibernatelab.g1_owning_side;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g1_owning_side.v2_o2m_uni_jointable.Customer;
import com.example.hibernatelab.g1_owning_side.v2_o2m_uni_jointable.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Biến thể V2: {@code @OneToMany} MỘT CHIỀU ở {@code Customer}, KHÔNG có {@code @JoinColumn} → Hibernate dùng bảng nối
 * {@code customer_orders}. Gồm S07 (DDL và số SQL) và S08 (xoá một phần tử khỏi List).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G1V2JoinTableTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g1_v2_o2m_uni_jointable";
	private static final String PURCHASE_ORDER = SCHEMA + ".purchase_order";
	private static final String JOIN_TABLE = SCHEMA + ".customer_orders";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/**
	 * G1-S07a — {@code @OneToMany} một chiều KHÔNG {@code @JoinColumn}: DDL có bảng nối, bảng con không có FK
	 *
	 * <p>Mapping: {@code Customer.orders} là OWNING side duy nhất ({@code @OneToMany}, không {@code mappedBy}, không {@code @JoinColumn}).
	 * <p><b>Câu hỏi</b>: FK nằm ở đâu khi chỉ khai báo {@code @OneToMany} ở phía cha mà không nói gì thêm?
	 * <p><b>Kết luận</b>: Không nằm ở {@code purchase_order}. Hibernate tạo BẢNG NỐI {@code customer_orders} (tên suy ra
	 * từ <i>tên bảng chủ + tên field</i>) gồm 2 cột FK, và đặt UNIQUE trên cột order vì mỗi order chỉ thuộc một customer.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S07a | @OneToMany không @JoinColumn: sinh bảng nối customer_orders, purchase_order không có customer_id")
	void s07a_oneToManyWithoutJoinColumnCreatesJoinTable() {
		// ── GIVEN: không cần dữ liệu, chỉ xem DDL ──

		// ── THEN: DDL ──
		schema.printDdl(SCHEMA);

		assertThat(schema.tablesOf(SCHEMA))
			.as("ngoài 4 bảng nghiệp vụ còn có bảng nối customer_orders do @OneToMany không @JoinColumn")
			.contains("customer", "purchase_order", "order_item", "product", "customer_orders");
		assertThat(schema.hasColumn(SCHEMA, "purchase_order", "customer_id"))
			.as("bảng con KHÔNG có cột FK: quan hệ nằm hẳn trong bảng nối")
			.isFalse();
		assertThat(schema.foreignKeyOnColumn(SCHEMA, "customer_orders", "customer_id").targetTable())
			.as("một cột của bảng nối trỏ về customer")
			.isEqualTo(SCHEMA + ".customer");
		assertThat(schema.foreignKeyOnColumn(SCHEMA, "customer_orders", "orders_id").targetTable())
			.as("cột còn lại của bảng nối trỏ về purchase_order")
			.isEqualTo(PURCHASE_ORDER);
		assertThat(schema.uniqueConstraintsOf(SCHEMA, "customer_orders"))
			.as("quan hệ một-nhiều: mỗi order chỉ xuất hiện MỘT lần trong bảng nối, nên cột order là UNIQUE")
			.contains(List.of("orders_id"));
	}

	/**
	 * G1-S07b — Thêm 2 order cho một customer: 5 câu INSERT (có 2 INSERT cho bảng nối)
	 *
	 * <p>Mapping: như S07a.
	 * <p><b>Câu hỏi</b>: Persist customer và 2 order rồi add vào collection thì tốn bao nhiêu câu INSERT?
	 * <p><b>Kết luận</b>: 5 = 1 customer + 2 order + 2 dòng bảng nối. Mỗi order tốn thêm một INSERT chỉ để ghi quan hệ.
	 * Đây là lý do không nên dùng {@code @OneToMany} một chiều trơn.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S07b | V2 thêm 2 order: 1 + 2 INSERT entity + 2 INSERT bảng nối = 5")
	void s07b_addingTwoOrdersCostsFiveInserts() {
		// ── GIVEN: DB trống (DbCleaner đã dọn) ──

		// ── WHEN: một transaction persist customer, 2 order, rồi add order vào collection ──
		sql.reset();
		inTx(() -> {
			Customer customerAn = new Customer("An");
			PurchaseOrder orderA1 = new PurchaseOrder("A-001");
			PurchaseOrder orderA2 = new PurchaseOrder("A-002");
			em.persist(customerAn);
			em.persist(orderA1);
			em.persist(orderA2);
			// [LIFECYCLE] cả ba: TRANSIENT → MANAGED (G1 không cascade nên phải persist từng entity)
			assertState(em, customerAn, MANAGED);
			assertState(em, orderA1, MANAGED);
			assertState(em, orderA2, MANAGED);

			customerAn.getOrders().add(orderA1);
			customerAn.getOrders().add(orderA2);
			// [FLUSH] lúc commit: INSERT customer, 2 INSERT purchase_order, rồi 2 INSERT customer_orders
		});
		// [TX END] persistence context đóng → customer, order: MANAGED → DETACHED

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("customer", 1);
		sql.assertInserts("purchase_order", 2);
		sql.assertInserts("customer_orders", 2);
		sql.assertTotal(5);

		// ── THEN: DB thật ──
		db.print(JOIN_TABLE);
		assertThat(db.countOf(JOIN_TABLE)).as("mỗi order có đúng một dòng trong bảng nối").isEqualTo(2);
		assertThat(db.countOf(PURCHASE_ORDER)).as("2 order đã được lưu vào bảng con").isEqualTo(2);
	}

	/**
	 * G1-S08 — Xoá MỘT phần tử khỏi List 🔬: Hibernate xoá HẾT dòng bảng nối của customer rồi INSERT lại phần còn lại
	 *
	 * <p>Mapping: {@code Customer.orders} là {@code List} không {@code @OrderColumn} ("bag"), OWNING side, bảng nối.
	 * <p><b>Câu hỏi</b>: Customer có 3 order, gỡ 1 order khỏi {@code orders} thì bảng nối bị đổi thế nào?
	 * <p><b>Kết luận</b>: Bag không biết dòng nào là dòng nào, nên Hibernate DELETE toàn bộ dòng bảng nối của customer
	 * rồi INSERT lại các dòng còn lại (3 dòng bị xoá, 2 dòng được ghi lại). Dòng order VẪN CÒN trong
	 * {@code purchase_order} vì không có orphanRemoval. So sánh với {@code Set} ở G7-S05.
	 * <p>Xác minh: Hibernate 7.4.15.Final (kết quả thực tế xem docs/RESULTS.md)
	 */
	@Test
	@DisplayName("G1-S08 | V2 gỡ 1/3 order khỏi List: xoá cả bảng nối của customer rồi ghi lại 2 dòng")
	void s08_removingOneElementFromBagRecreatesTheWholeJoinTableRows() {
		// ── GIVEN: customer An có 3 order, quan hệ đã ghi vào bảng nối ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			PurchaseOrder orderA1 = new PurchaseOrder("A-001");
			PurchaseOrder orderA2 = new PurchaseOrder("A-002");
			PurchaseOrder orderA3 = new PurchaseOrder("A-003");
			em.persist(customerAn);
			em.persist(orderA1);
			em.persist(orderA2);
			em.persist(orderA3);
			customerAn.getOrders().add(orderA1);
			customerAn.getOrders().add(orderA2);
			customerAn.getOrders().add(orderA3);
			return customerAn.getId();
		});
		assertThat(db.countOf(JOIN_TABLE)).as("điều kiện đầu: bảng nối có 3 dòng").isEqualTo(3);

		// ── WHEN: tải customer rồi gỡ phần tử ở vị trí 1 ──
		sql.reset();
		inTx(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			// [LIFECYCLE] customerAn: MANAGED (do find)
			assertState(em, customerAn, MANAGED);
			customerAn.getOrders().remove(1); // chạm vào collection LAZY → SELECT bảng nối + order
			sql.mark("remove(1) xong; chờ commit để flush");
			// [FLUSH] lúc commit: collection bị đổi → Hibernate tái tạo các dòng bảng nối
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("customer_orders", 1);
		sql.assertInserts("customer_orders", 2);
		sql.assertDeletes("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(JOIN_TABLE);
		db.print(PURCHASE_ORDER);
		assertThat(db.countOf(JOIN_TABLE)).as("bảng nối còn 2 dòng sau khi gỡ 1 order khỏi collection").isEqualTo(2);
		assertThat(db.countOf(PURCHASE_ORDER))
			.as("không có orphanRemoval/cascade nên cả 3 dòng order vẫn còn, chỉ là 1 order không còn thuộc customer")
			.isEqualTo(3);
	}
}
