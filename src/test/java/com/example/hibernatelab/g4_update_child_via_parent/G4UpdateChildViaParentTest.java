package com.example.hibernatelab.g4_update_child_via_parent;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0Customer;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0OrderItem;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0OrderItemRepository;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0Product;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0PurchaseOrder;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0PurchaseOrderRepository;
import com.example.hibernatelab.g4_update_child_via_parent.u1_no_merge.U1Customer;
import com.example.hibernatelab.g4_update_child_via_parent.u1_no_merge.U1OrderItem;
import com.example.hibernatelab.g4_update_child_via_parent.u1_no_merge.U1Product;
import com.example.hibernatelab.g4_update_child_via_parent.u1_no_merge.U1PurchaseOrder;
import com.example.hibernatelab.g4_update_child_via_parent.u1_no_merge.U1PurchaseOrderRepository;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G4, sửa CON thông qua CHA: S01–S03 (sửa field của item), S12 (đi qua cha vs đi thẳng), S13 (bulk JPQL).
 *
 * <p>Câu hỏi xuyên suốt: sửa {@code item.quantity} thì dòng {@code purchase_order} có bị đụng tới không? Cách chứng minh:
 * chụp snapshot dòng cha bằng JDBC (mọi cột, gồm {@code version} và {@code updated_at}) trước và sau, rồi so bằng nhau,
 * cộng thêm {@code sql.assertUpdates("purchase_order", 0)}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G4UpdateChildViaParentTest extends AbstractScenarioTest {

	private static final String U0 = "g4_u0_baseline";
	private static final String U1 = "g4_u1_no_merge";

	@Autowired
	private U0PurchaseOrderRepository u0OrderRepository;

	@Autowired
	private U0OrderItemRepository u0ItemRepository;

	@Autowired
	private U1PurchaseOrderRepository u1OrderRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { U0, U1 };
	}

	// ── dữ liệu mẫu dùng chung (GIVEN): order A-001 có 2 item: PEN × 1 và BOOK × 2 ──

	/** U0: persist customer, 2 product rồi order (cascade ALL kéo theo 2 item). Trả về id order. */
	private Long seedU0OrderWithPenAndBook() {
		U0Customer customerAn = new U0Customer("An");
		U0Product productPen = new U0Product("PEN");
		U0Product productBook = new U0Product("BOOK");
		U0PurchaseOrder orderA = new U0PurchaseOrder("A-001", "NEW");
		orderA.setCustomer(customerAn);
		orderA.addItem(new U0OrderItem(productPen, 1, new BigDecimal("10.00")));
		orderA.addItem(new U0OrderItem(productBook, 2, new BigDecimal("20.00")));
		return inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(productPen);
			em.persist(productBook);
			em.persist(orderA);
			return orderA.getId();
		});
	}

	/** U1: giống U0 nhưng items chỉ cascade PERSIST. Trả về id order. */
	private Long seedU1OrderWithPenAndBook() {
		U1Customer customerAn = new U1Customer("An");
		U1Product productPen = new U1Product("PEN");
		U1Product productBook = new U1Product("BOOK");
		U1PurchaseOrder orderA = new U1PurchaseOrder("A-001", "NEW");
		orderA.setCustomer(customerAn);
		orderA.addItem(new U1OrderItem(productPen, 1, new BigDecimal("10.00")));
		orderA.addItem(new U1OrderItem(productBook, 2, new BigDecimal("20.00")));
		return inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(productPen);
			em.persist(productBook);
			em.persist(orderA);
			return orderA.getId();
		});
	}

	/** Số lượng của các item trong DB (sắp theo id), đọc bằng JDBC. */
	private List<Integer> quantitiesInDb(String schemaName) {
		List<Integer> quantities = new ArrayList<>();
		for (Map<String, Object> row : db.rowsOf(schemaName + ".order_item")) {
			quantities.add(((Number) row.get("quantity")).intValue());
		}
		return quantities;
	}

	/** Id của item có mã sản phẩm cho trước (JOIN bằng JDBC, không qua Hibernate). */
	private Long itemIdOfProduct(String schemaName, String productName) {
		List<Map<String, Object>> products = db.rowsOf(schemaName + ".product");
		for (Map<String, Object> product : products) {
			if (productName.equals(product.get("name"))) {
				for (Map<String, Object> item : db.rowsOf(schemaName + ".order_item")) {
					if (product.get("id").equals(item.get("product_id"))) {
						return (Long) item.get("id");
					}
				}
			}
		}
		throw new AssertionError("Không có item nào của sản phẩm " + productName);
	}

	/**
	 * G4-S01 — U0: sửa field của con qua cha đang MANAGED
	 *
	 * <p>Mapping: {@code PurchaseOrder.items} là INVERSE side (mappedBy), cascade ALL, orphanRemoval; {@code PurchaseOrder} có {@code @Version}.
	 * <p><b>Câu hỏi</b>: {@code order.getItems().get(0).setQuantity(5)} có làm dòng order bị UPDATE không?
	 * <p><b>Kết luận</b>: không. Chỉ có 1 {@code UPDATE order_item}. Dirty checking chạy RIÊNG cho từng entity: order không có field
	 * nào đổi (collection inverse không phải state của order) nên không UPDATE, {@code version} và {@code updated_at} giữ nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S01 | U0: sửa quantity của item qua cha chỉ UPDATE order_item, dòng cha giữ nguyên")
	void s01_changingChildThroughManagedParentDoesNotTouchParentRow() {
		// ── GIVEN: order A-001 có 2 item, chụp snapshot dòng cha ──
		Long orderId = seedU0OrderWithPenAndBook();
		Map<String, Object> orderRowBefore = db.rowSnapshot(U0 + ".purchase_order", orderId);

		// ── WHEN: tải order, đi qua items, sửa quantity của item đầu ──
		sql.reset();
		inTx(() -> {
			U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
			U0OrderItem firstItem = orderA.getItems().get(0);
			firstItem.setQuantity(5);
			// [LIFECYCLE] cả hai đều MANAGED; chỉ item "dirty" (state khác snapshot lúc tải), order thì không
			assertState(em, orderA, MANAGED);
			assertState(em, firstItem, MANAGED);
			// [FLUSH] lúc commit: dirty checking từng entity → chỉ item có UPDATE
			// [TX END] commit
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);
		sql.assertUpdates("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(U0 + ".order_item");
		assertThat(quantitiesInDb(U0)).as("đúng một item đổi thành 5, item kia giữ nguyên").containsExactlyInAnyOrder(5, 2);
		assertThat(db.rowSnapshot(U0 + ".purchase_order", orderId))
			.as("dòng cha (kể cả version và updated_at) phải giống hệt trước khi sửa con")
			.isEqualTo(orderRowBefore);
	}

	/**
	 * G4-S02 — U0: đồ thị DETACHED → {@code orderRepository.save(order)}
	 *
	 * <p>Mapping: như S01 (cascade ALL, có MERGE).
	 * <p><b>Câu hỏi</b>: sửa item trên order DETACHED rồi {@code save(order)} thì cha có bị UPDATE không?
	 * <p><b>Kết luận</b>: {@code save()} của Spring Data gọi {@code merge()} (vì order đã có id). Merge SELECT lại order và items,
	 * copy state từ bản DETACHED sang bản MANAGED; chỉ item có state khác nên chỉ có 1 {@code UPDATE order_item}. Cha giữ nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S02 | U0: save(order DETACHED) với item đã sửa → merge, chỉ UPDATE order_item")
	void s02_savingDetachedGraphUpdatesOnlyTheChangedChild() {
		// ── GIVEN ──
		Long orderId = seedU0OrderWithPenAndBook();
		Map<String, Object> orderRowBefore = db.rowSnapshot(U0 + ".purchase_order", orderId);

		// tx1: tải cả đồ thị rồi đóng tx → order và item DETACHED; sửa quantity ngoài transaction
		U0PurchaseOrder detachedOrder = inTxReturning(() -> {
			U0PurchaseOrder loadedOrder = em.find(U0PurchaseOrder.class, orderId);
			loadedOrder.getItems().size(); // khởi tạo collection LAZY trước khi đóng tx
			return loadedOrder;
		});
		assertState(em, detachedOrder, DETACHED);
		detachedOrder.getItems().get(0).setQuantity(5);

		// ── WHEN: tx2 save(order) = merge ──
		sql.reset();
		inTx(() -> u0OrderRepository.save(detachedOrder));

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("order_item", 1);
		sql.assertUpdates("order_item", 1);
		sql.assertUpdates("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(U0 + ".order_item");
		assertThat(quantitiesInDb(U0)).as("cascade MERGE kéo thay đổi của item xuống DB").containsExactlyInAnyOrder(5, 2);
		assertThat(db.rowSnapshot(U0 + ".purchase_order", orderId))
			.as("order không có field nào đổi nên merge không UPDATE cha, version giữ nguyên")
			.isEqualTo(orderRowBefore);
	}

	/**
	 * G4-S03 — U1: như S02 nhưng items KHÔNG cascade MERGE (chỉ PERSIST)
	 *
	 * <p>Mapping: {@code PurchaseOrder.items}: {@code cascade = PERSIST}, orphanRemoval = true.
	 * <p><b>Câu hỏi</b>: {@code save(order)} có lưu thay đổi của item không?
	 * <p><b>Kết luận</b>: không. Merge không lan xuống item nên thay đổi của item bị MẤT, im lặng, không lỗi.
	 * Bài học: "save cha thì con tự lưu" chỉ đúng khi cascade có MERGE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S03 | U1: save(order DETACHED) không cascade MERGE → thay đổi của item bị mất")
	void s03_withoutCascadeMergeChildChangeIsSilentlyLost() {
		// ── GIVEN ──
		Long orderId = seedU1OrderWithPenAndBook();
		Map<String, Object> orderRowBefore = db.rowSnapshot(U1 + ".purchase_order", orderId);
		U1PurchaseOrder detachedOrder = inTxReturning(() -> {
			U1PurchaseOrder loadedOrder = em.find(U1PurchaseOrder.class, orderId);
			loadedOrder.getItems().size();
			return loadedOrder;
		});
		detachedOrder.getItems().get(0).setQuantity(5);

		// ── WHEN ──
		sql.reset();
		inTx(() -> u1OrderRepository.save(detachedOrder));

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 0);
		sql.assertUpdates("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(U1 + ".order_item");
		assertThat(quantitiesInDb(U1)).as("thiếu cascade MERGE nên thay đổi của item không được copy → DB giữ 1 và 2").containsExactly(1, 2);
		assertThat(db.rowSnapshot(U1 + ".purchase_order", orderId)).as("cha cũng không đổi").isEqualTo(orderRowBefore);
	}

	/**
	 * G4-S12 — U0: sửa con qua cha vs qua {@code OrderItemRepository}
	 *
	 * <p>Mapping: như S01.
	 * <p><b>Câu hỏi</b>: cùng sửa một item, đi qua cha và đi thẳng khác nhau ở đâu?
	 * <p><b>Kết luận</b>: kết quả trong DB như nhau (1 UPDATE order_item, cha giữ nguyên) nhưng CHI PHÍ khác: đi qua cha phải SELECT
	 * order rồi SELECT TOÀN BỘ items; đi thẳng chỉ SELECT đúng 1 item (order và product là LAZY nên không bị kéo theo).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S12 | U0: sửa item qua cha SELECT cả order + mọi item, đi thẳng qua repository chỉ SELECT 1 item")
	void s12_viaParentLoadsAllItemsWhileDirectLoadsOne() {
		// ── GIVEN ──
		Long orderId = seedU0OrderWithPenAndBook();
		Long penItemId = itemIdOfProduct(U0, "PEN");
		Long bookItemId = itemIdOfProduct(U0, "BOOK");

		// ── WHEN (cách 1): đi qua cha để sửa item PEN ──
		sql.reset();
		inTx(() -> {
			U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
			for (U0OrderItem item : orderA.getItems()) {
				if (item.getId().equals(penItemId)) {
					item.setQuantity(5);
				}
			}
		});

		// ── THEN (cách 1): SQL ──
		sql.print();
		sql.assertSelects("purchase_order", 1);
		sql.assertSelects("order_item", 1); // một câu SELECT lấy TOÀN BỘ item của order (cả PEN lẫn BOOK)
		sql.assertUpdates("order_item", 1);

		// ── WHEN (cách 2): đi thẳng qua repository để sửa item BOOK ──
		sql.reset();
		inTx(() -> {
			U0OrderItem bookItem = u0ItemRepository.findById(bookItemId).orElseThrow();
			bookItem.setQuantity(7);
		});

		// ── THEN (cách 2): SQL ──
		sql.print();
		sql.assertSelects("order_item", 1);
		sql.assertSelects("purchase_order", 0); // order là LAZY, không ai chạm vào nên không bị tải
		sql.assertUpdates("order_item", 1);

		// ── THEN: DB thật ──
		db.print(U0 + ".order_item");
		assertThat(quantitiesInDb(U0)).as("cả hai cách đều ghi đúng vào DB").containsExactlyInAnyOrder(5, 7);
	}

	/**
	 * G4-S13a — U0: bulk JPQL {@code update OrderItem set quantity = …} KHÔNG làm mới persistence context
	 *
	 * <p>Mapping: như S01.
	 * <p><b>Câu hỏi</b>: item đang MANAGED, chạy bulk UPDATE; object trong bộ nhớ có thấy giá trị mới không?
	 * <p><b>Kết luận</b>: không. DB đã đổi nhưng object vẫn giữ giá trị cũ (STALE), vì bulk JPQL đi thẳng vào DB, bỏ qua persistence context.
	 * (Bản có {@code @Version} riêng của item là U5; bulk update cũng không tự tăng version nếu không viết {@code versioned}.)
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S13a | U0: bulk JPQL đổi DB nhưng item đang MANAGED vẫn giữ giá trị cũ (stale)")
	void s13a_bulkUpdateLeavesManagedItemStale() {
		// ── GIVEN ──
		seedU0OrderWithPenAndBook();
		Long penItemId = itemIdOfProduct(U0, "PEN");

		// ── WHEN: trong cùng một tx, tải item rồi chạy bulk update ──
		sql.reset();
		inTx(() -> {
			U0OrderItem penItem = em.find(U0OrderItem.class, penItemId);
			int updatedRows = u0ItemRepository.bulkSetQuantity(penItemId, 99);
			// [LIFECYCLE] item vẫn MANAGED, Hibernate không biết dòng của nó đã bị câu bulk UPDATE đổi
			assertState(em, penItem, MANAGED);
			assertThat(updatedRows).as("bulk update báo đã đổi 1 dòng").isEqualTo(1);
			assertThat(penItem.getQuantity()).as("object trong persistence context vẫn là giá trị cũ → STALE").isEqualTo(1);
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);

		// ── THEN: DB thật ──
		db.print(U0 + ".order_item");
		assertThat(quantitiesInDb(U0)).as("DB đã có giá trị mới dù object trong bộ nhớ chưa biết").containsExactlyInAnyOrder(99, 2);
	}

	/**
	 * G4-S13b — U0: bulk JPQL với {@code @Modifying(clearAutomatically = true)}
	 *
	 * <p>Mapping: như S01.
	 * <p><b>Câu hỏi</b>: {@code clearAutomatically = true} thay đổi gì?
	 * <p><b>Kết luận</b>: sau câu UPDATE, persistence context bị {@code clear()}: item cũ thành DETACHED (vẫn giữ giá trị cũ trong object),
	 * còn {@code find} lần sau đọc lại DB và thấy giá trị mới.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S13b | U0: @Modifying(clearAutomatically = true) làm item DETACHED, find lại thấy giá trị mới")
	void s13b_bulkUpdateWithClearAutomaticallyRefreshesPersistenceContext() {
		// ── GIVEN ──
		seedU0OrderWithPenAndBook();
		Long penItemId = itemIdOfProduct(U0, "PEN");

		// ── WHEN ──
		inTx(() -> {
			U0OrderItem staleItem = em.find(U0OrderItem.class, penItemId);
			u0ItemRepository.bulkSetQuantityAndClear(penItemId, 99);
			// [LIFECYCLE] clearAutomatically → em.clear(): item bị tách khỏi persistence context
			assertState(em, staleItem, DETACHED);
			U0OrderItem reloadedItem = em.find(U0OrderItem.class, penItemId);
			assertThat(reloadedItem.getQuantity()).as("find sau clear phải SELECT lại từ DB nên thấy 99").isEqualTo(99);
			assertThat(staleItem.getQuantity()).as("bản DETACHED cũ vẫn giữ giá trị cũ").isEqualTo(1);
		});

		// ── THEN: DB thật ──
		assertThat(quantitiesInDb(U0)).as("DB có giá trị mới").containsExactlyInAnyOrder(99, 2);
	}
}
