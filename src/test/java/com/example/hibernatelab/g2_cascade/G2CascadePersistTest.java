package com.example.hibernatelab.g2_cascade;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.TRANSIENT;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
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
import com.example.hibernatelab.g2_cascade.c4_all.C4Customer;
import com.example.hibernatelab.g2_cascade.c4_all.C4OrderItem;
import com.example.hibernatelab.g2_cascade.c4_all.C4Product;
import com.example.hibernatelab.g2_cascade.c4_all.C4PurchaseOrder;
import com.example.hibernatelab.g2_cascade.c4deep_all_chain.C4DeepCustomer;
import com.example.hibernatelab.g2_cascade.c4deep_all_chain.C4DeepOrderItem;
import com.example.hibernatelab.g2_cascade.c4deep_all_chain.C4DeepProduct;
import com.example.hibernatelab.g2_cascade.c4deep_all_chain.C4DeepPurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G2, thao tác PERSIST: cascade {@code PERSIST} có lan từ order xuống item không?
 * Gồm S01 (C0, không cascade), S02 và S03 (C1, {@code cascade = PERSIST}), S11 (cascade 3 tầng).
 *
 * <p>Mọi quan hệ đều hai chiều chuẩn: {@code PurchaseOrder.items(mappedBy)} là INVERSE side,
 * {@code OrderItem.order} là OWNING side (ghi FK). Cascade luôn đặt ở INVERSE side {@code items}.
 * Product luôn được persist riêng trước (GIVEN) để item có chỗ trỏ tới, nhờ vậy test chỉ xoay quanh item.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G2CascadePersistTest extends AbstractScenarioTest {

	private static final String C0 = "g2_c0_none";
	private static final String C1 = "g2_c1_persist";
	private static final String C4 = "g2_c4_all";
	private static final String DEEP = "g2_c4deep_all_chain";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { C0, C1, C4, DEEP };
	}

	/**
	 * G2-S01 — C0: persist order có item mới, KHÔNG cascade
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order")} INVERSE side, không cascade.
	 * <p><b>Câu hỏi</b>: {@code order.addItem(...)} ×2 rồi {@code persist(order)}: item có được INSERT không,
	 * hay Hibernate báo lỗi vì order trỏ tới item chưa lưu?
	 * <p><b>Kết luận</b>: chỉ INSERT order; 2 item bị BỎ QUA IM LẶNG (không lỗi, không INSERT). Lý do không lỗi: {@code items}
	 * là INVERSE side nên Hibernate không có dòng nào để ghi cho collection này, và FK nằm ở item (chưa được lưu).
	 * Đây là giả thuyết (a) của 02-SCENARIOS, không phải (b) {@code TransientObjectException}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S01 | C0 không cascade: persist(order) không kéo theo item")
	void s01_withoutCascadePersistOrderDoesNotPersistItems() {
		// ── GIVEN: product đã có trong DB ──
		C0Product productPen = inTxReturning(() -> {
			C0Product product = new C0Product("Bút bi");
			em.persist(product);
			return product;
		});

		// ── WHEN: order có 2 item mới (TRANSIENT), chỉ persist order ──
		C0OrderItem itemOne = new C0OrderItem(productPen, 1);
		C0OrderItem itemTwo = new C0OrderItem(productPen, 2);
		sql.reset();
		inTx(() -> {
			C0PurchaseOrder orderA = new C0PurchaseOrder("A-001");
			orderA.addItem(itemOne);
			orderA.addItem(itemTwo);
			em.persist(orderA);
			// [LIFECYCLE] order TRANSIENT → MANAGED. Item KHÔNG đi theo vì không có cascade: vẫn TRANSIENT
			assertState(em, orderA, MANAGED);
			assertState(em, itemOne, TRANSIENT);
			assertState(em, itemTwo, TRANSIENT);
			// [FLUSH] lúc commit: INSERT purchase_order. Collection items là INVERSE side nên không có gì để ghi cho nó
		});
		// [TX END] order MANAGED → DETACHED; item vẫn TRANSIENT (chưa từng vào persistence context)

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
		sql.assertInserts("order_item", 0);

		// ── THEN: DB thật ──
		db.print(C0 + ".order_item");
		assertThat(db.countOf(C0 + ".purchase_order")).as("order được persist trực tiếp nên có 1 dòng").isEqualTo(1);
		assertThat(db.countOf(C0 + ".order_item"))
			.as("không cascade PERSIST thì item BỊ BỎ QUA IM LẶNG (không lỗi vì collection là inverse side, không có gì để ghi)")
			.isZero();
		assertThat(itemOne.getId()).as("item chưa từng được persist nên chưa có id").isNull();
	}

	/**
	 * G2-S02 — C1: {@code cascade = PERSIST} lan xuống item
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order", cascade = PERSIST)}.
	 * <p><b>Câu hỏi</b>: cùng thao tác như S01 nhưng có cascade PERSIST thì sao?
	 * <p><b>Kết luận</b>: {@code persist(order)} gọi persist cho từng item ngay lúc gọi. Cả 3 entity cùng TRANSIENT → MANAGED,
	 * lúc flush INSERT order trước rồi INSERT 2 item (cha trước con vì item cần FK order_id).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S02 | C1 cascade PERSIST: persist(order) INSERT order rồi cả 2 item")
	void s02_cascadePersistSavesItemsTogetherWithOrder() {
		// ── GIVEN: product đã có trong DB ──
		C1Product productPen = inTxReturning(() -> {
			C1Product product = new C1Product("Bút bi");
			em.persist(product);
			return product;
		});

		// ── WHEN: order có 2 item mới, chỉ persist order ──
		C1OrderItem itemOne = new C1OrderItem(productPen, 1);
		C1OrderItem itemTwo = new C1OrderItem(productPen, 2);
		sql.reset();
		inTx(() -> {
			C1PurchaseOrder orderA = new C1PurchaseOrder("A-001");
			orderA.addItem(itemOne);
			orderA.addItem(itemTwo);
			em.persist(orderA);
			// [LIFECYCLE] cascade PERSIST chạy NGAY trong persist(): cả item cũng thành MANAGED (có id), CHƯA có INSERT
			assertState(em, orderA, MANAGED);
			assertState(em, itemOne, MANAGED);
			assertState(em, itemTwo, MANAGED);
			// [FLUSH] lúc commit: INSERT purchase_order, rồi INSERT order_item ×2
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
		sql.assertInserts("order_item", 2);
		sql.assertInOrder("insert into " + C1 + ".purchase_order", "insert into " + C1 + ".order_item");

		// ── THEN: DB thật ──
		db.print(C1 + ".order_item");
		assertThat(db.countOf(C1 + ".order_item")).as("cascade PERSIST đã lưu cả 2 item").isEqualTo(2);
		assertThat(db.rowsOf(C1 + ".order_item").get(0).get("order_id"))
			.as("FK order_id được ghi vì addItem() đã gán OrderItem.order (owning side)")
			.isNotNull();
	}

	/**
	 * G2-S03 — C1: thêm item vào order đang MANAGED, không gọi persist cho item (persist-on-flush)
	 *
	 * <p>Mapping: như S02.
	 * <p><b>Câu hỏi</b>: order đã nằm trong DB và đang MANAGED; chỉ {@code addItem(new ...)} rồi commit, item có được lưu không?
	 * <p><b>Kết luận</b>: Có. Cascade PERSIST còn chạy một lần nữa LÚC FLUSH (persist-on-flush): Hibernate duyệt collection
	 * của mọi entity MANAGED và persist phần tử TRANSIENT. Không cần gọi {@code em.persist(item)}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S03 | C1 cascade PERSIST: addItem vào order MANAGED, item được INSERT lúc flush")
	void s03_cascadePersistRunsAgainAtFlushTime() {
		// ── GIVEN: order A-001 (chưa có item) và product đã có trong DB ──
		C1Product productPen = new C1Product("Bút bi");
		Long orderId = inTxReturning(() -> {
			C1PurchaseOrder orderA = new C1PurchaseOrder("A-001");
			em.persist(productPen);
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN: tải order (MANAGED), thêm item mới, KHÔNG gọi persist cho item ──
		C1OrderItem newItem = new C1OrderItem(productPen, 5);
		sql.reset();
		inTx(() -> {
			C1PurchaseOrder orderA = em.find(C1PurchaseOrder.class, orderId);
			orderA.addItem(newItem);
			// [LIFECYCLE] item mới vẫn TRANSIENT: chưa có ai gọi persist
			assertState(em, newItem, TRANSIENT);
			// [FLUSH] lúc commit: cascade PERSIST_ON_FLUSH persist item, rồi INSERT order_item
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("order_item", 1);
		sql.assertUpdates("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(C1 + ".order_item");
		assertThat(db.countOf(C1 + ".order_item"))
			.as("persist-on-flush: cascade PERSIST cũng chạy lúc flush nên item được lưu dù không gọi persist")
			.isEqualTo(1);
		assertThat(db.rowsOf(C1 + ".order_item").get(0).get("order_id"))
			.as("item trỏ về order A-001 qua FK order_id")
			.isEqualTo(orderId);
	}

	/**
	 * G2-S11a — C4-DEEP: cascade 3 tầng customer → order → item
	 *
	 * <p>Mapping: {@code Customer.orders(cascade = ALL)} và {@code PurchaseOrder.items(cascade = ALL)}.
	 * <p><b>Câu hỏi</b>: persist một customer có 2 order, mỗi order 2 item, cần gọi persist mấy lần?
	 * <p><b>Kết luận</b>: Một lần. Cascade đi liên tiếp qua 2 tầng: INSERT 1 customer + 2 order + 4 item,
	 * luôn cha trước con.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S11a | C4-DEEP cascade 3 tầng: persist(customer) INSERT 1 + 2 + 4 dòng, cha trước con")
	void s11a_cascadeChainsThroughThreeLevels() {
		// ── GIVEN: product đã có trong DB ──
		C4DeepProduct productPen = inTxReturning(() -> {
			C4DeepProduct product = new C4DeepProduct("Bút bi");
			em.persist(product);
			return product;
		});

		// ── WHEN: chỉ persist customer ──
		sql.reset();
		inTx(() -> {
			C4DeepCustomer customerAn = new C4DeepCustomer("An");
			C4DeepPurchaseOrder orderA1 = new C4DeepPurchaseOrder("A-001");
			C4DeepPurchaseOrder orderA2 = new C4DeepPurchaseOrder("A-002");
			orderA1.addItem(new C4DeepOrderItem(productPen, 1));
			orderA1.addItem(new C4DeepOrderItem(productPen, 2));
			orderA2.addItem(new C4DeepOrderItem(productPen, 3));
			orderA2.addItem(new C4DeepOrderItem(productPen, 4));
			customerAn.addOrder(orderA1);
			customerAn.addOrder(orderA2);
			em.persist(customerAn);
			// [LIFECYCLE] cascade đi 2 tầng nên order và item đều MANAGED
			assertState(em, orderA2, MANAGED);
			assertState(em, orderA2.getItems().get(1), MANAGED);
			// [FLUSH] lúc commit: INSERT customer, order ×2, item ×4 (cha trước con vì con cần FK)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("customer", 1);
		sql.assertInserts("purchase_order", 2);
		sql.assertInserts("order_item", 4);
		sql.assertInOrder("insert into " + DEEP + ".customer", "insert into " + DEEP + ".purchase_order",
			"insert into " + DEEP + ".order_item");

		// ── THEN: DB thật ──
		assertThat(db.countOf(DEEP + ".customer")).as("1 customer").isEqualTo(1);
		assertThat(db.countOf(DEEP + ".purchase_order")).as("2 order qua cascade tầng 1").isEqualTo(2);
		assertThat(db.countOf(DEEP + ".order_item")).as("4 item qua cascade tầng 2 (2 order × 2 item)").isEqualTo(4);
	}

	/**
	 * G2-S11b — C4: tắt cascade ở tầng customer → order thì dữ liệu thiếu đúng tại tầng đó
	 *
	 * <p>Mapping: {@code Customer.orders} KHÔNG cascade, {@code PurchaseOrder.items(cascade = ALL)}.
	 * <p><b>Câu hỏi</b>: persist customer có order trong collection nhưng tầng customer → order không cascade thì sao?
	 * <p><b>Kết luận</b>: chỉ INSERT customer. Order và item bị bỏ qua im lặng, y như S01 nhưng ở tầng trên. Dù
	 * {@code PurchaseOrder.items} có cascade ALL, order chưa bao giờ được persist nên cascade ở tầng dưới không có dịp chạy:
	 * cascade chỉ đi từ entity ĐƯỢC persist xuống, không "tự bắc cầu" qua một khâu thiếu cascade.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S11b | C4 thiếu cascade ở tầng customer → order: order và item không được lưu")
	void s11b_withoutCascadeAtTheTopLevelOrdersAreNotSaved() {
		// ── GIVEN: product đã có trong DB ──
		C4Product productPen = inTxReturning(() -> {
			C4Product product = new C4Product("Bút bi");
			em.persist(product);
			return product;
		});

		// ── WHEN: chỉ persist customer (order nằm trong customer.orders, tầng này không cascade) ──
		sql.reset();
		inTx(() -> {
			C4Customer customerAn = new C4Customer("An");
			C4PurchaseOrder orderA1 = new C4PurchaseOrder("A-001");
			orderA1.addItem(new C4OrderItem(productPen, 1));
			customerAn.addOrder(orderA1);
			em.persist(customerAn);
			// [LIFECYCLE] customer MANAGED; order KHÔNG được kéo theo (tầng này không cascade) nên vẫn TRANSIENT
			assertState(em, customerAn, MANAGED);
			assertState(em, orderA1, TRANSIENT);
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("customer", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C4 + ".customer")).as("customer được persist trực tiếp").isEqualTo(1);
		assertThat(db.countOf(C4 + ".purchase_order"))
			.as("tầng customer → order không cascade nên order bị bỏ qua im lặng (giống S01 nhưng ở tầng trên)")
			.isZero();
		assertThat(db.countOf(C4 + ".order_item"))
			.as("order không được lưu thì cascade ALL ở tầng order → item cũng không bao giờ được chạy tới")
			.isZero();
	}
}
