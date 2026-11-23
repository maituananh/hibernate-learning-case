package com.example.hibernatelab.g3_orphan_removal;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.TRANSIENT;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g3_orphan_removal.o0_cascade_all.O0ItemDiscount;
import com.example.hibernatelab.g3_orphan_removal.o0_cascade_all.O0OrderItem;
import com.example.hibernatelab.g3_orphan_removal.o0_cascade_all.O0PurchaseOrder;
import com.example.hibernatelab.g3_orphan_removal.o1_orphan.O1ItemDiscount;
import com.example.hibernatelab.g3_orphan_removal.o1_orphan.O1OrderItem;
import com.example.hibernatelab.g3_orphan_removal.o1_orphan.O1PurchaseOrder;
import com.example.hibernatelab.g3_orphan_removal.o2_orphan_no_cascade.O2OrderItem;
import com.example.hibernatelab.g3_orphan_removal.o2_orphan_no_cascade.O2PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G3, {@code orphanRemoval}: con bị TÁCH KHỎI cha (không phải cha bị xoá) thì dòng của con ra sao?
 * S01–S02 (O0, baseline), S03–S04 (O1), S05 (xoá cha, O0 vs O1), S06 (O2), S07 (gán collection mới),
 * S08 ({@code clear()}), S09 (chuyển item sang order khác), S11 (merge đồ thị DETACHED). S10 nằm ở {@link G3FlushOrderTest}.
 *
 * <p>Điểm cần nắm: {@code orphanRemoval} so sánh collection HIỆN TẠI với bản SNAPSHOT lúc tải. Phần tử có trong snapshot mà
 * không còn trong collection bị coi là "mồ côi" và bị DELETE lúc flush. Nó KHÔNG nhìn vào {@code item.order}
 * (owning side), đó là lý do S04 khác S01.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G3OrphanRemovalTest extends AbstractScenarioTest {

	private static final String O0 = "g3_o0_cascade_all";
	private static final String O1 = "g3_o1_orphan";
	private static final String O2 = "g3_o2_orphan_no_cascade";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { O0, O1, O2 };
	}

	// ── dữ liệu mẫu dùng chung (GIVEN): order có 1 item PEN, item có 1 discount ──

	/** O0: persist order, cascade = ALL kéo theo item và discount. Trả về id order. */
	private Long seedO0OrderWithPen() {
		O0PurchaseOrder orderA = new O0PurchaseOrder("A-001");
		O0OrderItem itemPen = new O0OrderItem("PEN", 1);
		itemPen.addDiscount(new O0ItemDiscount("Giảm 10%"));
		orderA.addItem(itemPen);
		return inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});
	}

	/** O1: giống O0 nhưng có orphanRemoval. Trả về id order. */
	private Long seedO1OrderWithPen() {
		O1PurchaseOrder orderA = new O1PurchaseOrder("A-001");
		O1OrderItem itemPen = new O1OrderItem("PEN", 1);
		itemPen.addDiscount(new O1ItemDiscount("Giảm 10%"));
		orderA.addItem(itemPen);
		return inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});
	}

	/**
	 * G3-S01 — O0: chỉ {@code items.remove(item)}, KHÔNG set {@code item.order = null}
	 *
	 * <p>Mapping: {@code PurchaseOrder.items} là INVERSE side ({@code mappedBy}), {@code cascade = ALL}, không orphanRemoval.
	 * <p><b>Câu hỏi</b>: bỏ item khỏi collection của order thì DB có thay đổi gì không?
	 * <p><b>Kết luận</b>: không có UPDATE/DELETE nào. Collection là phía inverse nên Hibernate không ghi FK từ nó;
	 * FK do {@code item.order} (owning side) quyết định và vẫn trỏ về order. Tải lại thì item vẫn thuộc order.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S01 | O0: chỉ items.remove(item) không đổi gì trong DB (collection là inverse side)")
	void s01_removingFromInverseCollectionOnlyChangesNothingInDb() {
		// ── GIVEN: order A-001 có 1 item PEN (kèm 1 discount) ──
		Long orderId = seedO0OrderWithPen();

		// ── WHEN: chỉ bỏ item khỏi collection, không đụng item.order ──
		sql.reset();
		inTx(() -> {
			O0PurchaseOrder orderA = em.find(O0PurchaseOrder.class, orderId);
			O0OrderItem itemPen = orderA.getItems().get(0);
			orderA.getItems().remove(itemPen);
			// [LIFECYCLE] item vẫn MANAGED, không ai đánh dấu nó REMOVED (không có orphanRemoval)
			assertState(em, itemPen, MANAGED);
			// [FLUSH] lúc commit: dirty checking không thấy gì thay đổi ở item (item.order vẫn là orderA)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 0);
		sql.assertDeletes("order_item", 0);

		// ── THEN: DB thật ──
		db.print(O0 + ".order_item");
		assertThat(db.countOf(O0 + ".order_item")).as("không orphanRemoval nên dòng item không bị xoá").isEqualTo(1);
		assertThat(db.rowsOf(O0 + ".order_item").get(0).get("order_id"))
			.as("FK do owning side item.order ghi; không ai đổi nó nên item vẫn thuộc order")
			.isEqualTo(orderId);
	}

	/**
	 * G3-S02 — O0: helper {@code removeItem()} (remove + {@code setOrder(null)})
	 *
	 * <p>Mapping: như S01.
	 * <p><b>Câu hỏi</b>: gỡ đủ hai phía thì DB ra sao khi KHÔNG có orphanRemoval?
	 * <p><b>Kết luận</b>: 1 {@code UPDATE order_item SET order_id = NULL}. Dòng item vẫn còn, thành "mồ côi" trong DB
	 * (không thuộc order nào). Nếu FK là NOT NULL thì lệnh này vi phạm constraint (ghi chú, không có biến thể riêng).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S02 | O0: removeItem() chỉ UPDATE order_id = NULL, dòng item thành mồ côi trong DB")
	void s02_removeItemHelperLeavesOrphanRowWithNullForeignKey() {
		// ── GIVEN ──
		Long orderId = seedO0OrderWithPen();

		// ── WHEN: gỡ cả hai phía bằng helper ──
		sql.reset();
		inTx(() -> {
			O0PurchaseOrder orderA = em.find(O0PurchaseOrder.class, orderId);
			orderA.removeItem(orderA.getItems().get(0));
			// [FLUSH] lúc commit: item.order đổi từ orderA sang null → UPDATE
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);
		sql.assertDeletes("order_item", 0);

		// ── THEN: DB thật ──
		db.print(O0 + ".order_item");
		assertThat(db.countOf(O0 + ".order_item")).as("không orphanRemoval nên dòng item còn nguyên").isEqualTo(1);
		assertThat(db.rowsOf(O0 + ".order_item").get(0).get("order_id"))
			.as("item bị tách khỏi order: FK thành NULL, đây chính là dòng mồ côi")
			.isNull();
	}

	/**
	 * G3-S03 — O1: helper {@code removeItem()}
	 *
	 * <p>Mapping: {@code items} và {@code discounts} đều {@code cascade = ALL, orphanRemoval = true}.
	 * <p><b>Câu hỏi</b>: cùng thao tác S02 nhưng có orphanRemoval thì sao?
	 * <p><b>Kết luận</b>: item bị DELETE (không UPDATE). Vì {@code discounts} cũng có orphanRemoval/cascade, DELETE discount
	 * của item chạy TRƯỚC DELETE item (discount giữ FK trỏ vào item).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S03 | O1: removeItem() DELETE discount của item rồi DELETE item")
	void s03_removeItemWithOrphanRemovalDeletesDiscountsThenItem() {
		// ── GIVEN ──
		Long orderId = seedO1OrderWithPen();

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			O1PurchaseOrder orderA = em.find(O1PurchaseOrder.class, orderId);
			O1OrderItem itemPen = orderA.getItems().get(0);
			orderA.removeItem(itemPen);
			// [LIFECYCLE] item vẫn MANAGED: orphan chỉ bị phát hiện LÚC FLUSH, chưa phải lúc remove khỏi list
			assertState(em, itemPen, MANAGED);
			// [FLUSH] lúc commit: Hibernate so collection với snapshot, thấy itemPen mồ côi → xoá nó (và discount của nó)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("item_discount", 1);
		sql.assertDeletes("order_item", 1);
		sql.assertUpdates("order_item", 0);
		sql.assertInOrder("delete from " + O1 + ".item_discount", "delete from " + O1 + ".order_item");

		// ── THEN: DB thật ──
		assertThat(db.countOf(O1 + ".order_item")).as("orphanRemoval xoá dòng item").isZero();
		assertThat(db.countOf(O1 + ".item_discount")).as("discount cũng bị xoá, nếu không FK sẽ chặn xoá item").isZero();
		assertThat(db.countOf(O1 + ".purchase_order")).as("order cha không bị đụng tới").isEqualTo(1);
	}

	/**
	 * G3-S04 — O1 🔬: chỉ {@code items.remove(item)} (quên set null)
	 *
	 * <p>Mapping: như S03.
	 * <p><b>Câu hỏi</b>: orphanRemoval có cần {@code item.order = null} không? (đối chiếu S01 ở O0: không có gì xảy ra)
	 * <p><b>Kết luận</b>: vẫn DELETE y như S03. orphanRemoval dựa trên việc so collection với snapshot, không nhìn
	 * {@code item.order}. Đây là điểm khác O0-S01.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S04 | O1 🔬: chỉ items.remove(item) vẫn DELETE (orphanRemoval dựa trên snapshot collection)")
	void s04_removingFromCollectionAloneStillDeletesWithOrphanRemoval() {
		// ── GIVEN ──
		Long orderId = seedO1OrderWithPen();

		// ── WHEN: chỉ bỏ khỏi collection, item.order vẫn trỏ về orderA ──
		sql.reset();
		inTx(() -> {
			O1PurchaseOrder orderA = em.find(O1PurchaseOrder.class, orderId);
			orderA.getItems().remove(0);
			// [FLUSH] lúc commit: snapshot có item, collection hiện tại không có → orphan → DELETE
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("order_item", 1);
		sql.assertDeletes("item_discount", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(O1 + ".order_item")).as("không cần set item.order = null, orphanRemoval vẫn xoá dòng").isZero();
	}

	/**
	 * G3-S05a — O0: xoá order (cascade ALL, không orphanRemoval)
	 *
	 * <p>Mapping: như S01.
	 * <p><b>Câu hỏi</b>: xoá cha thì con có bị xoá không? (lấp ô "Xoá cha" của bảng so sánh ở README)
	 * <p><b>Kết luận</b>: có. {@code CascadeType.REMOVE} lan xuống item rồi xuống discount: DELETE discount, item, rồi order.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S05a | O0: xoá order kéo theo xoá item và discount nhờ cascade REMOVE")
	void s05a_deletingParentDeletesChildrenViaCascadeRemove() {
		// ── GIVEN ──
		Long orderId = seedO0OrderWithPen();

		// ── WHEN ──
		sql.reset();
		inTx(() -> em.remove(em.find(O0PurchaseOrder.class, orderId)));
		// [FLUSH] lúc commit: DELETE discount → item → order (con xoá trước vì giữ FK)

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("item_discount", 1);
		sql.assertDeletes("order_item", 1);
		sql.assertDeletes("purchase_order", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(O0 + ".purchase_order")).as("order đã bị xoá").isZero();
		assertThat(db.countOf(O0 + ".order_item")).as("cascade REMOVE xoá item").isZero();
		assertThat(db.countOf(O0 + ".item_discount")).as("cascade REMOVE lan tiếp xuống discount").isZero();
	}

	/**
	 * G3-S05b — O1: xoá order (cascade ALL + orphanRemoval)
	 *
	 * <p>Mapping: như S03.
	 * <p><b>Câu hỏi</b>: có orphanRemoval thì xoá cha khác gì O0-S05a?
	 * <p><b>Kết luận</b>: kết quả giống hệt O0 (cả ba bảng trống, cùng số DELETE). Ô "Xoá cha" của hai cơ chế đều là "Xoá con".
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S05b | O1: xoá order cho kết quả giống O0, con bị xoá hết")
	void s05b_deletingParentWithOrphanRemovalGivesSameResultAsCascadeRemove() {
		// ── GIVEN ──
		Long orderId = seedO1OrderWithPen();

		// ── WHEN ──
		sql.reset();
		inTx(() -> em.remove(em.find(O1PurchaseOrder.class, orderId)));

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("item_discount", 1);
		sql.assertDeletes("order_item", 1);
		sql.assertDeletes("purchase_order", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(O1 + ".purchase_order")).as("order đã bị xoá").isZero();
		assertThat(db.countOf(O1 + ".order_item")).as("item bị xoá giống O0").isZero();
		assertThat(db.countOf(O1 + ".item_discount")).as("discount bị xoá giống O0").isZero();
	}

	/**
	 * G3-S06a — O2 🔬: {@code orphanRemoval = true} KHÔNG có cascade, xoá order
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order", orphanRemoval = true)}, không cascade.
	 * <p><b>Câu hỏi</b>: không có {@code CascadeType.REMOVE}, xoá order có xoá item không?
	 * <p><b>Kết luận</b>: có. orphanRemoval = true tự kéo theo hành vi remove khi cha bị remove (đúng theo spec JPA),
	 * dù không khai cascade REMOVE: SELECT item, DELETE item, DELETE order.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S06a | O2 🔬: orphanRemoval không cascade vẫn xoá item khi xoá order")
	void s06a_orphanRemovalAloneStillDeletesChildrenWhenParentIsRemoved() {
		// ── GIVEN: O2 không cascade nên phải persist item tường minh ──
		O2PurchaseOrder orderA = new O2PurchaseOrder("A-001");
		O2OrderItem itemPen = new O2OrderItem("PEN", 1);
		orderA.addItem(itemPen);
		Long orderId = inTxReturning(() -> {
			em.persist(orderA);
			em.persist(itemPen);
			return orderA.getId();
		});

		// ── WHEN ──
		sql.reset();
		inTx(() -> em.remove(em.find(O2PurchaseOrder.class, orderId)));

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("order_item", 1);
		sql.assertDeletes("purchase_order", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(O2 + ".order_item")).as("orphanRemoval = true kéo theo remove con khi cha bị remove").isZero();
		assertThat(db.countOf(O2 + ".purchase_order")).as("order đã bị xoá").isZero();
	}

	/**
	 * G3-S06b — O2 🔬: persist order mới có item mới (không PERSIST cascade)
	 *
	 * <p>Mapping: như S06a.
	 * <p><b>Câu hỏi</b>: orphanRemoval có thay được cascade PERSIST không?
	 * <p><b>Kết luận</b>: không, và cũng KHÔNG báo lỗi: chỉ INSERT order, item bị bỏ qua im lặng, vẫn TRANSIENT (chưa có id).
	 * orphanRemoval chỉ lo việc XOÁ, không lo việc LƯU.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S06b | O2 🔬: persist order không lưu item mới, và không báo lỗi (orphanRemoval không phải PERSIST)")
	void s06b_orphanRemovalDoesNotPersistNewChildren() {
		// ── GIVEN: order A-001 với 1 item mới (chưa persist) ──
		O2PurchaseOrder orderA = new O2PurchaseOrder("A-001");
		O2OrderItem itemPen = new O2OrderItem("PEN", 1);
		orderA.addItem(itemPen);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			em.persist(orderA);
			// [LIFECYCLE] chỉ order được persist; item không có cascade PERSIST nên vẫn TRANSIENT
			assertState(em, itemPen, TRANSIENT);
			// [FLUSH] lúc commit: chỉ INSERT order; item bị bỏ qua im lặng
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
		sql.assertInserts("order_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(O2 + ".purchase_order")).as("order được lưu").isEqualTo(1);
		assertThat(db.countOf(O2 + ".order_item")).as("không cascade PERSIST nên item không được lưu").isZero();
	}

	/**
	 * G3-S07a — O1: gán collection MỚI cho {@code items}
	 *
	 * <p>Mapping: {@code items} có {@code orphanRemoval = true}.
	 * <p><b>Câu hỏi</b>: {@code order.setItems(new ArrayList<>(...))} có được không?
	 * <p><b>Kết luận</b>: không. Hibernate giữ bản {@code PersistentBag} đã tải để theo dõi orphan; thay hẳn nó bằng list mới
	 * làm Hibernate ném {@code HibernateException: A collection with orphan deletion was no longer referenced by the owning
	 * entity instance} lúc flush (Spring bọc thành {@code JpaSystemException}). Transaction rollback, DB giữ nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S07a | O1: setItems(new ArrayList) ném HibernateException vì collection cũ không còn được tham chiếu")
	void s07a_replacingCollectionInstanceThrowsWithOrphanRemoval() {
		// ── GIVEN ──
		Long orderId = seedO1OrderWithPen();

		// ── WHEN: thay cả collection bằng list mới ──
		assertThatThrownBy(() -> inTx(() -> {
			O1PurchaseOrder orderA = em.find(O1PurchaseOrder.class, orderId);
			orderA.getItems().size(); // khởi tạo collection: Hibernate bắt đầu theo dõi nó
			orderA.setItems(new ArrayList<>(List.of(new O1OrderItem("INK", 2))));
			// [FLUSH] lúc commit: Hibernate thấy PersistentBag cũ bị bỏ rơi → ném exception
		}))
			.as("orphanRemoval cần giữ NGUYÊN instance collection để biết phần tử nào bị bỏ")
			.rootCause()
			.hasMessageContaining("orphan deletion was no longer referenced");

		// ── THEN: DB thật ──
		assertThat(db.countOf(O1 + ".order_item")).as("rollback: vẫn chỉ có item PEN ban đầu").isEqualTo(1);
		assertThat(db.rowsOf(O1 + ".order_item").get(0).get("product_code")).as("INK chưa bao giờ được lưu").isEqualTo("PEN");
	}

	/**
	 * G3-S07b — O1: cách sửa S07a, {@code clear()} rồi {@code addAll()}
	 *
	 * <p>Mapping: như S07a.
	 * <p><b>Câu hỏi</b>: muốn thay toàn bộ item thì làm thế nào cho đúng?
	 * <p><b>Kết luận</b>: giữ nguyên instance collection: {@code getItems().clear()} rồi thêm item mới. Item PEN cũ bị DELETE
	 * (orphan), item INK mới được INSERT (cascade PERSIST lúc flush).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S07b | O1: clear() rồi addAll() giữ nguyên instance collection nên thay được item")
	void s07b_clearThenAddAllReplacesItemsSafely() {
		// ── GIVEN ──
		Long orderId = seedO1OrderWithPen();

		// ── WHEN: sửa collection tại chỗ ──
		sql.reset();
		inTx(() -> {
			O1PurchaseOrder orderA = em.find(O1PurchaseOrder.class, orderId);
			orderA.getItems().clear();
			orderA.addItem(new O1OrderItem("INK", 2));
			// [FLUSH] lúc commit: INSERT INK, DELETE PEN (khác mã nên không đụng unique)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("order_item", 1);
		sql.assertDeletes("order_item", 1);

		// ── THEN: DB thật ──
		db.print(O1 + ".order_item");
		assertThat(db.countOf(O1 + ".order_item")).as("PEN bị xoá, INK được thêm: còn đúng 1 dòng").isEqualTo(1);
		assertThat(db.rowsOf(O1 + ".order_item").get(0).get("product_code")).as("dòng còn lại là item mới").isEqualTo("INK");
	}

	/**
	 * G3-S08 — O1: {@code items.clear()}
	 *
	 * <p>Mapping: như S03.
	 * <p><b>Câu hỏi</b>: xoá sạch collection thì tốn bao nhiêu câu SQL, order có bị đụng không?
	 * <p><b>Kết luận</b>: DELETE từng discount và từng item (mỗi dòng một câu: 2 item × 1 discount = 4 DELETE). Order còn nguyên, không UPDATE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S08 | O1: items.clear() DELETE từng discount và item, order cha còn nguyên")
	void s08_clearDeletesEveryChildOneByOne() {
		// ── GIVEN: order A-001 có 2 item, mỗi item 1 discount ──
		O1PurchaseOrder orderA = new O1PurchaseOrder("A-001");
		O1OrderItem itemPen = new O1OrderItem("PEN", 1);
		itemPen.addDiscount(new O1ItemDiscount("Giảm 10%"));
		O1OrderItem itemInk = new O1OrderItem("INK", 2);
		itemInk.addDiscount(new O1ItemDiscount("Giảm 5%"));
		orderA.addItem(itemPen);
		orderA.addItem(itemInk);
		Long orderId = inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN ──
		sql.reset();
		inTx(() -> em.find(O1PurchaseOrder.class, orderId).getItems().clear());
		// [FLUSH] lúc commit: cả 2 item đều mồ côi → DELETE discount ×2, DELETE item ×2

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("item_discount", 2);
		sql.assertDeletes("order_item", 2);
		sql.assertUpdates("purchase_order", 0);
		sql.assertDeletes("purchase_order", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(O1 + ".order_item")).as("mọi item bị xoá").isZero();
		assertThat(db.countOf(O1 + ".item_discount")).as("mọi discount bị xoá theo item").isZero();
		assertThat(db.countOf(O1 + ".purchase_order")).as("clear() chỉ xoá con, order cha còn").isEqualTo(1);
	}

	/**
	 * G3-S09a — O1 🔬: chuyển item từ đơn A sang đơn B trong CÙNG transaction
	 *
	 * <p>Mapping: như S03.
	 * <p><b>Câu hỏi</b>: {@code orderA.removeItem(item); orderB.addItem(item);} làm item bị xoá, báo lỗi hay chuyển đơn?
	 * <p><b>Kết luận</b>: chuyển đơn: 1 {@code UPDATE order_item SET order_id = B}, KHÔNG DELETE. Lúc flush item vừa mất khỏi
	 * collection của A vừa nằm trong collection của B, nên Hibernate không coi nó là mồ côi. Discount của item đi theo.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S09a | O1 🔬: removeItem rồi addItem sang order khác trong cùng tx chỉ UPDATE order_id, không mất dữ liệu")
	void s09a_movingItemBetweenOrdersInSameTransactionOnlyUpdatesForeignKey() {
		// ── GIVEN: order A có item PEN (kèm discount), order B rỗng ──
		O1PurchaseOrder orderA = new O1PurchaseOrder("A-001");
		O1OrderItem itemPen = new O1OrderItem("PEN", 1);
		itemPen.addDiscount(new O1ItemDiscount("Giảm 10%"));
		orderA.addItem(itemPen);
		O1PurchaseOrder orderB = new O1PurchaseOrder("B-001");
		Long[] orderIds = inTxReturning(() -> {
			em.persist(orderA);
			em.persist(orderB);
			return new Long[] { orderA.getId(), orderB.getId() };
		});

		// ── WHEN: chuyển item từ A sang B ──
		sql.reset();
		inTx(() -> {
			O1PurchaseOrder managedA = em.find(O1PurchaseOrder.class, orderIds[0]);
			O1PurchaseOrder managedB = em.find(O1PurchaseOrder.class, orderIds[1]);
			O1OrderItem itemToMove = managedA.getItems().get(0);
			managedA.removeItem(itemToMove);
			managedB.addItem(itemToMove);
			// [FLUSH] lúc commit: item.order = B → UPDATE; không bị coi là orphan vì đang nằm trong collection của B
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);
		sql.assertDeletes("order_item", 0);
		sql.assertDeletes("item_discount", 0);

		// ── THEN: DB thật ──
		db.print(O1 + ".order_item");
		Map<String, Object> itemRow = db.rowsOf(O1 + ".order_item").get(0);
		assertThat(itemRow.get("order_id")).as("item đã chuyển sang order B").isEqualTo(orderIds[1]);
		assertThat(db.countOf(O1 + ".item_discount")).as("discount đi theo item, không bị xoá").isEqualTo(1);
	}

	/**
	 * G3-S09b — O1: chỉ đổi FK trực tiếp ({@code item.setOrder(orderB)}), không đụng collection của A
	 *
	 * <p>Mapping: như S03.
	 * <p><b>Câu hỏi</b>: cách "an toàn" đổi order của item là gì?
	 * <p><b>Kết luận</b>: gán {@code item.order = B} (owning side) là đủ để UPDATE FK. Collection của A không bị sửa nên snapshot
	 * không đổi, không có orphan, không DELETE. (Đồng bộ in-memory của hai collection là việc của code gọi.)
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S09b | O1: đổi FK trực tiếp item.setOrder(orderB) chỉ UPDATE, không có orphan")
	void s09b_changingOwningSideDirectlyOnlyUpdatesForeignKey() {
		// ── GIVEN ──
		O1PurchaseOrder orderA = new O1PurchaseOrder("A-001");
		orderA.addItem(new O1OrderItem("PEN", 1));
		O1PurchaseOrder orderB = new O1PurchaseOrder("B-001");
		Long[] orderIds = inTxReturning(() -> {
			em.persist(orderA);
			em.persist(orderB);
			return new Long[] { orderA.getId(), orderB.getId() };
		});

		// ── WHEN: đổi owning side, không đụng collection ──
		sql.reset();
		inTx(() -> {
			O1PurchaseOrder managedA = em.find(O1PurchaseOrder.class, orderIds[0]);
			O1PurchaseOrder managedB = em.find(O1PurchaseOrder.class, orderIds[1]);
			managedA.getItems().get(0).setOrder(managedB);
			// [FLUSH] lúc commit: dirty checking thấy item.order đổi → UPDATE order_item
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);
		sql.assertDeletes("order_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.rowsOf(O1 + ".order_item").get(0).get("order_id")).as("FK do owning side quyết định → đã sang B").isEqualTo(orderIds[1]);
	}

	/**
	 * G3-S11 — O1 ➕ 🔬: đồ thị DETACHED bỏ bớt item rồi {@code merge}
	 *
	 * <p>Mapping: như S03 ({@code cascade = ALL} gồm MERGE).
	 * <p><b>Câu hỏi</b>: đồ thị order đã DETACHED, bỏ một item khỏi collection, rồi {@code merge} thì item đó có bị xoá không?
	 * <p><b>Kết luận</b>: có. {@code merge} nạp lại order, so collection của bản DETACHED với bản MANAGED và copy state;
	 * item vắng mặt trở thành orphan và bị DELETE lúc flush. Item còn lại không bị đụng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G3-S11 | O1 ➕ 🔬: merge đồ thị DETACHED đã bỏ bớt item thì item vắng mặt bị DELETE")
	void s11_mergingDetachedGraphWithRemovedItemDeletesIt() {
		// ── GIVEN: order A có 2 item PEN và INK trong DB ──
		O1PurchaseOrder orderA = new O1PurchaseOrder("A-001");
		O1OrderItem itemPen = new O1OrderItem("PEN", 1);
		O1OrderItem itemInk = new O1OrderItem("INK", 2);
		orderA.addItem(itemPen);
		orderA.addItem(itemInk);
		Long orderId = inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});

		// ── Tải đồ thị rồi để nó DETACHED (collection đã khởi tạo trước khi tx kết thúc) ──
		O1PurchaseOrder detachedOrder = inTxReturning(() -> {
			O1PurchaseOrder loaded = em.find(O1PurchaseOrder.class, orderId);
			loaded.getItems().size();
			// [TX END] persistence context đóng → loaded và các item: MANAGED → DETACHED
			return loaded;
		});
		O1OrderItem detachedPen = null;
		for (O1OrderItem candidate : detachedOrder.getItems()) {
			if (candidate.getProductCode().equals("PEN")) {
				detachedPen = candidate;
			}
		}
		detachedOrder.getItems().remove(detachedPen); // bỏ PEN khỏi bản DETACHED

		// ── WHEN: merge bản DETACHED vào persistence context mới ──
		sql.reset();
		inTx(() -> em.merge(detachedOrder));
		// [FLUSH] lúc commit: collection MANAGED (sau khi copy state) mất PEN → orphan → DELETE

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("order_item", 1);

		// ── THEN: DB thật ──
		db.print(O1 + ".order_item");
		assertThat(db.countOf(O1 + ".order_item")).as("PEN bị xoá, INK còn").isEqualTo(1);
		assertThat(db.rowsOf(O1 + ".order_item").get(0).get("product_code")).as("dòng còn lại là INK").isEqualTo("INK");
	}
}
