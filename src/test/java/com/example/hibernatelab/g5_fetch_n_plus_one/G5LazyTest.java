package com.example.hibernatelab.g5_fetch_n_plus_one;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1Customer;
import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1CustomerRepository;
import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1OrderItem;
import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1OrderItemRepository;
import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G5, biến thể F1 (mọi quan hệ LAZY tường minh): S03, S04, S05, S14, S16.
 *
 * <p>Câu hỏi xuyên suốt: LAZY nghĩa là gì thật sự (proxy, collection chưa khởi tạo), tốn SQL ở đâu, và chuyện gì
 * xảy ra khi truy cập ngoài transaction?
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G5LazyTest extends AbstractScenarioTest {

	private static final String F1 = "g5_f1_all_lazy";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private F1CustomerRepository customerRepository;

	@Autowired
	private F1OrderItemRepository itemRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { F1 };
	}

	/**
	 * G5-S03 — F1: proxy LAZY
	 *
	 * <p>Mapping: {@code PurchaseOrder.customer} là OWNING side, {@code fetch = LAZY} tường minh.
	 * <p><b>Câu hỏi</b>: {@code find(order)} xong thì {@code order.getCustomer()} là gì? Khi nào mới có SELECT customer?
	 * <p><b>Kết luận</b>: {@code find()} chỉ SELECT {@code purchase_order}, không join. {@code getCustomer()} trả về một PROXY
	 * (object giả mang sẵn id, lấy từ cột FK). {@code getId()} trên proxy không tải gì (Hibernate đã biết id).
	 * Chỉ khi đọc thuộc tính khác như {@code getName()} thì proxy mới chạy SELECT customer và trở thành "đã khởi tạo".
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S03 | F1: customer LAZY là proxy, getId() không SELECT, getName() mới SELECT customer")
	void s03_lazyManyToOneIsProxyUntilANonIdPropertyIsRead() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);
		Long orderId = G5Fixtures.firstOrderId();

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			F1PurchaseOrder orderA = em.find(F1PurchaseOrder.class, orderId);
			// [LIFECYCLE] order MANAGED; customer là proxy chưa khởi tạo
			assertState(em, orderA, MANAGED);
			assertThat(Hibernate.isInitialized(orderA.getCustomer()))
				.as("@ManyToOne LAZY: find(order) không tải customer, chỉ gắn proxy")
				.isFalse();
			sql.assertTotal(1);
			sql.assertNoStatementContains("join");

			Long customerId = orderA.getCustomer().getId();
			assertThat(customerId).as("id có sẵn trong cột FK của order nên proxy trả về được").isNotNull();
			assertThat(Hibernate.isInitialized(orderA.getCustomer()))
				.as("getId() trên proxy KHÔNG khởi tạo proxy (mặc định Hibernate)")
				.isFalse();
			sql.assertTotal(1);

			String customerName = orderA.getCustomer().getName();
			assertThat(customerName).as("order 11 thuộc Customer-1").isEqualTo("Customer-1");
			// [LIFECYCLE] getName() buộc proxy chạy SELECT, từ đây customer đã khởi tạo
			assertThat(Hibernate.isInitialized(orderA.getCustomer())).as("getName() đã khởi tạo proxy").isTrue();
			// [FLUSH] không có thay đổi
			// [TX END] commit
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2);
		sql.assertSelects("purchase_order", 1);
		sql.assertSelects("customer", 1);

		// ── THEN: DB thật ──
		db.print(F1 + ".customer");
		assertThat(db.countOf(F1 + ".customer")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.CUSTOMER_COUNT);
	}

	/**
	 * G5-S04 — F1: {@code LazyInitializationException}
	 *
	 * <p>Mapping: như S03; {@code PurchaseOrder.items} cũng LAZY.
	 * <p><b>Câu hỏi</b>: tải order trong transaction, hết transaction rồi mới chạm vào {@code items} và {@code customer} thì sao?
	 * <p><b>Kết luận</b>: cả hai ném {@link LazyInitializationException}. Order đã DETACHED, session đã đóng nên proxy và collection
	 * (chưa khởi tạo) không còn đường nào chạy SELECT. {@code spring.jpa.open-in-view} chỉ ảnh hưởng web request nên không liên quan
	 * ở đây; {@code hibernate.enable_lazy_load_no_trans} sẽ che lỗi này nhưng là anti-pattern (mỗi lần chạm một transaction riêng) nên không bật.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S04 | F1: truy cập items và customer LAZY sau khi hết transaction → LazyInitializationException")
	void s04_touchingLazyAssociationsAfterTransactionThrows() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);
		Long orderId = G5Fixtures.firstOrderId();

		// ── WHEN: tx tải order rồi đóng; ngoài tx mới chạm vào quan hệ LAZY ──
		F1PurchaseOrder detachedOrder = inTxReturning(() -> em.find(F1PurchaseOrder.class, orderId));
		// [LIFECYCLE] hết tx → order DETACHED; items và customer chưa từng được khởi tạo
		assertState(em, detachedOrder, DETACHED);
		assertThat(Hibernate.isInitialized(detachedOrder.getItems())).as("items chưa khởi tạo").isFalse();
		assertThat(Hibernate.isInitialized(detachedOrder.getCustomer())).as("customer là proxy chưa khởi tạo").isFalse();
		sql.reset();

		// ── THEN: exception, và không có câu SQL nào chạy ──
		assertThatThrownBy(() -> detachedOrder.getItems().size())
			.as("collection LAZY chưa khởi tạo + session đã đóng → không thể tải")
			.isInstanceOf(LazyInitializationException.class);
		assertThatThrownBy(() -> detachedOrder.getCustomer().getName())
			.as("proxy LAZY chưa khởi tạo + session đã đóng → không thể tải")
			.isInstanceOf(LazyInitializationException.class);
		sql.print();
		sql.assertTotal(0);

		// ── THEN: DB thật ── (dữ liệu vẫn còn nguyên; lỗi nằm ở phía ứng dụng, không phải DB)
		db.print(F1 + ".order_item");
		assertThat(db.countOf(F1 + ".order_item")).as("DB đầy đủ 20 item, chỉ là session không còn để đọc").isEqualTo(G5Fixtures.ITEM_COUNT);
	}

	/**
	 * G5-S05 — F1: N+1 trên collection
	 *
	 * <p>Mapping: {@code Customer.orders} là INVERSE side, LAZY.
	 * <p><b>Câu hỏi</b>: lấy mọi customer rồi lặp {@code c.getOrders().size()} thì có bao nhiêu SELECT?
	 * <p><b>Kết luận</b>: 1 + 5. 1 SELECT customer, rồi mỗi lần chạm collection của một customer lại có 1 SELECT purchase_order riêng.
	 * LAZY tránh tải thừa nhưng KHÔNG tránh được N+1 khi ta lặp. Số câu SQL tăng theo số customer.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S05 | F1: findAll customers rồi lặp getOrders().size() → 1 + 5 SELECT (N+1 trên collection)")
	void s05_loopingOverLazyCollectionsCausesNPlusOne() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);

		// ── WHEN: lấy mọi customer, lặp qua orders của từng người ──
		sql.reset();
		inTx(() -> {
			List<F1Customer> customers = customerRepository.findAll();
			int totalOrders = 0;
			for (F1Customer customer : customers) {
				totalOrders += customer.getOrders().size(); // mỗi lần chạm: 1 SELECT purchase_order
			}
			// [LIFECYCLE] mọi customer MANAGED; tổng số order đọc được khớp seed
			assertState(em, customers.get(0), MANAGED);
			assertThat(totalOrders).as("5 customer × 2 order").isEqualTo(G5Fixtures.ORDER_COUNT);
			// [FLUSH] không có thay đổi
			// [TX END] commit
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 1);
		sql.assertSelects("purchase_order", G5Fixtures.CUSTOMER_COUNT);
		sql.assertTotal(1 + G5Fixtures.CUSTOMER_COUNT);

		// ── THEN: DB thật ──
		db.print(F1 + ".purchase_order");
		assertThat(db.countOf(F1 + ".purchase_order")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.ORDER_COUNT);
	}

	/**
	 * G5-S14 — F1: {@code Hibernate.initialize()} trong transaction và ngoài transaction
	 *
	 * <p>Mapping: {@code Customer.orders} LAZY.
	 * <p><b>Câu hỏi</b>: {@code Hibernate.initialize(collection)} giúp được gì, và có cứu được lúc đã ngoài transaction không?
	 * <p><b>Kết luận</b>: trong tx thì collection được tải (1 SELECT) và dùng được cả sau khi customer DETACHED. Ngoài tx thì
	 * {@code initialize} cũng ném {@link LazyInitializationException}, vì nó chỉ là cách "chạm" collection có chủ ý, không phải phép màu.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S14 | F1: Hibernate.initialize() trong tx dùng được sau khi D; ngoài tx thì LazyInitializationException")
	void s14_hibernateInitializeWorksOnlyInsideTransaction() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);
		Long customerId = G5Fixtures.customerWithPaymentsId();

		// ── WHEN (a): initialize TRONG tx ──
		sql.reset();
		F1Customer initializedCustomer = inTxReturning(() -> {
			F1Customer customerAn = em.find(F1Customer.class, customerId);
			Hibernate.initialize(customerAn.getOrders());
			return customerAn;
		});
		// [LIFECYCLE] hết tx → DETACHED, nhưng orders đã được tải nên vẫn đọc được
		assertState(em, initializedCustomer, DETACHED);
		assertThat(initializedCustomer.getOrders()).as("orders đã khởi tạo trong tx nên đọc được sau khi D").hasSize(2);
		sql.print();
		sql.assertSelects("purchase_order", 1);

		// ── WHEN (b): initialize NGOÀI tx ──
		F1Customer uninitializedCustomer = inTxReturning(() -> em.find(F1Customer.class, customerId));

		// ── THEN ──
		assertThatThrownBy(() -> Hibernate.initialize(uninitializedCustomer.getOrders()))
			.as("session đã đóng nên initialize() cũng không tải được")
			.isInstanceOf(LazyInitializationException.class);
		db.print(F1 + ".purchase_order");
		assertThat(db.countOf(F1 + ".purchase_order")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.ORDER_COUNT);
	}

	/**
	 * G5-S16 — F1: N+1 qua {@code @ManyToOne} LAZY khi lặp items
	 *
	 * <p>Mapping: {@code OrderItem.product} là OWNING side, LAZY.
	 * <p><b>Câu hỏi</b>: lặp 20 item và đọc {@code item.getProduct().getName()} thì có bao nhiêu SELECT? Sửa thế nào?
	 * <p><b>Kết luận</b>: 1 SELECT item + 3 SELECT product (mỗi product KHÁC NHAU 1 lần, lần sau lấy từ persistence context),
	 * KHÔNG phải 20. Số SELECT phụ = số phần tử khác nhau của quan hệ. Sửa bằng {@code join fetch i.product}: chỉ còn 1 SELECT.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S16 | F1: lặp items đọc getProduct().getName() → 1 + 3 SELECT; join fetch i.product → 1 SELECT")
	void s16_loopingOverLazyManyToOneCausesNPlusOneAndJoinFetchFixesIt() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);

		// ── WHEN (a): findAll rồi đọc product từng item ──
		sql.reset();
		inTx(() -> {
			List<F1OrderItem> items = itemRepository.findAll();
			for (F1OrderItem item : items) {
				item.getProduct().getName(); // chạm proxy: SELECT product lần đầu của mỗi product khác nhau
			}
			// [LIFECYCLE] 20 item MANAGED; 3 product MANAGED (đã khởi tạo)
			assertState(em, items.get(0), MANAGED);
			assertThat(items).as("seed có 20 item").hasSize(G5Fixtures.ITEM_COUNT);
		});
		sql.print();
		sql.assertSelects("order_item", 1);
		sql.assertSelects("product", G5Fixtures.PRODUCT_COUNT);
		sql.assertTotal(1 + G5Fixtures.PRODUCT_COUNT);

		// ── WHEN (b): sửa bằng join fetch ──
		sql.reset();
		inTx(() -> {
			List<F1OrderItem> items = itemRepository.findAllWithProductJoinFetch();
			for (F1OrderItem item : items) {
				item.getProduct().getName(); // product đã có sẵn, không SELECT thêm
			}
			assertThat(items).as("vẫn đủ 20 item").hasSize(G5Fixtures.ITEM_COUNT);
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("join");

		// ── THEN: DB thật ──
		db.print(F1 + ".product");
		assertThat(db.countOf(F1 + ".product")).as("DB có 3 product, khớp 3 SELECT lẻ ở (a)").isEqualTo(G5Fixtures.PRODUCT_COUNT);
	}
}
