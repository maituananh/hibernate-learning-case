package com.example.hibernatelab.g2_cascade;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g2_cascade.c4_all.C4OrderItem;
import com.example.hibernatelab.g2_cascade.c4_all.C4Product;
import com.example.hibernatelab.g2_cascade.c4_all.C4PurchaseOrder;
import com.example.hibernatelab.g2_cascade.c5_persist_merge.C5OrderItem;
import com.example.hibernatelab.g2_cascade.c5_persist_merge.C5Product;
import com.example.hibernatelab.g2_cascade.c5_persist_merge.C5PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G2, thao tác DETACH và REFRESH: C4 ({@code ALL}, có DETACH và REFRESH) so với C5 ({@code {PERSIST, MERGE}}, thiếu cả hai).
 * S06 là {@code detach(order)}, S07 là {@code refresh(order)}.
 *
 * <p>Bài học chung: khi liệt kê cascade thủ công mà quên DETACH/REFRESH, thao tác trên order KHÔNG lan xuống item, và item
 * vẫn MANAGED (vẫn bị dirty checking) dù order đã DETACHED. Dễ gây bất ngờ vì mọi thứ trông "bình thường" cho tới lúc flush.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G2CascadeDetachRefreshTest extends AbstractScenarioTest {

	private static final String C4 = "g2_c4_all";
	private static final String C5 = "g2_c5_persist_merge";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { C4, C5 };
	}

	/**
	 * G2-S06a — C4: {@code detach(order)} kéo theo item DETACHED
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order", cascade = ALL)}.
	 * <p><b>Câu hỏi</b>: sau {@code em.detach(order)}, sửa {@code item.quantity} có được UPDATE không?
	 * <p><b>Kết luận</b>: Không. {@code ALL} gồm DETACH nên item cũng DETACHED, hết dirty checking, 0 UPDATE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S06a | C4 cascade ALL: detach(order) làm cả item DETACHED, sửa item không UPDATE")
	void s06a_cascadeAllDetachesItemsToo() {
		// ── GIVEN: order A-001 có 2 item (số lượng 1 và 2) trong DB ──
		C4Product productPen = new C4Product("Bút bi");
		C4PurchaseOrder orderA = new C4PurchaseOrder("A-001");
		orderA.addItem(new C4OrderItem(productPen, 1));
		orderA.addItem(new C4OrderItem(productPen, 2));
		Long orderId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN: tải order + item, detach order, rồi sửa số lượng của một item ──
		sql.reset();
		inTx(() -> {
			C4PurchaseOrder loadedOrder = em.createQuery(
				"select o from G2C4_PurchaseOrder o join fetch o.items where o.id = :id", C4PurchaseOrder.class)
				.setParameter("id", orderId).getSingleResult();
			C4OrderItem loadedItem = loadedOrder.getItems().get(0);
			em.detach(loadedOrder);
			// [LIFECYCLE] cascade DETACH: order VÀ item đều MANAGED → DETACHED
			assertState(em, loadedOrder, DETACHED);
			assertState(em, loadedItem, DETACHED);
			loadedItem.setQuantity(99); // item đã DETACHED nên thay đổi này không ai theo dõi
			// [FLUSH] lúc commit: không còn gì dirty trong persistence context
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 0);

		// ── THEN: DB thật ──
		db.print(C4 + ".order_item");
		assertThat(db.rowsOf(C4 + ".order_item"))
			.extracting(row -> row.get("quantity"))
			.as("item đã DETACHED nên số lượng 99 không bao giờ tới DB")
			.containsExactlyInAnyOrder(1, 2);
	}

	/**
	 * G2-S06b — C5: thiếu DETACH nên item vẫn MANAGED và vẫn bị UPDATE
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order", cascade = {PERSIST, MERGE})}.
	 * <p><b>Câu hỏi</b>: cùng thao tác S06a nhưng cascade không có DETACH thì sao?
	 * <p><b>Kết luận</b>: {@code detach(order)} KHÔNG lan xuống item: order DETACHED nhưng item vẫn MANAGED, nên thay đổi
	 * số lượng vẫn được flush thành UPDATE dù order đã detach. Đây là chỗ dễ bất ngờ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S06b | C5 thiếu DETACH: detach(order) nhưng item vẫn MANAGED và vẫn bị UPDATE")
	void s06b_withoutDetachCascadeItemsStayManagedAndAreStillUpdated() {
		// ── GIVEN: order A-001 có 2 item (số lượng 1 và 2) trong DB ──
		C5Product productPen = new C5Product("Bút bi");
		C5PurchaseOrder orderA = new C5PurchaseOrder("A-001");
		orderA.addItem(new C5OrderItem(productPen, 1));
		orderA.addItem(new C5OrderItem(productPen, 2));
		Long orderId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN: tải order + item, detach order, rồi sửa số lượng của một item ──
		sql.reset();
		inTx(() -> {
			C5PurchaseOrder loadedOrder = em.createQuery(
				"select o from G2C5_PurchaseOrder o join fetch o.items where o.id = :id", C5PurchaseOrder.class)
				.setParameter("id", orderId).getSingleResult();
			C5OrderItem loadedItem = loadedOrder.getItems().get(0);
			em.detach(loadedOrder);
			// [LIFECYCLE] DETACH không nằm trong cascade: order DETACHED nhưng item VẪN MANAGED
			assertState(em, loadedOrder, DETACHED);
			assertState(em, loadedItem, MANAGED);
			loadedItem.setQuantity(99); // item còn MANAGED nên dirty checking vẫn thấy thay đổi này
			// [FLUSH] lúc commit: UPDATE order_item
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);

		// ── THEN: DB thật ──
		db.print(C5 + ".order_item");
		assertThat(db.rowsOf(C5 + ".order_item"))
			.extracting(row -> row.get("quantity"))
			.as("item vẫn MANAGED nên số lượng 99 được ghi xuống DB, dù order cha đã detach")
			.contains(99);
	}

	/**
	 * G2-S07a — C4: {@code refresh(order)} nạp lại cả item, mất thay đổi chưa flush
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order", cascade = ALL)}.
	 * <p><b>Câu hỏi</b>: sửa {@code item.quantity} trong bộ nhớ rồi {@code em.refresh(order)}, số lượng đó còn không?
	 * <p><b>Kết luận</b>: Mất. {@code ALL} gồm REFRESH nên item cũng bị nạp lại từ DB, thay đổi chưa flush bị ghi đè, 0 UPDATE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S07a | C4 cascade ALL: refresh(order) nạp lại item, thay đổi chưa flush bị mất")
	void s07a_cascadeAllRefreshesItemsAndDiscardsInMemoryChanges() {
		// ── GIVEN: order A-001 có 2 item (số lượng 1 và 2) trong DB ──
		C4Product productPen = new C4Product("Bút bi");
		C4PurchaseOrder orderA = new C4PurchaseOrder("A-001");
		orderA.addItem(new C4OrderItem(productPen, 1));
		orderA.addItem(new C4OrderItem(productPen, 2));
		Long orderId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN: sửa số lượng trong bộ nhớ rồi refresh order ──
		sql.reset();
		inTx(() -> {
			C4PurchaseOrder loadedOrder = em.createQuery(
				"select o from G2C4_PurchaseOrder o join fetch o.items where o.id = :id", C4PurchaseOrder.class)
				.setParameter("id", orderId).getSingleResult();
			C4OrderItem loadedItem = loadedOrder.getItems().get(0);
			int quantityInDb = loadedItem.getQuantity();
			loadedItem.setQuantity(99);
			em.refresh(loadedOrder);
			// [LIFECYCLE] cascade REFRESH: item cũng được nạp lại từ DB nên vẫn MANAGED và số lượng trở về giá trị trong DB
			assertState(em, loadedItem, MANAGED);
			assertThat(loadedItem.getQuantity())
				.as("REFRESH lan xuống item nên số lượng 99 (chưa flush) bị ghi đè bằng giá trị trong DB")
				.isEqualTo(quantityInDb);
			// [FLUSH] lúc commit: item không còn dirty
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.rowsOf(C4 + ".order_item"))
			.extracting(row -> row.get("quantity"))
			.as("DB không bị đổi vì thay đổi 99 đã bị refresh huỷ")
			.containsExactlyInAnyOrder(1, 2);
	}

	/**
	 * G2-S07b — C5: thiếu REFRESH nên item giữ thay đổi và bị flush thành UPDATE
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order", cascade = {PERSIST, MERGE})}.
	 * <p><b>Câu hỏi</b>: cùng thao tác S07a nhưng cascade không có REFRESH thì sao?
	 * <p><b>Kết luận</b>: {@code refresh(order)} chỉ nạp lại order, KHÔNG lan xuống item: số lượng 99 vẫn còn trong bộ nhớ
	 * và lúc commit bị flush thành UPDATE. Người viết tưởng "refresh là huỷ mọi thay đổi" sẽ bị bất ngờ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S07b | C5 thiếu REFRESH: refresh(order) không đụng tới item, số lượng 99 vẫn bị UPDATE")
	void s07b_withoutRefreshCascadeItemsKeepInMemoryChangesAndAreUpdated() {
		// ── GIVEN: order A-001 có 2 item (số lượng 1 và 2) trong DB ──
		C5Product productPen = new C5Product("Bút bi");
		C5PurchaseOrder orderA = new C5PurchaseOrder("A-001");
		orderA.addItem(new C5OrderItem(productPen, 1));
		orderA.addItem(new C5OrderItem(productPen, 2));
		Long orderId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN: sửa số lượng trong bộ nhớ rồi refresh order ──
		sql.reset();
		inTx(() -> {
			C5PurchaseOrder loadedOrder = em.createQuery(
				"select o from G2C5_PurchaseOrder o join fetch o.items where o.id = :id", C5PurchaseOrder.class)
				.setParameter("id", orderId).getSingleResult();
			C5OrderItem loadedItem = loadedOrder.getItems().get(0);
			loadedItem.setQuantity(99);
			em.refresh(loadedOrder);
			// [LIFECYCLE] REFRESH không nằm trong cascade: item không bị nạp lại, vẫn MANAGED và giữ số lượng 99
			assertState(em, loadedItem, MANAGED);
			assertThat(loadedItem.getQuantity())
				.as("refresh không lan xuống item nên thay đổi 99 của item vẫn còn trong bộ nhớ")
				.isEqualTo(99);
			// [FLUSH] lúc commit: UPDATE order_item
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);

		// ── THEN: DB thật ──
		db.print(C5 + ".order_item");
		assertThat(db.rowsOf(C5 + ".order_item"))
			.extracting(row -> row.get("quantity"))
			.as("số lượng 99 sống sót qua refresh(order) và được ghi xuống DB")
			.contains(99);
	}
}
