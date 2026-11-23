package com.example.hibernatelab.g1_owning_side;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.TRANSIENT;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.hibernate.TransientPropertyValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.hibernatelab.bootfail.g1s06.DuplicateColumnOrder;
import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.Address;
import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.Customer;
import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.DualMappedOrder;
import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.OrderItem;
import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.Product;
import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.PurchaseOrder;
import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.G1V1PurchaseOrderRepository;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.MappingBootstrap;
import com.example.hibernatelab.support.SchemaInspector.ForeignKey;

/**
 * Biến thể V1 (baseline của G1): {@code PurchaseOrder} có {@code @ManyToOne customer}, Customer KHÔNG có collection.
 * Gồm S01 (DDL), S02 (persist trỏ tới cha đã có), S03 (cha TRANSIENT), S05 (hai FK cùng bảng),
 * S06 (map cột FK hai lần), S18 (query ngược thay cho collection).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G1V1ManyToOneTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g1_v1_m2o_uni";
	private static final String CUSTOMER = SCHEMA + ".customer";
	private static final String PURCHASE_ORDER = SCHEMA + ".purchase_order";
	private static final String ORDER_ITEM = SCHEMA + ".order_item";
	private static final String DUAL_ORDER = SCHEMA + ".order_dual_mapping";

	@Autowired
	private G1V1PurchaseOrderRepository purchaseOrderRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/**
	 * G1-S01 — DDL của {@code @ManyToOne}: cột FK nằm ở bảng CON, cho phép NULL, có constraint
	 *
	 * <p>Mapping: {@code PurchaseOrder.customer} là OWNING side ({@code @ManyToOne @JoinColumn(name = "customer_id")});
	 * {@code Customer} không có collection nên không có INVERSE side.
	 * <p><b>Câu hỏi</b>: Hibernate sinh ra bảng và cột như thế nào cho một {@code @ManyToOne} một chiều?
	 * <p><b>Kết luận</b>:
	 * <ol>
	 *   <li>Cột FK {@code customer_id} nằm ở bảng {@code purchase_order} (bảng con), KHÔNG nằm ở {@code customer}.</li>
	 *   <li>Mặc định cột cho phép NULL (không ghi {@code nullable}/{@code optional} thì order có thể không có customer).</li>
	 *   <li>Có FK constraint trỏ tới {@code customer(id)}, tên đúng như đã đặt trong {@code @ForeignKey}.</li>
	 * </ol>
	 * Xác minh: Hibernate 7.4.15.Final (do Spring Boot 4.2.0 quản lý)
	 */
	@Test
	@DisplayName("G1-S01 | DDL @ManyToOne: customer_id ở bảng con, nullable, có FK constraint")
	void s01_manyToOneCreatesNullableForeignKeyInChildTable() {
		// ── GIVEN: không cần dữ liệu, DDL do Hibernate sinh lúc khởi động (create-drop) ──

		// ── THEN: DDL ──
		schema.printDdl(SCHEMA);

		assertThat(schema.columnNamed(SCHEMA, "purchase_order", "customer_id").nullable())
			.as("không ghi nullable/optional → mặc định cho phép NULL, order có thể chưa có customer")
			.isTrue();

		ForeignKey foreignKey = schema.foreignKeyOnColumn(SCHEMA, "purchase_order", "customer_id");
		assertThat(foreignKey.targetTable()).as("FK phải trỏ tới bảng customer").isEqualTo(CUSTOMER);
		assertThat(foreignKey.targetColumn()).as("FK trỏ tới khoá chính của customer").isEqualTo("id");
		assertThat(foreignKey.constraintName())
			.as("@ForeignKey(name = ...) đặt tên constraint tường minh (không đặt thì Hibernate sinh tên dạng fk + chuỗi băm)")
			.isEqualTo("fk_order_customer");

		assertThat(schema.hasColumn(SCHEMA, "customer", "customer_id"))
			.as("bảng cha không chứa FK: FK luôn nằm ở phía @ManyToOne (bảng con)")
			.isFalse();
	}

	/**
	 * G1-S02 — Persist con trỏ tới cha ĐÃ CÓ trong DB bằng {@code getReference}: không SELECT cha
	 *
	 * <p>Mapping: {@code PurchaseOrder.customer} OWNING side; {@code OrderItem.order} và {@code OrderItem.product} OWNING side.
	 * <p><b>Câu hỏi</b>: Muốn tạo order cho customer đã có trong DB, có phải tải customer lên (SELECT) không?
	 * <p><b>Kết luận</b>: Không cần. {@code getReference} trả về proxy chưa khởi tạo (chỉ chứa id), đủ để Hibernate
	 * ghi FK. Chỉ có 1 INSERT order và 2 INSERT item, 0 SELECT customer/product.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S02 | Persist order trỏ tới customer đã có bằng getReference: không SELECT customer")
	void s02_persistChildPointingToExistingParentNeedsNoSelect() {
		// ── GIVEN: tx1 lưu customer và product (xong tx1 cả hai là DETACHED, chỉ giữ lại id) ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			return customerAn.getId();
		});
		Long productId = inTxReturning(() -> {
			Product keyboard = new Product("Bàn phím");
			em.persist(keyboard);
			return keyboard.getId();
		});

		// ── WHEN: tx2 tạo order + 2 item, chỉ dùng reference của customer và product ──
		sql.reset();
		PurchaseOrder orderA = inTxReturning(() -> {
			Customer customerRef = em.getReference(Customer.class, customerId);
			Product productRef = em.getReference(Product.class, productId);
			// [LIFECYCLE] customerRef: proxy MANAGED nhưng CHƯA khởi tạo (chưa SELECT)
			assertState(em, customerRef, MANAGED);
			assertThat(Hibernate.isInitialized(customerRef)).as("getReference chỉ tạo proxy chứa id, chưa SELECT").isFalse();

			PurchaseOrder order = new PurchaseOrder("A-001");
			// [LIFECYCLE] order: TRANSIENT (mới new)
			assertState(em, order, TRANSIENT);
			order.setCustomer(customerRef);
			em.persist(order);
			// [LIFECYCLE] order: TRANSIENT → MANAGED (id từ sequence, INSERT còn hoãn tới flush)
			assertState(em, order, MANAGED);

			em.persist(new OrderItem(order, productRef, 1));
			em.persist(new OrderItem(order, productRef, 2));
			assertThat(Hibernate.isInitialized(customerRef)).as("ghi FK chỉ cần id, không cần đọc customer").isFalse();
			return order;
			// [FLUSH] lúc commit: INSERT purchase_order, rồi 2 INSERT order_item
		});
		// [TX END] persistence context đóng → order: MANAGED → DETACHED

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 0);
		sql.assertSelects("product", 0);
		sql.assertInserts("purchase_order", 1);
		sql.assertInserts("order_item", 2);

		// ── THEN: DB thật ──
		db.print(PURCHASE_ORDER);
		db.print(ORDER_ITEM);
		assertThat(db.rowById(PURCHASE_ORDER, orderA.getId()).get("customer_id"))
			.as("FK customer_id được ghi từ id của proxy, dù customer không hề được SELECT")
			.isEqualTo(customerId);
		assertThat(db.countOf(ORDER_ITEM)).as("cả 2 item đã được lưu").isEqualTo(2);
	}

	/**
	 * G1-S03 — Con trỏ tới cha TRANSIENT, không cascade: lỗi lúc flush
	 *
	 * <p>Mapping: {@code PurchaseOrder.customer} OWNING side, cố ý KHÔNG có cascade.
	 * <p><b>Câu hỏi</b>: Persist order trỏ tới một customer mới {@code new} (chưa persist) thì chuyện gì xảy ra?
	 * <p><b>Kết luận</b>: {@code persist(order)} êm, nhưng lúc flush Hibernate phát hiện FK trỏ tới entity chưa
	 * được lưu và ném {@code TransientPropertyValueException}. Cả transaction rollback nên DB vẫn trống.
	 * Lỗi là cách Hibernate nói: "không có cascade thì bạn phải tự persist cha trước".
	 * <p>Xác minh: Hibernate 7.4.15.Final. Kiểu exception bên ngoài xem docs/RESULTS.md.
	 */
	@Test
	@DisplayName("G1-S03 | Order trỏ tới customer TRANSIENT (không cascade): lỗi lúc flush, DB trống")
	void s03_pointingToTransientParentFailsAtFlush() {
		// ── GIVEN: DB trống (DbCleaner đã dọn) ──
		assertThat(db.countOf(CUSTOMER)).as("điều kiện đầu: chưa có customer").isZero();

		// ── WHEN: persist order trỏ tới customer chưa persist, rồi commit ──
		Throwable failure = catchThrowable(() -> inTx(() -> {
			Customer customerNew = new Customer("Chưa lưu");
			PurchaseOrder order = new PurchaseOrder("A-001");
			order.setCustomer(customerNew);
			em.persist(order);
			// [LIFECYCLE] order: TRANSIENT → MANAGED; customerNew vẫn TRANSIENT (không cascade nên không ai persist nó)
			assertState(em, order, MANAGED);
			assertState(em, customerNew, TRANSIENT);
			// [FLUSH] lúc commit: Hibernate kiểm tra FK, thấy customerNew là TRANSIENT → ném lỗi, rollback
		}));

		// ── THEN: lỗi ──
		System.out.println("[EXCEPTION] " + failure.getClass().getName() + ": " + failure.getMessage());
		assertThat(failure).as("flush phải thất bại vì FK trỏ tới entity chưa lưu").isNotNull();
		assertThat(failure).hasRootCauseInstanceOf(TransientPropertyValueException.class);
		assertThat(failure.getMessage())
			.as("thông báo của Hibernate nói rõ entity chưa lưu")
			.contains("transient instance");

		// ── THEN: DB thật ──
		sql.print();
		assertThat(db.countOf(PURCHASE_ORDER)).as("transaction rollback nên không có order").isZero();
		assertThat(db.countOf(CUSTOMER)).as("customer chưa từng được persist").isZero();
	}

	/**
	 * G1-S05 — Hai FK cùng trỏ tới một bảng: tên cột suy ra từ tên FIELD
	 *
	 * <p>Mapping: {@code PurchaseOrder.billingAddress} và {@code shippingAddress}, cả hai {@code @ManyToOne Address},
	 * cố ý KHÔNG ghi {@code @JoinColumn}. Cả hai là OWNING side.
	 * <p><b>Câu hỏi</b>: Hai quan hệ cùng trỏ tới bảng {@code address} thì cột FK tên gì, có xung đột không?
	 * <p><b>Kết luận</b>: Không xung đột. Tên cột mặc định = {@code <tên field>_<tên cột PK>}, nên ra
	 * {@code billing_address_id} và {@code shipping_address_id}, mỗi cột một FK riêng với tên constraint khác nhau.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S05 | Hai @ManyToOne tới cùng bảng address: 2 cột FK đặt tên theo field")
	void s05_twoForeignKeysToSameTableAreNamedAfterTheFields() {
		// ── GIVEN: không cần dữ liệu, chỉ xem DDL ──

		// ── THEN: DDL ──
		schema.printDdl(SCHEMA);
		ForeignKey billingForeignKey = schema.foreignKeyOnColumn(SCHEMA, "purchase_order", "billing_address_id");
		ForeignKey shippingForeignKey = schema.foreignKeyOnColumn(SCHEMA, "purchase_order", "shipping_address_id");

		assertThat(billingForeignKey.targetTable())
			.as("billing_address_id trỏ tới bảng address")
			.isEqualTo(SCHEMA + ".address");
		assertThat(shippingForeignKey.targetTable())
			.as("shipping_address_id cũng trỏ tới bảng address")
			.isEqualTo(SCHEMA + ".address");
		assertThat(billingForeignKey.constraintName())
			.as("mỗi cột có một FK constraint riêng, tên khác nhau (Hibernate sinh tên mặc định)")
			.isNotEqualTo(shippingForeignKey.constraintName());
		assertThat(schema.hasColumn(SCHEMA, "purchase_order", "address_id"))
			.as("tên cột lấy từ tên field (billingAddress → billing_address_id), không phải từ tên bảng đích")
			.isFalse();
	}

	/**
	 * G1-S06a — Map cột FK hai lần: chỉ set {@code Long customerId} (phía được quyền ghi)
	 *
	 * <p>Mapping: {@code DualMappedOrder.customerId} (ghi được) và {@code DualMappedOrder.customer}
	 * ({@code insertable = false, updatable = false}, chỉ đọc), cả hai trỏ cùng cột {@code customer_id}.
	 * <p><b>Câu hỏi</b>: Cột {@code customer_id} được map bởi 2 property, vậy property nào ghi FK?
	 * <p><b>Kết luận</b>: Property có quyền ghi ({@code customerId}). Set nó thì FK xuống DB đúng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S06a | Cột FK map 2 lần: set Long customerId → FK được ghi")
	void s06a_settingTheWritableLongColumnWritesTheForeignKey() {
		// ── GIVEN: một customer đã có trong DB ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN: persist order chỉ set customerId (không set field customer) ──
		sql.reset();
		DualMappedOrder orderA = inTxReturning(() -> {
			DualMappedOrder order = new DualMappedOrder("A-001");
			order.setCustomerId(customerId);
			em.persist(order);
			// [LIFECYCLE] order: TRANSIENT → MANAGED
			assertState(em, order, MANAGED);
			return order;
			// [FLUSH] lúc commit: INSERT order_dual_mapping với customer_id lấy từ property customerId
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertInserts("order_dual_mapping", 1);
		db.print(DUAL_ORDER);
		assertThat(db.rowById(DUAL_ORDER, orderA.getId()).get("customer_id"))
			.as("customerId có quyền ghi nên là nguồn của FK")
			.isEqualTo(customerId);
	}

	/**
	 * G1-S06b — Map cột FK hai lần: chỉ set field {@code customer} (phía bị tắt quyền ghi)
	 *
	 * <p>Mapping: như S06a.
	 * <p><b>Câu hỏi</b>: Nếu chỉ gán entity {@code customer} (cách quen thuộc) thì FK có được ghi không?
	 * <p><b>Kết luận</b>: KHÔNG. {@code insertable = false} khiến Hibernate bỏ qua field này khi INSERT, nên
	 * {@code customer_id = NULL}. Đây là bẫy: code trông đúng nhưng FK mất.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S06b | Cột FK map 2 lần: chỉ set field customer (insertable=false) → FK = NULL")
	void s06b_settingTheReadOnlyEntityFieldDoesNotWriteTheForeignKey() {
		// ── GIVEN: một customer đã có trong DB ──
		Long customerId = inTxReturning(() -> {
			Customer customerAn = new Customer("An");
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN: persist order chỉ set field customer (entity), không set customerId ──
		sql.reset();
		DualMappedOrder orderA = inTxReturning(() -> {
			DualMappedOrder order = new DualMappedOrder("A-001");
			Customer customerManaged = em.find(Customer.class, customerId);
			order.setCustomer(customerManaged);
			em.persist(order);
			// [LIFECYCLE] order: TRANSIENT → MANAGED (nhưng field customer bị bỏ qua khi ghi)
			assertState(em, order, MANAGED);
			return order;
			// [FLUSH] lúc commit: INSERT order_dual_mapping KHÔNG có customer_id (insertable = false)
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertInserts("order_dual_mapping", 1);
		db.print(DUAL_ORDER);
		assertThat(db.rowById(DUAL_ORDER, orderA.getId()).get("customer_id"))
			.as("field customer bị tắt quyền ghi (insertable/updatable = false) nên FK không được ghi")
			.isNull();
	}

	/**
	 * G1-S06c — [BOOT-FAIL] Map cột FK hai lần mà CẢ HAI cùng được ghi
	 *
	 * <p>Mapping: entity sai {@code bootfail/g1s06/DuplicateColumnOrder}, thiếu {@code insertable/updatable = false}.
	 * <p><b>Câu hỏi</b>: Quên tắt quyền ghi của một trong hai property cùng map cột {@code customer_id} thì sao?
	 * <p><b>Kết luận</b>: Hibernate không khởi động được, vì không biết property nào là nguồn của cột. Lỗi nêu tên cột.
	 * <p>Xác minh: Hibernate 7.4.15.Final. Nguyên văn thông báo lỗi xem docs/RESULTS.md.
	 */
	@Test
	@DisplayName("G1-S06c | [BOOT-FAIL] Cột FK map 2 lần đều ghi được: Hibernate không khởi động")
	void s06c_bootFailWhenColumnIsMappedTwiceAndBothWritable() {
		// ── WHEN: dựng SessionFactory riêng chỉ gồm entity sai (+ Customer mà nó trỏ tới) ──
		Throwable failure = MappingBootstrap.tryBuild(DuplicateColumnOrder.class, Customer.class);

		// ── THEN ──
		System.out.println("[BOOT-FAIL] " + (failure == null ? "(không lỗi)" : failure.getClass().getName() + ": " + failure.getMessage()));
		assertThat(failure).as("mapping sai thì SessionFactory phải khởi động thất bại").isNotNull();
		assertThat(failure.getMessage())
			.as("thông báo lỗi phải nêu tên cột bị map trùng để người học biết sửa ở đâu")
			.contains("customer_id");
	}

	/**
	 * G1-S18a — Một chiều vẫn query ngược được: JPQL
	 *
	 * <p>Mapping: V1, {@code Customer} KHÔNG có collection {@code orders}.
	 * <p><b>Câu hỏi</b>: Không có collection thì làm sao lấy các order của một customer?
	 * <p><b>Kết luận</b>: Hỏi DB bằng query: {@code where o.customer = :customer} → 1 câu SELECT lọc theo
	 * {@code customer_id}. Nhiều khi {@code @ManyToOne} một chiều + query là đủ, không cần collection.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S18a | Một chiều vẫn lấy được order của customer bằng JPQL")
	void s18a_jpqlFindsOrdersOfCustomerWithoutCollection() {
		// ── GIVEN: An có 2 order, Bình có 1 order ──
		Customer customerAn = new Customer("An");
		seedOrders(customerAn, "A-001", "A-002");
		Customer customerBinh = new Customer("Bình");
		seedOrders(customerBinh, "B-001");

		// ── WHEN: lấy order của An bằng JPQL ──
		sql.reset();
		List<PurchaseOrder> ordersOfAn = inTxReturning(() -> em
			.createQuery("select o from G1V1_PurchaseOrder o where o.customer = :customer", PurchaseOrder.class)
			.setParameter("customer", customerAn)
			.getResultList());

		// ── THEN: SQL + kết quả ──
		sql.print();
		sql.assertSelects("purchase_order", 1);
		assertThat(ordersOfAn).as("An có đúng 2 order, order của Bình không lẫn vào").hasSize(2);
		assertThat(ordersOfAn.get(0).getOrderNo()).as("mọi order trả về đều thuộc An").startsWith("A-");
		assertThat(ordersOfAn.get(1).getOrderNo()).as("mọi order trả về đều thuộc An").startsWith("A-");
	}

	/**
	 * G1-S18b — Một chiều vẫn query ngược được: derived query của Spring Data
	 *
	 * <p>Mapping: V1, {@code G1V1PurchaseOrderRepository.findByCustomerId(Long)}.
	 * <p><b>Câu hỏi</b>: Có cách nào không cần viết JPQL?
	 * <p><b>Kết luận</b>: Có. Spring Data đọc tên method {@code findByCustomerId}, đi qua {@code customer.id} và sinh
	 * câu SELECT {@code where customer_id = ?}, không cần collection.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G1-S18b | Một chiều vẫn lấy được order của customer bằng derived query findByCustomerId")
	void s18b_derivedQueryFindsOrdersOfCustomerWithoutCollection() {
		// ── GIVEN: An có 2 order, Bình có 1 order ──
		Customer customerAn = new Customer("An");
		seedOrders(customerAn, "A-001", "A-002");
		Customer customerBinh = new Customer("Bình");
		seedOrders(customerBinh, "B-001");

		// ── WHEN: gọi derived query ──
		sql.reset();
		List<PurchaseOrder> ordersOfAn = purchaseOrderRepository.findByCustomerId(customerAn.getId());

		// ── THEN: SQL + kết quả ──
		sql.print();
		sql.assertSelects("purchase_order", 1);
		assertThat(ordersOfAn).as("derived query lọc theo customer_id nên chỉ trả 2 order của An").hasSize(2);
		Map<String, Object> firstRow = db.rowById(PURCHASE_ORDER, ordersOfAn.get(0).getId());
		assertThat(firstRow.get("customer_id")).as("dữ liệu thật: order trả về có FK là An").isEqualTo(customerAn.getId());
	}

	/** Helper GIVEN dùng chung cho S18: lưu customer rồi các order trỏ tới nó (mỗi bước một transaction). */
	private void seedOrders(Customer customer, String... orderNumbers) {
		inTx(() -> em.persist(customer));
		for (String orderNumber : orderNumbers) {
			inTx(() -> {
				PurchaseOrder order = new PurchaseOrder(orderNumber);
				order.setCustomer(customer);
				em.persist(order);
			});
		}
	}
}
