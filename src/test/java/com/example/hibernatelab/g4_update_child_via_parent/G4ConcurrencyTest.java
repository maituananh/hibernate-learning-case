package com.example.hibernatelab.g4_update_child_via_parent;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.example.hibernatelab.g4_update_child_via_parent.u5_child_version.U5Customer;
import com.example.hibernatelab.g4_update_child_via_parent.u5_child_version.U5OrderItem;
import com.example.hibernatelab.g4_update_child_via_parent.u5_child_version.U5Product;
import com.example.hibernatelab.g4_update_child_via_parent.u5_child_version.U5PurchaseOrder;
import com.example.hibernatelab.g4_update_child_via_parent.u5_child_version.U5PurchaseOrderRepository;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G4, cập nhật đồng thời (S11): item có {@code @Version} riêng thì "lost update" bị chặn, còn cha vẫn không đổi.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G4ConcurrencyTest extends AbstractScenarioTest {

	private static final String U5 = "g4_u5_child_version";

	@Autowired
	private U5PurchaseOrderRepository u5OrderRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { U5 };
	}

	/** Tải order + items rồi đóng tx: trả về bản DETACHED (mỗi lần gọi là một "người dùng" khác nhau). */
	private U5PurchaseOrder loadDetachedOrder(Long orderId) {
		return inTxReturning(() -> {
			U5PurchaseOrder loadedOrder = em.find(U5PurchaseOrder.class, orderId);
			loadedOrder.getItems().size(); // khởi tạo collection LAZY trước khi đóng tx
			return loadedOrder;
		});
	}

	/**
	 * G4-S11 — U5: hai bản DETACHED của cùng một item cập nhật đồng thời
	 *
	 * <p>Mapping: {@code OrderItem} có {@code @Version} riêng; {@code PurchaseOrder.items} cascade ALL (có MERGE).
	 * <p><b>Câu hỏi</b>: hai người cùng mở đơn (quantity = 1), người A sửa thành 5 và lưu trước, người B sửa thành 7 và lưu sau: điều gì xảy ra?
	 * <p><b>Kết luận</b>: lần lưu của B bị {@code ObjectOptimisticLockingFailureException} (version trong bản của B đã cũ), DB giữ 5 của A.
	 * Cha không đổi trong cả hai lần: version của item là cơ chế khoá RIÊNG, không liên quan version của order.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S11 | U5: hai bản DETACHED cùng sửa một item, bản lưu sau bị OptimisticLock; cha vẫn không đổi")
	void s11_secondDetachedCopyOfSameItemIsRejected() {
		// ── GIVEN: order A-001 có 1 item PEN × 1 ──
		U5Customer customerAn = new U5Customer("An");
		U5Product productPen = new U5Product("PEN");
		U5PurchaseOrder orderA = new U5PurchaseOrder("A-001", "NEW");
		orderA.setCustomer(customerAn);
		orderA.addItem(new U5OrderItem(productPen, 1, new BigDecimal("10.00")));
		Long orderId = inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(productPen);
			em.persist(orderA);
			return orderA.getId();
		});
		Map<String, Object> orderRowBefore = db.rowSnapshot(U5 + ".purchase_order", orderId);

		// tx1, tx2: hai người dùng cùng tải đơn → hai bản DETACHED độc lập, cùng item version = 0
		U5PurchaseOrder copyOfUserA = loadDetachedOrder(orderId);
		U5PurchaseOrder copyOfUserB = loadDetachedOrder(orderId);
		assertState(em, copyOfUserA, DETACHED);
		assertState(em, copyOfUserB, DETACHED);
		copyOfUserA.getItems().get(0).setQuantity(5);
		copyOfUserB.getItems().get(0).setQuantity(7);

		// ── WHEN 1: tx3 lưu bản của người A (đến trước) → thành công ──
		sql.reset();
		inTx(() -> u5OrderRepository.save(copyOfUserA));
		sql.print();
		sql.assertUpdates("order_item", 1);
		sql.assertUpdates("purchase_order", 0);

		// ── WHEN 2: tx4 lưu bản của người B (đến sau, version item đã cũ) → bị từ chối ──
		assertThatThrownBy(() -> inTx(() -> u5OrderRepository.save(copyOfUserB)))
			.as("version của item trong bản B là 0 nhưng DB đã là 1 → optimistic locking failure")
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);

		// ── THEN: DB thật ──
		db.print(U5 + ".order_item");
		Map<String, Object> itemRow = db.rowsOf(U5 + ".order_item").get(0);
		assertThat(itemRow.get("quantity")).as("giá trị của người A được giữ, của B bị từ chối").isEqualTo(5);
		assertThat(((Number) itemRow.get("version")).longValue()).as("item được cập nhật đúng 1 lần nên version = 1").isEqualTo(1L);
		assertThat(db.rowSnapshot(U5 + ".purchase_order", orderId))
			.as("version của order là cơ chế khoá riêng, không bị tăng khi con đổi")
			.isEqualTo(orderRowBefore);
	}
}
