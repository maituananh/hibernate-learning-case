package com.example.hibernatelab.g4_update_child_via_parent;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0Customer;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0OrderItem;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0Product;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0PurchaseOrder;
import com.example.hibernatelab.g4_update_child_via_parent.u2_owning_collection.U2Customer;
import com.example.hibernatelab.g4_update_child_via_parent.u2_owning_collection.U2OrderItem;
import com.example.hibernatelab.g4_update_child_via_parent.u2_owning_collection.U2Product;
import com.example.hibernatelab.g4_update_child_via_parent.u2_owning_collection.U2PurchaseOrder;
import com.example.hibernatelab.g4_update_child_via_parent.u3_optimistic_excluded.U3Customer;
import com.example.hibernatelab.g4_update_child_via_parent.u3_optimistic_excluded.U3OrderItem;
import com.example.hibernatelab.g4_update_child_via_parent.u3_optimistic_excluded.U3Product;
import com.example.hibernatelab.g4_update_child_via_parent.u3_optimistic_excluded.U3PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

import jakarta.persistence.LockModeType;

/**
 * Nhóm G4, câu hỏi "version của cha có bị tăng khi con đổi không?": S04 (thêm con), S05 (xoá con), S06 (U2, collection owning),
 * S07 (U3, {@code @OptimisticLock(excluded = true)}), S08 ({@code @UpdateTimestamp}/{@code @PreUpdate}), S09 (chủ động tăng version).
 *
 * <p>Điểm cần nắm: {@code @Version} của cha chỉ tăng khi Hibernate coi CHA "dirty". Collection INVERSE (mappedBy) không thuộc
 * state của cha nên đổi nó không làm cha dirty; collection OWNING (bảng nối) thì có.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G4ParentVersionTest extends AbstractScenarioTest {

	private static final String U0 = "g4_u0_baseline";
	private static final String U2 = "g4_u2_owning_collection";
	private static final String U3 = "g4_u3_optimistic_excluded";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { U0, U2, U3 };
	}

	// ── dữ liệu mẫu dùng chung (GIVEN): order A-001 có 1 item PEN × 1; product BOOK có sẵn để thêm item mới ──

	private Long seedU0OrderWithPen() {
		U0Customer customerAn = new U0Customer("An");
		U0Product productPen = new U0Product("PEN");
		U0Product productBook = new U0Product("BOOK");
		U0PurchaseOrder orderA = new U0PurchaseOrder("A-001", "NEW");
		orderA.setCustomer(customerAn);
		orderA.addItem(new U0OrderItem(productPen, 1, new BigDecimal("10.00")));
		return inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(productPen);
			em.persist(productBook);
			em.persist(orderA);
			return orderA.getId();
		});
	}

	private Long seedU2OrderWithPen() {
		U2Customer customerAn = new U2Customer("An");
		U2Product productPen = new U2Product("PEN");
		U2Product productBook = new U2Product("BOOK");
		U2PurchaseOrder orderA = new U2PurchaseOrder("A-001", "NEW");
		orderA.setCustomer(customerAn);
		orderA.addItem(new U2OrderItem(productPen, 1, new BigDecimal("10.00")));
		return inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(productPen);
			em.persist(productBook);
			em.persist(orderA);
			return orderA.getId();
		});
	}

	private Long seedU3OrderWithPen() {
		U3Customer customerAn = new U3Customer("An");
		U3Product productPen = new U3Product("PEN");
		U3Product productBook = new U3Product("BOOK");
		U3PurchaseOrder orderA = new U3PurchaseOrder("A-001", "NEW");
		orderA.setCustomer(customerAn);
		orderA.addItem(new U3OrderItem(productPen, 1, new BigDecimal("10.00")));
		return inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(productPen);
			em.persist(productBook);
			em.persist(orderA);
			return orderA.getId();
		});
	}

	/** Id của dòng đầu tiên (theo id) của bảng, đọc bằng JDBC. */
	private Long idOfFirstRow(String table) {
		return ((Number) db.rowsOf(table).get(0).get("id")).longValue();
	}

	/** Id của sản phẩm theo tên (JDBC). */
	private Long productIdByName(String schemaName, String productName) {
		for (Map<String, Object> product : db.rowsOf(schemaName + ".product")) {
			if (productName.equals(product.get("name"))) {
				return ((Number) product.get("id")).longValue();
			}
		}
		throw new AssertionError("Không có sản phẩm " + productName);
	}

	/**
	 * G4-S04 🔬 — U0: thêm con mới qua cha (collection mappedBy)
	 *
	 * <p>Mapping: {@code PurchaseOrder.items} là INVERSE side, cascade ALL, orphanRemoval; cha có {@code @Version}.
	 * <p><b>Câu hỏi</b>: {@code order.addItem(newItem)} có làm version của cha tăng không?
	 * <p><b>Kết luận</b>: không. Chỉ có INSERT order_item; cha không dirty vì collection inverse không thuộc state của cha.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S04 | U0: thêm item mới qua cha chỉ INSERT order_item, version của cha không tăng")
	void s04_addingChildToInverseCollectionDoesNotBumpParentVersion() {
		// ── GIVEN ──
		Long orderId = seedU0OrderWithPen();
		Long bookProductId = productIdByName(U0, "BOOK");
		Map<String, Object> orderRowBefore = db.rowSnapshot(U0 + ".purchase_order", orderId);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
			U0OrderItem itemBook = new U0OrderItem(em.find(U0Product.class, bookProductId), 3, new BigDecimal("20.00"));
			orderA.addItem(itemBook);
			// [FLUSH] lúc commit: cascade PERSIST lan xuống itemBook → INSERT order_item
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("order_item", 1);
		sql.assertUpdates("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(U0 + ".purchase_order");
		assertThat(db.countOf(U0 + ".order_item")).as("có thêm 1 dòng item").isEqualTo(2);
		assertThat(db.rowSnapshot(U0 + ".purchase_order", orderId))
			.as("collection inverse không thuộc state của cha nên cha không dirty: version, updated_at giữ nguyên")
			.isEqualTo(orderRowBefore);
	}

	/**
	 * G4-S05 🔬 — U0: xoá con qua cha (orphanRemoval)
	 *
	 * <p>Mapping: như S04.
	 * <p><b>Câu hỏi</b>: {@code order.removeItem(item)} (orphanRemoval) có làm version của cha tăng không?
	 * <p><b>Kết luận</b>: không, giống S04: chỉ DELETE order_item, cha giữ nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S05 | U0: xoá item qua cha (orphanRemoval) chỉ DELETE order_item, version của cha không tăng")
	void s05_removingChildThroughOrphanRemovalDoesNotBumpParentVersion() {
		// ── GIVEN ──
		Long orderId = seedU0OrderWithPen();
		Map<String, Object> orderRowBefore = db.rowSnapshot(U0 + ".purchase_order", orderId);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
			orderA.removeItem(orderA.getItems().get(0));
			// [FLUSH] lúc commit: item không còn trong collection → mồ côi → DELETE
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("order_item", 1);
		sql.assertUpdates("purchase_order", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(U0 + ".order_item")).as("item mồ côi bị xoá").isEqualTo(0);
		assertThat(db.rowSnapshot(U0 + ".purchase_order", orderId)).as("cha giữ nguyên").isEqualTo(orderRowBefore);
	}

	/**
	 * G4-S06 🔬 — U2: thêm con vào collection OWNING (bảng nối)
	 *
	 * <p>Mapping: {@code PurchaseOrder.items} là {@code @OneToMany} một chiều + {@code @JoinTable} (OWNING side).
	 * <p><b>Câu hỏi</b>: thêm item vào collection owning có làm version của cha tăng không?
	 * <p><b>Kết luận</b>: có. {@code UPDATE purchase_order set ..., version = ? where id = ? and version = ?} (version tăng 1).
	 * Ngoài ra Hibernate không chỉ INSERT 1 dòng nối mà XOÁ HẾT dòng nối của order rồi INSERT lại cả 2 (collection kiểu List/bag).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S06 | U2: thêm item vào collection owning (bảng nối) làm version của cha tăng")
	void s06_addingToOwningCollectionBumpsParentVersion() {
		// ── GIVEN ──
		Long orderId = seedU2OrderWithPen();
		Long bookProductId = productIdByName(U2, "BOOK");
		Map<String, Object> orderRowBefore = db.rowSnapshot(U2 + ".purchase_order", orderId);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			U2PurchaseOrder orderA = em.find(U2PurchaseOrder.class, orderId);
			orderA.addItem(new U2OrderItem(em.find(U2Product.class, bookProductId), 3, new BigDecimal("20.00")));
			// [FLUSH] lúc commit: collection owning thay đổi → cha dirty → tăng version
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("order_item", 1);
		sql.assertDeletes("purchase_order_items", 1); // List (bag) owning: Hibernate XOÁ HẾT dòng nối của order rồi ghi lại (như G1-S08)
		sql.assertInserts("purchase_order_items", 2); // ghi lại cả item cũ (PEN) lẫn item mới (BOOK)
		sql.assertUpdates("purchase_order", 1);

		// ── THEN: DB thật ──
		db.print(U2 + ".purchase_order");
		db.print(U2 + ".purchase_order_items");
		assertThat(db.countOf(U2 + ".purchase_order_items")).as("bảng nối có đủ 2 dòng sau khi recreate").isEqualTo(2);
		Map<String, Object> orderRowAfter = db.rowSnapshot(U2 + ".purchase_order", orderId);
		assertThat(((Number) orderRowAfter.get("version")).longValue())
			.as("collection owning thuộc state của cha → version tăng thêm 1")
			.isEqualTo(((Number) orderRowBefore.get("version")).longValue() + 1);
	}

	/**
	 * G4-S07 — U3: như S06 + {@code @OptimisticLock(excluded = true)}
	 *
	 * <p>Mapping: như U2, {@code items} thêm {@code @OptimisticLock(excluded = true)}.
	 * <p><b>Câu hỏi</b>: có tắt được việc tăng version của cha khi đổi collection owning không?
	 * <p><b>Kết luận</b>: có. Bảng nối vẫn được ghi (recreate như S06) nhưng không còn UPDATE purchase_order.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S07 | U3: @OptimisticLock(excluded = true) → thêm item vào collection owning không tăng version cha")
	void s07_excludedCollectionDoesNotBumpParentVersion() {
		// ── GIVEN ──
		Long orderId = seedU3OrderWithPen();
		Long bookProductId = productIdByName(U3, "BOOK");
		Map<String, Object> orderRowBefore = db.rowSnapshot(U3 + ".purchase_order", orderId);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			U3PurchaseOrder orderA = em.find(U3PurchaseOrder.class, orderId);
			orderA.addItem(new U3OrderItem(em.find(U3Product.class, bookProductId), 3, new BigDecimal("20.00")));
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("order_item", 1);
		sql.assertDeletes("purchase_order_items", 1); // bảng nối vẫn bị recreate: @OptimisticLock chỉ tắt version, không đổi cách ghi collection
		sql.assertInserts("purchase_order_items", 2);
		sql.assertUpdates("purchase_order", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(U3 + ".purchase_order_items")).as("bảng nối vẫn có đủ 2 dòng").isEqualTo(2);
		assertThat(db.rowSnapshot(U3 + ".purchase_order", orderId))
			.as("collection bị loại khỏi optimistic lock nên cha không dirty, version giữ nguyên")
			.isEqualTo(orderRowBefore);
	}

	/**
	 * G4-S08 — U0: {@code @UpdateTimestamp} / {@code @PreUpdate} của cha KHÔNG chạy khi chỉ con đổi
	 *
	 * <p>Mapping: như S04.
	 * <p><b>Câu hỏi</b>: callback của cha có chạy khi chỉ item đổi không? Và đối chứng: khi cha tự đổi thì sao?
	 * <p><b>Kết luận</b>: không chạy: counter {@code @PreUpdate} = 0, {@code updated_at} giữ nguyên. Đối chứng: đổi {@code status}
	 * của cha thì counter = 1, {@code updated_at} và {@code version} đổi.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S08 | U0: chỉ con đổi thì @PreUpdate và updated_at của cha không chạy (đối chứng: cha tự đổi thì có)")
	void s08_parentCallbacksDoNotRunWhenOnlyChildChanges() {
		// ── GIVEN ──
		Long orderId = seedU0OrderWithPen();
		Map<String, Object> orderRowBefore = db.rowSnapshot(U0 + ".purchase_order", orderId);

		// ── WHEN 1: chỉ sửa item ──
		U0PurchaseOrder.PRE_UPDATE_CALLS.set(0);
		inTx(() -> {
			U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
			orderA.getItems().get(0).setQuantity(5);
		});

		// ── THEN 1 ──
		assertThat(U0PurchaseOrder.PRE_UPDATE_CALLS.get()).as("cha không dirty nên callback @PreUpdate không được gọi").isEqualTo(0);
		assertThat(db.rowSnapshot(U0 + ".purchase_order", orderId)).as("updated_at và version giữ nguyên").isEqualTo(orderRowBefore);

		// ── WHEN 2 (đối chứng): sửa field của CHÍNH cha ──
		inTx(() -> {
			U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
			orderA.setStatus("PAID");
			assertState(em, orderA, MANAGED);
		});

		// ── THEN 2 ──
		Map<String, Object> orderRowAfter = db.rowSnapshot(U0 + ".purchase_order", orderId);
		assertThat(U0PurchaseOrder.PRE_UPDATE_CALLS.get()).as("đối chứng: cha tự đổi thì callback phải chạy đúng 1 lần").isEqualTo(1);
		assertThat(orderRowAfter.get("updated_at")).as("@UpdateTimestamp ghi thời điểm mới").isNotEqualTo(orderRowBefore.get("updated_at"));
		assertThat(((Number) orderRowAfter.get("version")).longValue()).as("version tăng 1").isEqualTo(((Number) orderRowBefore.get("version")).longValue() + 1);
	}

	/**
	 * G4-S09 🔬 — U0: muốn version cha TĂNG khi con đổi (aggregate root)
	 *
	 * <p>Mapping: như S04.
	 * <p><b>Câu hỏi</b>: làm sao chủ động tăng version của cha khi chỉ sửa con?
	 * <p><b>Kết luận</b>: tải cha bằng {@code LockModeType.OPTIMISTIC_FORCE_INCREMENT}; lúc flush Hibernate thêm
	 * {@code UPDATE purchase_order set version = ?} dù cha không đổi gì.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S09 | U0: find(order, OPTIMISTIC_FORCE_INCREMENT) rồi sửa item → version cha tăng")
	void s09_forceIncrementBumpsParentVersionWhenChildChanges() {
		// ── GIVEN ──
		Long orderId = seedU0OrderWithPen();
		Map<String, Object> orderRowBefore = db.rowSnapshot(U0 + ".purchase_order", orderId);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
			orderA.getItems().get(0).setQuantity(5);
			// [FLUSH] lúc commit: item dirty → UPDATE order_item; cha bị khoá FORCE_INCREMENT → UPDATE version
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);
		sql.assertUpdates("purchase_order", 1);

		// ── THEN: DB thật ──
		Map<String, Object> orderRowAfter = db.rowSnapshot(U0 + ".purchase_order", orderId);
		assertThat(((Number) orderRowAfter.get("version")).longValue())
			.as("OPTIMISTIC_FORCE_INCREMENT buộc tăng version dù cha không đổi field nào")
			.isEqualTo(((Number) orderRowBefore.get("version")).longValue() + 1);
	}
}
