package com.example.hibernatelab.g2_cascade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g2_cascade.c0_none.C0OrderItem;
import com.example.hibernatelab.g2_cascade.c0_none.C0Product;
import com.example.hibernatelab.g2_cascade.c0_none.C0PurchaseOrder;
import com.example.hibernatelab.g2_cascade.c1_persist.C1OrderItem;
import com.example.hibernatelab.g2_cascade.c1_persist.C1Product;
import com.example.hibernatelab.g2_cascade.c1_persist.C1PurchaseOrder;
import com.example.hibernatelab.g2_cascade.c2_merge.C2OrderItem;
import com.example.hibernatelab.g2_cascade.c2_merge.C2Product;
import com.example.hibernatelab.g2_cascade.c2_merge.C2PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G2, thao tác MERGE: gửi một đồ thị DETACHED (order + item đã sửa + item mới) vào {@code merge(order)}.
 * S04a (C0 không cascade), S04b (C2 {@code cascade = MERGE}), S04c (C1 chỉ {@code PERSIST}, kết quả 🔬 ghi ở RESULTS.md).
 *
 * <p>Cả 3 test cùng kịch bản: GIVEN order có 2 item (số lượng 1 và 2). tx1 tải order + item bằng {@code JOIN FETCH}
 * (đóng tx → DETACHED), sửa số lượng item đầu thành 99 và thêm 1 item mới, tx2 gọi {@code merge(order)}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G2CascadeMergeTest extends AbstractScenarioTest {

	private static final String C0 = "g2_c0_none";
	private static final String C1 = "g2_c1_persist";
	private static final String C2 = "g2_c2_merge";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { C0, C1, C2 };
	}

	/**
	 * G2-S04a — C0: merge đồ thị DETACHED khi KHÔNG cascade
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order")} không cascade.
	 * <p><b>Câu hỏi</b>: item bị sửa và item mới nằm trong order DETACHED, {@code merge(order)} có lưu chúng không?
	 * <p><b>Kết luận</b>: thay đổi của item bị MẤT hoàn toàn: 0 UPDATE, 0 INSERT, không lỗi. Hibernate chỉ SELECT lại order
	 * và các item theo id (để giải quyết tham chiếu) rồi không copy state của item, nên số lượng 99 không bao giờ tới DB;
	 * item mới (7) cũng không được INSERT. Merge không lan nếu không có {@code cascade = MERGE}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S04a | C0 không cascade: merge(order) làm mất thay đổi của item")
	void s04a_withoutCascadeMergeIgnoresItemChanges() {
		// ── GIVEN: order A-001 có 2 item (số lượng 1 và 2) trong DB ──
		C0Product productPen = new C0Product("Bút bi");
		Long orderId = inTxReturning(() -> {
			C0PurchaseOrder orderA = new C0PurchaseOrder("A-001");
			orderA.addItem(new C0OrderItem(productPen, 1));
			orderA.addItem(new C0OrderItem(productPen, 2));
			em.persist(productPen);
			em.persist(orderA);
			em.persist(orderA.getItems().get(0));
			em.persist(orderA.getItems().get(1));
			return orderA.getId();
		});
		// tx1: tải cả đồ thị rồi đóng tx → order và 2 item đều DETACHED
		C0PurchaseOrder detachedOrder = inTxReturning(() -> em.createQuery(
			"select o from G2C0_PurchaseOrder o join fetch o.items where o.id = :id", C0PurchaseOrder.class)
			.setParameter("id", orderId).getSingleResult());
		detachedOrder.getItems().get(0).setQuantity(99);
		detachedOrder.addItem(new C0OrderItem(productPen, 7));

		// ── WHEN: tx2 merge cả đồ thị ──
		sql.reset();
		inTx(() -> em.merge(detachedOrder));
		// [TX END] không cascade MERGE thì Hibernate chỉ xử lý order, không đụng tới state của item

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("purchase_order", 0);
		sql.assertUpdates("order_item", 0);
		sql.assertInserts("order_item", 0);

		// ── THEN: DB thật ──
		db.print(C0 + ".order_item");
		assertThat(db.countOf(C0 + ".order_item"))
			.as("không cascade MERGE: item mới (7) không được INSERT, vẫn chỉ 2 item cũ")
			.isEqualTo(2);
		assertThat(db.rowsOf(C0 + ".order_item"))
			.extracting(row -> row.get("quantity"))
			.as("không cascade MERGE: số lượng 99 của item bị MẤT, DB giữ nguyên 1 và 2")
			.containsExactlyInAnyOrder(1, 2);
	}

	/**
	 * G2-S04b — C2: {@code cascade = MERGE} đưa thay đổi của item vào DB
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order", cascade = MERGE)}.
	 * <p><b>Câu hỏi</b>: cùng kịch bản S04a nhưng có cascade MERGE thì sao?
	 * <p><b>Kết luận</b>: merge lan xuống từng item: item cũ được copy state (UPDATE số lượng 99), item mới (TRANSIENT)
	 * được INSERT. Order không đổi gì nên không có UPDATE purchase_order.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S04b | C2 cascade MERGE: merge(order) UPDATE item cũ và INSERT item mới")
	void s04b_cascadeMergeAppliesItemChanges() {
		// ── GIVEN: order A-001 có 2 item (số lượng 1 và 2) trong DB ──
		C2Product productPen = new C2Product("Bút bi");
		Long orderId = inTxReturning(() -> {
			C2PurchaseOrder orderA = new C2PurchaseOrder("A-001");
			orderA.addItem(new C2OrderItem(productPen, 1));
			orderA.addItem(new C2OrderItem(productPen, 2));
			em.persist(productPen);
			em.persist(orderA);
			em.persist(orderA.getItems().get(0));
			em.persist(orderA.getItems().get(1));
			return orderA.getId();
		});
		C2PurchaseOrder detachedOrder = inTxReturning(() -> em.createQuery(
			"select o from G2C2_PurchaseOrder o join fetch o.items where o.id = :id", C2PurchaseOrder.class)
			.setParameter("id", orderId).getSingleResult());
		detachedOrder.getItems().get(0).setQuantity(99);
		detachedOrder.addItem(new C2OrderItem(productPen, 7));

		// ── WHEN: tx2 merge cả đồ thị ──
		sql.reset();
		inTx(() -> em.merge(detachedOrder));
		// [FLUSH] lúc commit: UPDATE item đã sửa, INSERT item mới

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);
		sql.assertInserts("order_item", 1);
		sql.assertUpdates("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(C2 + ".order_item");
		assertThat(db.rowsOf(C2 + ".order_item"))
			.extracting(row -> row.get("quantity"))
			.as("cascade MERGE: 99 (item cũ đã sửa), 2 (không đổi), 7 (item mới)")
			.containsExactlyInAnyOrder(99, 2, 7);
	}

	/**
	 * G2-S04c — C1 🔬: chỉ cascade PERSIST (không MERGE) mà vẫn gọi merge
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order", cascade = PERSIST)}.
	 * <p><b>Câu hỏi</b>: PERSIST có "giúp" merge không?
	 * <p><b>Kết luận</b>: KHÔNG, và còn tệ hơn không có gì: item cũ không được cập nhật (99 mất, 0 UPDATE), còn item MỚI
	 * sinh ra một dòng "MA" ({@code quantity = 0}, {@code order_id = NULL}, {@code product_id = NULL}). Lý do: không có
	 * {@code MERGE} nên merge không copy state của item mới mà chỉ tạo một bản sao RỖNG gắn vào collection của order MANAGED;
	 * lúc flush cascade PERSIST (persist-on-flush, như S03) lưu đúng bản rỗng đó. Muốn merge cả đồ thị thì PHẢI có MERGE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S04c | C1 chỉ cascade PERSIST: merge không cập nhật item cũ và INSERT một item MA rỗng")
	void s04c_persistCascadeDoesNotHelpMerge() {
		// ── GIVEN: order A-001 có 2 item (số lượng 1 và 2) trong DB ──
		C1Product productPen = new C1Product("Bút bi");
		Long orderId = inTxReturning(() -> {
			C1PurchaseOrder orderA = new C1PurchaseOrder("A-001");
			orderA.addItem(new C1OrderItem(productPen, 1));
			orderA.addItem(new C1OrderItem(productPen, 2));
			em.persist(productPen);
			em.persist(orderA); // cascade PERSIST lưu luôn 2 item
			return orderA.getId();
		});
		C1PurchaseOrder detachedOrder = inTxReturning(() -> em.createQuery(
			"select o from G2C1_PurchaseOrder o join fetch o.items where o.id = :id", C1PurchaseOrder.class)
			.setParameter("id", orderId).getSingleResult());
		detachedOrder.getItems().get(0).setQuantity(99);
		detachedOrder.addItem(new C1OrderItem(productPen, 7));

		// ── WHEN: tx2 merge cả đồ thị ──
		sql.reset();
		inTx(() -> em.merge(detachedOrder));

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 0);
		sql.assertInserts("order_item", 1);

		// ── THEN: DB thật ──
		db.print(C1 + ".order_item");
		assertThat(db.rowsOf(C1 + ".order_item"))
			.extracting(row -> row.get("quantity"))
			.as("1 và 2: item cũ KHÔNG được cập nhật (99 mất) vì không có cascade MERGE; "
				+ "0: dòng mới là bản sao RỖNG của item 7 (state không được copy), không phải 7")
			.containsExactlyInAnyOrder(1, 2, 0);
		assertThat(db.rowsOf(C1 + ".order_item"))
			.extracting(row -> row.get("order_id"))
			.as("dòng ma không có FK order_id (NULL) nên không thuộc order nào: dữ liệu rác lặng lẽ sinh ra")
			.containsExactlyInAnyOrder(orderId, orderId, null);
	}
}
