package com.example.hibernatelab.g9_integrated;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.REMOVED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.hibernatelab.g9_integrated.s06_delete_strategies.CustomerDeletionService;
import com.example.hibernatelab.g9_integrated.s06_delete_strategies.a_cascade_remove.Customer;
import com.example.hibernatelab.g9_integrated.s06_delete_strategies.a_cascade_remove.OrderItem;
import com.example.hibernatelab.g9_integrated.s06_delete_strategies.a_cascade_remove.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G9-S06 ⭐ So sánh chiến lược xoá {@code Customer → PurchaseOrder → OrderItem}.
 *
 * <p>Dữ liệu chung ở mọi biến thể: 1 customer, 2 order, mỗi order 2 item (tổng 1 + 2 + 4 = 7 dòng).
 * Bốn chiến lược: (a) cascade REMOVE, (b) {@code @OnDelete(CASCADE)}, (c) bulk delete từ dưới lên, (d) không chiến lược nào.
 * <p>Các class entity của ba biến thể trùng tên (khác package): biến thể (a) import thường, (b) và (c) dùng tên đầy đủ.
 * <p>Bảng so sánh cuối cùng nằm ở README của nhóm.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G9S06DeleteStrategiesTest extends AbstractScenarioTest {

	private static final String A = "g9_s06a_cascade_remove";
	private static final String B = "g9_s06b_on_delete";
	private static final String C = "g9_s06c_no_strategy";

	@Autowired
	private CustomerDeletionService deletionService;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { A, B, C };
	}

	private Long seedA() {
		Customer customerAn = new Customer("An");
		PurchaseOrder orderOne = new PurchaseOrder("A-001");
		orderOne.addItem(new OrderItem("PEN-01"));
		orderOne.addItem(new OrderItem("PEN-02"));
		PurchaseOrder orderTwo = new PurchaseOrder("A-002");
		orderTwo.addItem(new OrderItem("BOOK-01"));
		orderTwo.addItem(new OrderItem("BOOK-02"));
		customerAn.addOrder(orderOne);
		customerAn.addOrder(orderTwo);
		return inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(orderOne);
			em.persist(orderTwo);
			em.persist(orderOne.getItems().get(0));
			em.persist(orderOne.getItems().get(1));
			em.persist(orderTwo.getItems().get(0));
			em.persist(orderTwo.getItems().get(1));
			return customerAn.getId();
		});
	}

	private Long seedB() {
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.Customer customerAn =
			new com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.Customer("An");
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.PurchaseOrder orderOne =
			new com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.PurchaseOrder("A-001");
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.PurchaseOrder orderTwo =
			new com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.PurchaseOrder("A-002");
		customerAn.addOrder(orderOne);
		customerAn.addOrder(orderTwo);
		// Biến thể b KHÔNG cascade PERSIST nên phải persist từng entity một, cha trước con.
		return inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(orderOne);
			em.persist(orderTwo);
			persistTwoItems(orderOne, "PEN-01", "PEN-02");
			persistTwoItems(orderTwo, "BOOK-01", "BOOK-02");
			return customerAn.getId();
		});
	}

	private void persistTwoItems(com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.PurchaseOrder order,
		String firstSku, String secondSku) {
		em.persist(newItemB(order, firstSku));
		em.persist(newItemB(order, secondSku));
	}

	private com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.OrderItem newItemB(
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.PurchaseOrder order, String sku) {
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.OrderItem item =
			new com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.OrderItem(sku);
		order.addItem(item);
		return item;
	}

	private Long seedC() {
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.Customer customerAn =
			new com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.Customer("An");
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.PurchaseOrder orderOne =
			new com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.PurchaseOrder("A-001");
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.PurchaseOrder orderTwo =
			new com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.PurchaseOrder("A-002");
		customerAn.addOrder(orderOne);
		customerAn.addOrder(orderTwo);
		return inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(orderOne);
			em.persist(orderTwo);
			for (String sku : new String[] { "PEN-01", "PEN-02" }) {
				com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.OrderItem item =
					new com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.OrderItem(sku);
				orderOne.addItem(item);
				em.persist(item);
			}
			for (String sku : new String[] { "BOOK-01", "BOOK-02" }) {
				com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.OrderItem item =
					new com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.OrderItem(sku);
				orderTwo.addItem(item);
				em.persist(item);
			}
			return customerAn.getId();
		});
	}

	private void assertAllTablesEmpty(String schemaName, String reason) {
		assertThat(db.countOf(schemaName + ".customer")).as("customer: " + reason).isEqualTo(0);
		assertThat(db.countOf(schemaName + ".purchase_order")).as("purchase_order: " + reason).isEqualTo(0);
		assertThat(db.countOf(schemaName + ".order_item")).as("order_item: " + reason).isEqualTo(0);
	}

	/**
	 * G9-S06a — cascade REMOVE theo chuỗi
	 *
	 * <p>Mapping: {@code Customer.orders} và {@code PurchaseOrder.items} đều INVERSE + {@code cascade = REMOVE}.
	 * <p><b>Câu hỏi</b>: {@code em.remove(customer)} tốn bao nhiêu SQL và theo thứ tự nào?
	 * <p><b>Kết luận</b>: Hibernate phải TẢI hết (1 customer + 1 orders + 2 items) rồi DELETE TỪNG dòng: 4 item, 2 order, 1 customer, con trước cha.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S06a | cascade REMOVE: tải toàn bộ rồi DELETE từng dòng, con trước cha")
	void s06a_cascadeRemoveLoadsEverythingThenDeletesRowByRow() {
		// ── GIVEN ──
		Long customerId = seedA();

		// ── WHEN ──
		sql.reset();
		deletionService.deleteByCascadeRemove(customerId);

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes(A + ".order_item", 4);
		sql.assertDeletes(A + ".purchase_order", 2);
		sql.assertDeletes(A + ".customer", 1);
		sql.assertInOrder("delete from " + A + ".order_item", "delete from " + A + ".purchase_order", "delete from " + A + ".customer");
		sql.assertSelects(A + ".purchase_order", 1); // nạp danh sách order của customer để biết xoá những order nào
		sql.assertSelects(A + ".order_item", 2); // nạp items của TỪNG order (2 order → 2 SELECT)

		// ── THEN: DB thật ──
		assertAllTablesEmpty(A, "cascade REMOVE đã xoá toàn bộ chuỗi");
	}

	/**
	 * G9-S06b — {@code @OnDelete(CASCADE)}
	 *
	 * <p>Mapping: {@code @OnDelete(action = CASCADE)} trên {@code PurchaseOrder.customer} và {@code OrderItem.order} (FK có {@code ON DELETE CASCADE});
	 * KHÔNG có cascade JPA.
	 * <p><b>Câu hỏi</b>: {@code em.remove(customer)} tốn bao nhiêu SQL?
	 * <p><b>Kết luận</b>: 1 SELECT (tải customer) + 1 DELETE customer; PostgreSQL tự xoá order và item. Ít SQL nhất nhưng Hibernate không biết con đã biến mất (S06e).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S06b | @OnDelete(CASCADE): chỉ 1 DELETE customer, DB tự xoá order và item")
	void s06b_onDeleteLetsDatabaseCascadeWithSingleDelete() {
		// ── GIVEN ──
		Long customerId = seedB();

		// ── WHEN ──
		sql.reset();
		deletionService.deleteByOnDelete(customerId);

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes(B + ".customer", 1);
		sql.assertDeletes(B + ".purchase_order", 0); // không có DELETE order nào do Hibernate gửi: do PostgreSQL tự xoá
		sql.assertDeletes(B + ".order_item", 0);
		sql.assertTotal(2); // select customer + delete customer

		// ── THEN: DB thật ──
		assertAllTablesEmpty(B, "FK ON DELETE CASCADE khiến PostgreSQL xoá dây chuyền order rồi item");
	}

	/**
	 * G9-S06c — bulk delete từ dưới lên trong service
	 *
	 * <p>Mapping: baseline trần (không cascade, không @OnDelete); service tự viết 3 câu DELETE JPQL: item → order → customer.
	 * <p><b>Câu hỏi</b>: có xoá được mà không cần cascade/@OnDelete không, tốn bao nhiêu SQL?
	 * <p><b>Kết luận</b>: được: đúng 3 câu DELETE, 0 SELECT. Phải đi từ dưới lên vì FK. Đổi lại bỏ qua persistence context và mọi callback của entity.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S06c | Bulk delete từ dưới lên: 3 câu DELETE, 0 SELECT")
	void s06c_bulkDeleteBottomUpUsesThreeStatementsAndNoSelect() {
		// ── GIVEN ──
		Long customerId = seedC();

		// ── WHEN ──
		sql.reset();
		deletionService.deleteByBulkBottomUp(customerId);

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes(C + ".order_item", 1);
		sql.assertDeletes(C + ".purchase_order", 1);
		sql.assertDeletes(C + ".customer", 1);
		sql.assertInOrder("delete from " + C + ".order_item", "delete from " + C + ".purchase_order", "delete from " + C + ".customer");
		sql.assertTotal(3);

		// ── THEN: DB thật ──
		assertAllTablesEmpty(C, "bulk delete từ dưới lên không vi phạm FK");
	}

	/**
	 * G9-S06d — không chiến lược nào
	 *
	 * <p>Mapping: baseline trần của biến thể c.
	 * <p><b>Câu hỏi</b>: {@code em.remove(customer)} khi còn order thì sao?
	 * <p><b>Kết luận</b>: PostgreSQL từ chối DELETE customer (còn order trỏ tới) → {@code DataIntegrityViolationException}, transaction rollback, dữ liệu nguyên vẹn.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S06d | Không chiến lược nào: DELETE customer vi phạm FK, rollback")
	void s06d_noStrategyViolatesForeignKeyAndRollsBack() {
		// ── GIVEN ──
		Long customerId = seedC();

		// ── WHEN + THEN ──
		assertThatThrownBy(() -> deletionService.deleteWithoutStrategy(customerId))
			.as("order.customer_id còn trỏ tới customer nên PostgreSQL chặn DELETE")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C + ".customer")).as("rollback: customer còn nguyên").isEqualTo(1);
		assertThat(db.countOf(C + ".purchase_order")).as("rollback: 2 order còn nguyên").isEqualTo(2);
		assertThat(db.countOf(C + ".order_item")).as("rollback: 4 item còn nguyên").isEqualTo(4);
	}

	/**
	 * G9-S06e — Trạng thái persistence context sau khi xoá: (a) REMOVED cả chuỗi, (b) con thành "ma" (vẫn MANAGED)
	 *
	 * <p><b>Câu hỏi</b>: sau {@code em.remove(customer)}, order đã tải ở trạng thái nào trong hai chiến lược?
	 * <p><b>Kết luận</b>: (a) cascade REMOVE đánh dấu REMOVED cả order. (b) {@code @OnDelete} chỉ có DB biết: order đã tải vẫn MANAGED
	 * dù dòng của nó sắp bị DB xoá. Dùng tiếp entity "ma" này (ví dụ gán lại làm cha) sẽ gây lỗi khó hiểu.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S06e | Trạng thái PC sau khi xoá: cascade REMOVE → order REMOVED; @OnDelete → order vẫn MANAGED (ma)")
	void s06e_persistenceContextStateAfterRemoveDiffersBetweenCascadeAndOnDelete() {
		// ── GIVEN ──
		Long customerIdA = seedA();
		Long customerIdB = seedB();

		// ── WHEN + THEN: (a) cascade REMOVE ──
		inTx(() -> {
			Customer customerA = em.find(Customer.class, customerIdA);
			em.remove(customerA);
			// [LIFECYCLE] cascade REMOVE: customer VÀ order (đã được cascade tải ra) đều MANAGED → REMOVED
			assertState(em, customerA, REMOVED);
			assertState(em, customerA.getOrders().get(0), REMOVED);
		});

		// ── WHEN + THEN: (b) @OnDelete ──
		inTx(() -> {
			com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.Customer customerB = em.find(
				com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.Customer.class, customerIdB);
			com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.PurchaseOrder orderOfB = customerB.getOrders().get(0); // đã tải
			em.remove(customerB);
			// [LIFECYCLE] không có cascade JPA: chỉ customer REMOVED; order đã tải vẫn MANAGED dù DB sắp xoá dòng của nó
			assertState(em, customerB, REMOVED);
			assertState(em, orderOfB, MANAGED);
		});

		// ── THEN: DB thật ──
		assertAllTablesEmpty(A, "(a) xoá hết chuỗi");
		assertAllTablesEmpty(B, "(b) PostgreSQL xoá dây chuyền lúc commit");
	}
}
