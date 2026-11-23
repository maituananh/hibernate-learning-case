package com.example.hibernatelab.g9_integrated;

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

import com.example.hibernatelab.g9_integrated.s02_order_update.OrderItem;
import com.example.hibernatelab.g9_integrated.s02_order_update.OrderUpdateRepository;
import com.example.hibernatelab.g9_integrated.s02_order_update.Product;
import com.example.hibernatelab.g9_integrated.s02_order_update.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

import jakarta.persistence.OptimisticLockException;

/**
 * G9-S08 ➕ (hai người cùng sửa một đơn) và G9-S09 ➕ (chuyển item giữa hai đơn + tính lại tổng tiền).
 * Dùng chung entity của S02: {@code PurchaseOrder} có {@code @Version}, items INVERSE + cascade ALL + orphanRemoval.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G9S08S09OptimisticLockAndMoveItemTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g9_s02_order_update";

	@Autowired
	private OrderUpdateRepository orderRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/**
	 * G9-S08 — Hai bản DETACHED của cùng một order: lưu bản 1 rồi lưu bản 2
	 *
	 * <p><b>Câu hỏi</b>: hai người cùng mở đơn (cùng version), người thứ hai lưu sau thì sao?
	 * <p><b>Kết luận</b>: lần lưu thứ hai ném {@code ObjectOptimisticLockingFailureException} (qua repository Spring Data) vì version
	 * trong DB đã tăng; DB giữ dữ liệu của người thứ nhất (không ghi đè mất âm thầm như G9-S02b).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S08 | Hai bản D cùng version: lưu bản 2 ném ObjectOptimisticLockingFailureException, DB giữ bản 1")
	void s08_savingSecondStaleCopyFailsWithOptimisticLockException() {
		// ── GIVEN ──
		Long orderId = inTxReturning(() -> {
			PurchaseOrder orderA = new PurchaseOrder("A-001", "NEW");
			em.persist(orderA);
			return orderA.getId();
		});
		// Hai người mở đơn ở hai transaction khác nhau: cả hai nhận bản DETACHED có version = 0.
		PurchaseOrder copyOfUserOne = inTxReturning(() -> em.find(PurchaseOrder.class, orderId));
		PurchaseOrder copyOfUserTwo = inTxReturning(() -> em.find(PurchaseOrder.class, orderId));
		copyOfUserOne.setNote("bản của người 1");
		copyOfUserTwo.setNote("bản của người 2");

		// ── WHEN: người 1 lưu trước ──
		orderRepository.save(copyOfUserOne); // save() trên entity DETACHED = merge → UPDATE ... set version=1 where id=? and version=0
		Map<String, Object> rowAfterUserOne = db.rowById(SCHEMA + ".purchase_order", orderId);
		assertThat(rowAfterUserOne.get("version")).as("lần lưu đầu thành công nên version tăng 0 → 1").isEqualTo(1L);

		// ── WHEN + THEN: người 2 lưu sau với version cũ (0) ──
		sql.reset();
		assertThatThrownBy(() -> orderRepository.save(copyOfUserTwo))
			.as("bản 2 mang version 0 nhưng DB đã là 1 → Hibernate phát hiện ghi đè đồng thời, Spring dịch sang exception của nó")
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);

		// ── THEN: DB thật ──
		sql.print();
		Map<String, Object> rowFinal = db.rowById(SCHEMA + ".purchase_order", orderId);
		assertThat(rowFinal.get("note")).as("DB giữ dữ liệu của người 1, bản 2 không ghi đè được").isEqualTo("bản của người 1");
		assertThat(rowFinal.get("version")).as("lần lưu thất bại bị rollback nên version vẫn là 1").isEqualTo(1L);
	}

	/**
	 * G9-S08b — Cùng tình huống nhưng gọi thẳng {@code em.merge} (không qua repository)
	 *
	 * <p><b>Câu hỏi</b>: nếu không qua repository Spring Data thì exception có khác không?
	 * <p><b>Kết luận</b>: có. {@code em.merge} ném thẳng {@code jakarta.persistence.OptimisticLockException} (merge so version ngay lúc gọi);
	 * chỉ khi đi qua bean {@code @Repository} Spring mới dịch nó thành {@code ObjectOptimisticLockingFailureException}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S08b | em.merge trực tiếp: ném OptimisticLockException của JPA (chưa được Spring dịch)")
	void s08b_rawEntityManagerMergeThrowsJpaOptimisticLockException() {
		// ── GIVEN ──
		Long orderId = inTxReturning(() -> {
			PurchaseOrder orderA = new PurchaseOrder("A-001", "NEW");
			em.persist(orderA);
			return orderA.getId();
		});
		PurchaseOrder staleCopy = inTxReturning(() -> em.find(PurchaseOrder.class, orderId)); // DETACHED, version 0
		inTx(() -> em.find(PurchaseOrder.class, orderId).setNote("người khác đã sửa trước")); // UPDATE → version 1
		staleCopy.setNote("bản cũ");

		// ── WHEN + THEN ──
		assertThatThrownBy(() -> inTx(() -> em.merge(staleCopy)))
			.as("em.merge không đi qua proxy @Repository nên không có bước dịch exception")
			.isInstanceOf(OptimisticLockException.class);
		assertThat(db.rowById(SCHEMA + ".purchase_order", orderId).get("note"))
			.as("lần merge thất bại không ghi gì").isEqualTo("người khác đã sửa trước");
	}

	/**
	 * G9-S09 — Chuyển một item từ đơn A sang đơn B và tính lại tổng tiền cả hai
	 *
	 * <p><b>Câu hỏi</b>: chuyển item giữa hai order cần UPDATE những gì, version của hai cha có đổi không, orphanRemoval có xoá nhầm item không?
	 * <p><b>Kết luận</b> 🔬: item được thêm sang collection của B nên orphanRemoval KHÔNG xoá nó (0 DELETE); chỉ có 1 UPDATE order_item
	 * (đổi order_id) và 2 UPDATE purchase_order (total đổi nên version của cả hai cha tăng). Khác G4: ở đây đụng tới cha là CỐ Ý, vì {@code total} là cột thật.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S09 | Chuyển item giữa hai đơn: cả hai cha đổi total và version")
	void s09_movingItemBetweenOrdersUpdatesBothParents() {
		// ── GIVEN ──
		Long[] ids = seedTwoOrders();
		Long orderAId = ids[0];
		Long orderBId = ids[1];
		Long movedItemId = ids[2];

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			PurchaseOrder orderA = em.find(PurchaseOrder.class, orderAId);
			PurchaseOrder orderB = em.find(PurchaseOrder.class, orderBId);
			OrderItem movedItem = em.find(OrderItem.class, movedItemId);
			orderA.removeItem(movedItem); // gỡ khỏi A (inverse) + movedItem.order = null
			orderB.addItem(movedItem); // thêm vào B (inverse) + movedItem.order = B (owning, quyết định FK)
			orderA.recalculateTotal();
			orderB.recalculateTotal();
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes(SCHEMA + ".order_item", 0);
		sql.assertUpdates(SCHEMA + ".order_item", 1);
		sql.assertUpdates(SCHEMA + ".purchase_order", 2);

		// ── THEN: DB thật ──
		db.print(SCHEMA + ".purchase_order");
		db.print(SCHEMA + ".order_item");
		assertThat(db.countOf(SCHEMA + ".order_item"))
			.as("item vừa gỡ khỏi A nhưng được thêm vào B trong CÙNG transaction nên orphanRemoval không xoá nó").isEqualTo(3);
		assertThat(db.rowById(SCHEMA + ".order_item", movedItemId).get("order_id"))
			.as("owning side OrderItem.order đã trỏ sang B nên FK order_id đổi").isEqualTo(orderBId);
		Map<String, Object> rowA = db.rowById(SCHEMA + ".purchase_order", orderAId);
		Map<String, Object> rowB = db.rowById(SCHEMA + ".purchase_order", orderBId);
		assertThat((BigDecimal) rowA.get("total")).as("A còn PEN × 1 × 10.00").isEqualByComparingTo("10.00");
		assertThat((BigDecimal) rowB.get("total")).as("B = MOUSE 15.00 + BOOK 2 × 20.00").isEqualByComparingTo("55.00");
		assertThat(rowA.get("version")).as("total của A đổi nên A bị UPDATE và version tăng 0 → 1").isEqualTo(1L);
		assertThat(rowB.get("version")).as("total của B đổi nên B bị UPDATE và version tăng 0 → 1").isEqualTo(1L);
	}

	/** Order A: PEN×1@10 + BOOK×2@20 (total 50). Order B: MOUSE×1@15 (total 15). Trả về {orderAId, orderBId, bookItemId}. */
	private Long[] seedTwoOrders() {
		Product productPen = new Product("PEN");
		Product productBook = new Product("BOOK");
		Product productMouse = new Product("MOUSE");
		PurchaseOrder orderA = new PurchaseOrder("A-001", "NEW");
		OrderItem itemPen = new OrderItem(productPen, 1, new BigDecimal("10.00"));
		OrderItem itemBook = new OrderItem(productBook, 2, new BigDecimal("20.00"));
		orderA.addItem(itemPen);
		orderA.addItem(itemBook);
		orderA.recalculateTotal();
		PurchaseOrder orderB = new PurchaseOrder("B-001", "NEW");
		orderB.addItem(new OrderItem(productMouse, 1, new BigDecimal("15.00")));
		orderB.recalculateTotal();
		return inTxReturning(() -> {
			em.persist(productPen);
			em.persist(productBook);
			em.persist(productMouse);
			em.persist(orderA);
			em.persist(orderB);
			return new Long[] { orderA.getId(), orderB.getId(), itemBook.getId() };
		});
	}
}
