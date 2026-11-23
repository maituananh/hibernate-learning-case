package com.example.hibernatelab.g9_integrated;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g9_integrated.s11_soft_delete.OrderItem;
import com.example.hibernatelab.g9_integrated.s11_soft_delete.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G9-S11 ➕🔬 {@code @SoftDelete} (Hibernate 6.4+) trên {@code OrderItem}: xoá = UPDATE cờ {@code deleted}.
 * Kết quả được ghi theo hành vi QUAN SÁT được (xem docs/RESULTS.md).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G9S11SoftDeleteTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g9_s11_soft_delete";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/** Lưu order A-001 có 2 item PEN-01, PEN-02; trả về {orderId, itemPen01Id}. */
	private Long[] seedOrderWithTwoItems() {
		PurchaseOrder orderA = new PurchaseOrder("A-001");
		OrderItem itemPen01 = new OrderItem("PEN-01");
		orderA.addItem(itemPen01);
		orderA.addItem(new OrderItem("PEN-02"));
		return inTxReturning(() -> {
			em.persist(orderA);
			return new Long[] { orderA.getId(), itemPen01.getId() };
		});
	}

	/**
	 * G9-S11a — {@code em.remove(item)} sinh UPDATE cờ deleted, dòng vẫn còn trong DB
	 *
	 * <p>Mapping: {@code @SoftDelete(columnName = "deleted")} trên {@code OrderItem}.
	 * <p><b>Câu hỏi</b>: remove một item thì SQL gì chạy, dòng còn không?
	 * <p><b>Kết luận</b> 🔬: 1 UPDATE đặt {@code deleted = true}, 0 DELETE; dòng vẫn nằm trong bảng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S11a | @SoftDelete: remove() là UPDATE cờ deleted, không DELETE")
	void s11a_removeBecomesUpdateOfDeletedFlag() {
		// ── GIVEN ──
		Long[] ids = seedOrderWithTwoItems();
		Long itemId = ids[1];

		// ── WHEN ──
		sql.reset();
		inTx(() -> em.remove(em.find(OrderItem.class, itemId)));

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes(SCHEMA + ".order_item", 0);
		sql.assertUpdates(SCHEMA + ".order_item", 1);

		// ── THEN: DB thật ──
		db.print(SCHEMA + ".order_item");
		assertThat(db.countOf(SCHEMA + ".order_item")).as("dòng vẫn còn trong bảng (xoá mềm)").isEqualTo(2);
		assertThat(db.rowById(SCHEMA + ".order_item", itemId).get("deleted")).as("cờ deleted được đặt").isEqualTo(true);
	}

	/**
	 * G9-S11b — Collection của cha tự lọc item đã xoá
	 *
	 * <p><b>Câu hỏi</b>: sau khi soft delete một item, {@code order.getItems()} còn thấy nó không?
	 * <p><b>Kết luận</b> 🔬: không: SELECT nạp collection tự thêm điều kiện loại dòng đã xoá.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S11b | Collection của cha tự lọc item đã soft delete")
	void s11b_parentCollectionFiltersSoftDeletedItems() {
		// ── GIVEN ──
		Long[] ids = seedOrderWithTwoItems();
		Long orderId = ids[0];
		Long itemId = ids[1];
		inTx(() -> em.remove(em.find(OrderItem.class, itemId)));

		// ── WHEN ──
		sql.reset();
		int visibleItems = inTxReturning(() -> em.find(PurchaseOrder.class, orderId).getItems().size());

		// ── THEN ──
		sql.print();
		sql.assertSomeStatementContains("deleted");
		assertThat(visibleItems).as("PEN-01 đã xoá mềm nên bị lọc khỏi collection; chỉ còn PEN-02").isEqualTo(1);
		assertThat(db.countOf(SCHEMA + ".order_item")).as("nhưng DB vẫn giữ cả 2 dòng").isEqualTo(2);
	}

	/**
	 * G9-S11c — orphanRemoval cũng thành soft delete
	 *
	 * <p><b>Câu hỏi</b>: gỡ item khỏi collection (orphanRemoval) có DELETE thật không?
	 * <p><b>Kết luận</b> 🔬: không: orphanRemoval gọi remove nội bộ nên cũng ra UPDATE cờ deleted.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S11c | orphanRemoval + @SoftDelete: gỡ khỏi collection chỉ UPDATE cờ deleted")
	void s11c_orphanRemovalBecomesSoftDelete() {
		// ── GIVEN ──
		Long[] ids = seedOrderWithTwoItems();
		Long orderId = ids[0];
		Long itemId = ids[1];

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			PurchaseOrder orderA = em.find(PurchaseOrder.class, orderId);
			OrderItem itemPen01 = em.find(OrderItem.class, itemId);
			orderA.removeItem(itemPen01); // thành orphan → bị "xoá" lúc flush
		});

		// ── THEN ──
		sql.print();
		sql.assertDeletes(SCHEMA + ".order_item", 0);
		assertThat(db.countOf(SCHEMA + ".order_item")).as("dòng vẫn còn: orphanRemoval cũng chỉ xoá mềm").isEqualTo(2);
		assertThat(db.rowById(SCHEMA + ".order_item", itemId).get("deleted")).as("cờ deleted được đặt").isEqualTo(true);
	}
}
