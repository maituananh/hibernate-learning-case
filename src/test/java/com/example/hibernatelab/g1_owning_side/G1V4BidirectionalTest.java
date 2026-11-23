package com.example.hibernatelab.g1_owning_side;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.bootfail.g1s17.BrokenMappedByCustomer;
import com.example.hibernatelab.g1_owning_side.v4_bidi_mappedby.Customer;
import com.example.hibernatelab.g1_owning_side.v4_bidi_mappedby.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.MappingBootstrap;

/**
 * Biến thể V4: hai chiều ĐÚNG CHUẨN. {@code PurchaseOrder.customer} là OWNING side (ghi FK),
 * {@code Customer.orders(mappedBy = "customer")} là INVERSE side (chỉ để điều hướng).
 * Gồm S12 (chỉ set inverse), S13 (chỉ set owning), S14 (helper đồng bộ hai phía), S17 ([BOOT-FAIL] mappedBy sai).
 *
 * <p>Ở các test này ta cố ý đọc trạng thái collection TRONG BỘ NHỚ (qua entity), vì chính sự lệch giữa bộ nhớ
 * và DB là điều cần học. Dữ liệu trong DB vẫn luôn được assert bằng JDBC.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G1V4BidirectionalTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g1_v4_bidi_mappedby";
	private static final String PURCHASE_ORDER = SCHEMA + ".purchase_order";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/**
	 * G1-S12 — V4: chỉ set phía INVERSE ({@code mappedBy}) → FK = NULL
	 *
	 * <p>Mapping: {@code PurchaseOrder.customer} OWNING side; {@code Customer.orders(mappedBy = "customer")} INVERSE side.
	 * <p><b>Câu hỏi</b>: {@code customer.getOrders().add(order)} rồi {@code persist(order)}, KHÔNG gọi
	 * {@code order.setCustomer(...)}: FK có được ghi không?
	 * <p><b>Kết luận</b>: Không. Hibernate chỉ ghi FK theo owning side ({@code order.customer}), mà nó vẫn null.
	 * Collection {@code mappedBy} bị BỎ QUA khi ghi. INSERT order với {@code customer_id = NULL}, code không báo lỗi gì.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S12 | V4 chỉ add vào collection (inverse), không setCustomer: customer_id = NULL")
	void s12_settingOnlyTheInverseSideDoesNotWriteTheForeignKey() {
		// ── GIVEN: customer An đã có trong DB ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN: chỉ add order vào collection của customer, KHÔNG gọi order.setCustomer ──
		sql.reset();
		PurchaseOrder orderA1 = inTxReturning(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			PurchaseOrder order = new PurchaseOrder("A-001");
			customerAn.getOrders().add(order); // chỉ đổi phía inverse (bộ nhớ)
			em.persist(order);
			// [LIFECYCLE] cả customerAn và order đều MANAGED
			assertState(em, customerAn, MANAGED);
			assertState(em, order, MANAGED);
			return order;
			// [FLUSH] lúc commit: INSERT purchase_order với customer_id = NULL (order.customer vẫn null)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("purchase_order", 1);

		// ── THEN: DB thật ──
		db.print(PURCHASE_ORDER);
		assertThat(db.rowById(PURCHASE_ORDER, orderA1.getId()).get("customer_id"))
			.as("mappedBy là INVERSE side: Hibernate bỏ qua nó khi ghi, và order.customer chưa được gán nên FK = NULL")
			.isNull();
	}

	/**
	 * G1-S13 — V4: chỉ set phía OWNING → DB đúng nhưng collection trong bộ nhớ bị LỆCH
	 *
	 * <p>Mapping: như S12.
	 * <p><b>Câu hỏi</b>: Chỉ gọi {@code order.setCustomer(customer)} (không đụng collection) thì DB và bộ nhớ ra sao?
	 * <p><b>Kết luận</b>: DB đúng (FK có giá trị). Nhưng trong CÙNG persistence context, collection
	 * {@code customer.getOrders()} vẫn KHÔNG chứa order mới (Hibernate không tự đồng bộ). Chỉ sau khi
	 * {@code em.clear()} và tải lại mới thấy. Vì vậy phải dùng helper đồng bộ cả hai phía (S14).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S13 | V4 chỉ setCustomer (owning): DB đúng nhưng customer.getOrders() trong bộ nhớ vẫn thiếu")
	void s13_settingOnlyTheOwningSideLeavesTheInMemoryCollectionStale() {
		// ── GIVEN: customer An đã có trong DB ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN: trong một transaction, chỉ gán order.customer rồi flush ──
		sql.reset();
		inTx(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			assertThat(customerAn.getOrders()).as("điều kiện đầu: collection được khởi tạo, đang rỗng").isEmpty();

			PurchaseOrder orderA1 = new PurchaseOrder("A-001");
			orderA1.setCustomer(customerAn); // chỉ đổi phía owning
			em.persist(orderA1);
			em.flush();
			// [FLUSH] INSERT purchase_order với customer_id = An. Entity vẫn MANAGED, bộ nhớ chưa đổi

			// ── THEN (trong tx): DB đúng, bộ nhớ lệch ──
			assertThat(db.countOf(PURCHASE_ORDER))
				.as("dòng order đã được INSERT (cùng connection thấy dữ liệu đã flush)")
				.isEqualTo(1);
			assertThat(db.rowById(PURCHASE_ORDER, orderA1.getId()).get("customer_id"))
				.as("DB đúng: owning side có giá trị nên FK được ghi")
				.isEqualTo(customerId);
			assertThat(customerAn.getOrders())
				.as("bộ nhớ LỆCH: Hibernate không tự thêm order vào collection inverse trong cùng persistence context")
				.isEmpty();

			em.clear(); // bỏ cache cấp 1 → lần sau phải đọc lại từ DB
			Customer customerReloaded = em.find(Customer.class, customerId);
			assertThat(customerReloaded.getOrders())
				.as("sau em.clear() tải lại từ DB thì collection mới thấy order (vì FK trong DB đã đúng)")
				.hasSize(1);
		});
		// [TX END] persistence context đóng

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
	}

	/**
	 * G1-S14a — V4: helper {@code addOrder()} đồng bộ cả hai phía
	 *
	 * <p>Mapping: như S12; {@code Customer.addOrder()} vừa add vào collection (inverse) vừa gán {@code order.customer} (owning).
	 * <p><b>Câu hỏi</b>: Dùng helper thì FK và bộ nhớ có khớp nhau không?
	 * <p><b>Kết luận</b>: Khớp. INSERT order với FK đúng (1 câu, không UPDATE bù) và collection trong bộ nhớ chứa order.
	 * Đây là pattern khuyến nghị cho quan hệ hai chiều; các nhóm sau đều dùng nó.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S14a | V4 helper addOrder(): FK đúng, bộ nhớ khớp, 1 INSERT là đủ")
	void s14a_addOrderHelperSynchronizesBothSides() {
		// ── GIVEN: customer An đã có trong DB ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN: thêm order bằng helper addOrder ──
		sql.reset();
		PurchaseOrder orderA1 = inTxReturning(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			PurchaseOrder order = new PurchaseOrder("A-001");
			customerAn.addOrder(order); // add vào collection VÀ gán order.customer
			em.persist(order);
			// [LIFECYCLE] order: TRANSIENT → MANAGED
			assertState(em, order, MANAGED);
			assertThat(customerAn.getOrders()).as("bộ nhớ: collection đã chứa order mới nhờ helper").contains(order);
			assertThat(order.getCustomer()).as("bộ nhớ: order trỏ ngược về customer nhờ helper").isSameAs(customerAn);
			return order;
			// [FLUSH] lúc commit: INSERT purchase_order với customer_id = An
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
		sql.assertUpdates("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(PURCHASE_ORDER);
		assertThat(db.rowById(PURCHASE_ORDER, orderA1.getId()).get("customer_id"))
			.as("helper gán order.customer (owning side) nên FK được ghi đúng ngay trong INSERT")
			.isEqualTo(customerId);
	}

	/**
	 * G1-S14b — V4: helper {@code removeOrder()} gỡ cả hai phía, KHÔNG xoá dòng order
	 *
	 * <p>Mapping: như S14a, không cascade, không orphanRemoval.
	 * <p><b>Câu hỏi</b>: Gỡ order khỏi customer bằng helper thì order có bị xoá khỏi DB không?
	 * <p><b>Kết luận</b>: Không. Helper đặt {@code order.customer = null} nên Hibernate chỉ chạy
	 * {@code UPDATE purchase_order set customer_id = NULL}. Dòng order vẫn còn (muốn xoá hẳn: orphanRemoval, nhóm G3).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S14b | V4 helper removeOrder(): UPDATE customer_id = NULL, dòng order vẫn còn")
	void s14b_removeOrderHelperClearsForeignKeyButKeepsTheRow() {
		// ── GIVEN: An có 1 order (tạo bằng helper) ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			PurchaseOrder orderA1 = new PurchaseOrder("A-001");
			customerAn.addOrder(orderA1);
			em.persist(orderA1);
			return customerAn.getId();
		});

		// ── WHEN: tải customer rồi gỡ order bằng helper ──
		sql.reset();
		inTx(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			PurchaseOrder orderA1 = customerAn.getOrders().get(0); // chạm vào collection LAZY → SELECT order
			customerAn.removeOrder(orderA1);
			assertThat(customerAn.getOrders()).as("bộ nhớ: collection đã bỏ order").isEmpty();
			assertThat(orderA1.getCustomer()).as("bộ nhớ: order không còn trỏ về customer").isNull();
			// [FLUSH] lúc commit: UPDATE purchase_order set customer_id = NULL (dirty checking trên owning side)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("purchase_order", 1);
		sql.assertDeletes("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(PURCHASE_ORDER);
		assertThat(db.countOf(PURCHASE_ORDER)).as("không có orphanRemoval nên dòng order vẫn còn").isEqualTo(1);
		assertThat(db.rowsOf(PURCHASE_ORDER).get(0).get("customer_id"))
			.as("FK được đặt về NULL vì order.customer (owning side) đã bị gỡ")
			.isNull();
	}

	/**
	 * G1-S17 — [BOOT-FAIL] {@code mappedBy} trỏ sai tên field
	 *
	 * <p>Mapping: entity sai {@code bootfail/g1s17/BrokenMappedByCustomer} với {@code mappedBy = "khachHang"},
	 * trong khi field thật ở {@code PurchaseOrder} tên là {@code customer}.
	 * <p><b>Câu hỏi</b>: Gõ sai tên property trong {@code mappedBy} thì chuyện gì xảy ra?
	 * <p><b>Kết luận</b>: Hibernate không khởi động được và thông báo lỗi nêu tên property không tồn tại,
	 * nên lỗi này bị bắt ngay lúc chạy app, không âm thầm ghi sai dữ liệu như S12.
	 * <p>Xác minh: Hibernate 7.4.15.Final. Nguyên văn thông báo xem docs/RESULTS.md.
	 */
	@Test
	@DisplayName("G1-S17 | [BOOT-FAIL] mappedBy trỏ sai tên field: Hibernate không khởi động")
	void s17_bootFailWhenMappedByNamesAPropertyThatDoesNotExist() {
		// ── WHEN: dựng SessionFactory riêng chỉ gồm entity sai và các entity nó trỏ tới ──
		Throwable failure = MappingBootstrap.tryBuild(BrokenMappedByCustomer.class, PurchaseOrder.class, Customer.class);

		// ── THEN ──
		System.out.println("[BOOT-FAIL] " + (failure == null ? "(không lỗi)" : failure.getClass().getName() + ": " + failure.getMessage()));
		assertThat(failure).as("mappedBy sai thì SessionFactory phải khởi động thất bại").isNotNull();
		assertThat(failure.getMessage())
			.as("thông báo lỗi phải nêu tên property sai (khachHang) để người học biết sửa ở đâu")
			.contains("khachHang");
	}
}
