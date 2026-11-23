package com.example.hibernatelab.g3_orphan_removal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.hibernatelab.g3_orphan_removal.o0_cascade_all.O0OrderItem;
import com.example.hibernatelab.g3_orphan_removal.o0_cascade_all.O0PurchaseOrder;
import com.example.hibernatelab.g3_orphan_removal.o1_orphan.O1OrderItem;
import com.example.hibernatelab.g3_orphan_removal.o1_orphan.O1PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G3-S10: thay một item bằng item mới CÓ CÙNG {@code product_code} (ràng buộc UNIQUE {@code (order_id, product_code)}).
 * Bài học về thứ tự ActionQueue (01-DESIGN §8): lúc flush Hibernate chạy INSERT trước, UPDATE sau, DELETE SAU CÙNG,
 * bất kể thứ tự bạn gọi trong code. Kể cả DELETE do orphanRemoval cũng đứng cuối.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G3FlushOrderTest extends AbstractScenarioTest {

	private static final String O0 = "g3_o0_cascade_all";
	private static final String O1 = "g3_o1_orphan";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { O0, O1 };
	}

	/**
	 * G3-S10a — O1 🔬: {@code removeItem(old); addItem(new cùng code)} trong một lần flush
	 *
	 * <p>Mapping: {@code items} {@code cascade = ALL, orphanRemoval = true}; unique {@code (order_id, product_code)}.
	 * <p><b>Câu hỏi</b>: orphan removal có chạy TRƯỚC INSERT item mới không (nếu có thì không vi phạm unique)?
	 * <p><b>Kết luận</b>: KHÔNG. INSERT item mới chạy trước, DELETE orphan chạy sau → PostgreSQL báo vi phạm unique
	 * ({@code DataIntegrityViolationException}), rollback, item cũ vẫn còn. (Giả thuyết ban đầu "thành công" đã SAI, xem RESULTS.md.)
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S10a | O1 🔬: thay item cùng product_code trong một flush vi phạm unique (INSERT chạy trước DELETE orphan)")
	void s10a_orphanDeleteRunsAfterInsertSoSameProductCodeViolatesUnique() {
		// ── GIVEN: order A-001 có item PEN số lượng 1 ──
		O1PurchaseOrder orderA = new O1PurchaseOrder("A-001");
		orderA.addItem(new O1OrderItem("PEN", 1));
		Long orderId = inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN: thay PEN cũ bằng PEN mới (số lượng 5) trong cùng một lần flush ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> {
			O1PurchaseOrder managedOrder = em.find(O1PurchaseOrder.class, orderId);
			O1OrderItem oldPen = managedOrder.getItems().get(0);
			managedOrder.removeItem(oldPen);
			managedOrder.addItem(new O1OrderItem("PEN", 5));
			// [FLUSH] lúc commit: INSERT PEN mới chạy TRƯỚC khi DELETE PEN cũ → trùng (order_id, 'PEN')
		}))
			.as("ActionQueue chạy INSERT trước DELETE nên lúc INSERT, PEN cũ vẫn còn trong bảng")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("order_item", 1);
		sql.assertDeletes("order_item", 0);

		// ── THEN: DB thật ──
		db.print(O1 + ".order_item");
		assertThat(db.countOf(O1 + ".order_item")).as("rollback: chỉ còn item cũ").isEqualTo(1);
		assertThat(db.rowsOf(O1 + ".order_item").get(0).get("quantity")).as("item cũ (số lượng 1) còn nguyên").isEqualTo(1);
	}

	/**
	 * G3-S10b — O0 🔬: {@code em.remove(old); persist(new cùng code)} trong một lần flush
	 *
	 * <p>Mapping: {@code items} {@code cascade = ALL}, không orphanRemoval; unique như S10a.
	 * <p><b>Câu hỏi</b>: dùng {@code em.remove} thay vì orphanRemoval có khác thứ tự không?
	 * <p><b>Kết luận</b>: không. Vẫn INSERT trước DELETE → vi phạm unique, y hệt S10a. Thứ tự flush không phụ thuộc cơ chế xoá.
	 * (Lưu ý: item cũ phải được gỡ khỏi collection, nếu không cascade PERSIST lúc flush sẽ "cứu" nó khỏi DELETE.)
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S10b | O0 🔬: em.remove(old) rồi persist(new cùng code) cũng vi phạm unique, cùng thứ tự INSERT → DELETE")
	void s10b_explicitRemoveFollowedByPersistAlsoViolatesUnique() {
		// ── GIVEN ──
		O0PurchaseOrder orderA = new O0PurchaseOrder("A-001");
		orderA.addItem(new O0OrderItem("PEN", 1));
		Long orderId = inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> {
			O0PurchaseOrder managedOrder = em.find(O0PurchaseOrder.class, orderId);
			O0OrderItem oldPen = managedOrder.getItems().get(0);
			managedOrder.removeItem(oldPen);
			em.remove(oldPen);
			managedOrder.addItem(new O0OrderItem("PEN", 5));
			// [FLUSH] lúc commit: INSERT PEN mới → vi phạm unique, DELETE PEN cũ chưa kịp chạy
		}))
			.as("em.remove cũng chỉ là DELETE xếp hàng chờ, và DELETE luôn đứng sau INSERT")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("order_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(O0 + ".order_item")).as("rollback: item cũ còn nguyên").isEqualTo(1);
	}

	/**
	 * G3-S10c — O1: cách sửa, {@code em.flush()} ngay sau khi gỡ item cũ
	 *
	 * <p>Mapping: như S10a.
	 * <p><b>Câu hỏi</b>: ép DELETE chạy trước INSERT bằng cách nào?
	 * <p><b>Kết luận</b>: gọi {@code em.flush()} sau {@code removeItem}: DELETE PEN cũ chạy ngay, rồi mới thêm PEN mới và
	 * INSERT lúc commit. Thành công, DB chỉ còn PEN mới. (Cách khác: sửa chính item cũ thay vì thay mới.)
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S10c | O1: em.flush() sau removeItem ép DELETE chạy trước, rồi INSERT item cùng product_code thành công")
	void s10c_flushingBetweenRemoveAndAddMakesDeleteRunFirst() {
		// ── GIVEN ──
		O1PurchaseOrder orderA = new O1PurchaseOrder("A-001");
		orderA.addItem(new O1OrderItem("PEN", 1));
		Long orderId = inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN: gỡ item cũ, flush ngay, rồi mới thêm item mới ──
		sql.reset();
		inTx(() -> {
			O1PurchaseOrder managedOrder = em.find(O1PurchaseOrder.class, orderId);
			managedOrder.removeItem(managedOrder.getItems().get(0));
			em.flush(); // [FLUSH] DELETE PEN cũ chạy NGAY tại đây
			managedOrder.addItem(new O1OrderItem("PEN", 5));
			// [FLUSH] lúc commit: INSERT PEN mới (lúc này PEN cũ đã biến mất nên không trùng)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInOrder("delete from " + O1 + ".order_item", "insert into " + O1 + ".order_item");

		// ── THEN: DB thật ──
		db.print(O1 + ".order_item");
		assertThat(db.countOf(O1 + ".order_item")).as("chỉ còn item mới").isEqualTo(1);
		assertThat(db.rowsOf(O1 + ".order_item").get(0).get("quantity")).as("đó là PEN mới (số lượng 5)").isEqualTo(5);
	}
}
