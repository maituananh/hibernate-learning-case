package com.example.hibernatelab.g0_lifecycle;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G0: {@code getReference()} vs {@code find()} (S08) và cache cấp 1 (S09).
 *
 * <p>Khái niệm proxy: {@code getReference} trả về một object "giữ chỗ" (proxy) chỉ biết id, KHÔNG chạm DB.
 * Proxy chỉ SELECT khi code đọc một field khác id. Đây là nền của lazy loading ở các nhóm sau.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class ReferenceAndCacheTest extends AbstractScenarioTest {

	private static final String CUSTOMER = "g0_lifecycle.customer";
	private static final String PURCHASE_ORDER = "g0_lifecycle.purchase_order";

	/** Id chắc chắn không có trong DB (bảng được TRUNCATE trước mỗi test, sequence chỉ đi từ 1). */
	private static final long MISSING_CUSTOMER_ID = 987_654_321L;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { "g0_lifecycle" };
	}

	/**
	 * G0-S08a — getReference(): proxy chưa khởi tạo, chỉ SELECT khi đọc field khác id
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: {@code getReference} có SELECT không? {@code getId()} và {@code getName()} thì sao?
	 * <p><b>Kết luận</b>: {@code getReference} và {@code getId()} không SELECT (id đã nằm sẵn trong proxy).
	 * {@code getName()} mới kích hoạt SELECT và khởi tạo proxy (initialize).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S08a | getReference(): không SELECT cho tới khi đọc field khác id")
	void s08a_getReferenceIsLazyUntilNonIdFieldIsRead() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = insertCustomerAn();

		// ── WHEN: getReference → getId → getName, trong một transaction ──
		sql.reset();
		inTx(() -> {
			Customer customerProxy = em.getReference(Customer.class, customerId);
			// [LIFECYCLE] customerProxy: MANAGED nhưng CHƯA khởi tạo (chỉ là proxy giữ id)
			assertState(em, customerProxy, MANAGED);
			assertThat(Hibernate.isInitialized(customerProxy)).as("getReference không chạm DB nên proxy chưa khởi tạo").isFalse();
			sql.assertSelects("customer", 0);

			Long idFromProxy = customerProxy.getId();
			assertThat(idFromProxy).as("id đã có sẵn trong proxy").isEqualTo(customerId);
			assertThat(Hibernate.isInitialized(customerProxy)).as("getId() không làm proxy khởi tạo").isFalse();
			sql.assertSelects("customer", 0);

			sql.mark("WHEN: getName() — chạm field khác id");
			String name = customerProxy.getName();
			// [LIFECYCLE] customerProxy: vẫn MANAGED, nhưng lúc này đã khởi tạo (đã SELECT)
			assertThat(Hibernate.isInitialized(customerProxy)).as("đọc field khác id buộc proxy tải dữ liệu").isTrue();
			assertThat(name).as("dữ liệu đúng như trong DB").isEqualTo("An");
			sql.assertSelects("customer", 1);
		});
		sql.print();
	}

	/**
	 * G0-S08b — Gán FK bằng getReference(): INSERT order mà KHÔNG SELECT customer
	 *
	 * <p>Mapping : g0_lifecycle.purchase_order, {@code @ManyToOne(LAZY) customer} (OWNING side, giữ FK customer_id)
	 * <p><b>Câu hỏi</b>: Muốn gán customer cho order chỉ cần id, có phải SELECT customer không?
	 * <p><b>Kết luận</b>: Không. Hibernate chỉ cần id của proxy để ghi cột FK. Bài học: dùng
	 * {@code getReferenceById} thay cho {@code findById} khi chỉ cần gán FK, tiết kiệm 1 SELECT.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S08b | getReference() làm FK: INSERT order không cần SELECT customer")
	void s08b_getReferenceAsForeignKeyAvoidsSelect() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = insertCustomerAn();

		// ── WHEN: tạo order trỏ tới customer bằng getReference, persist, commit ──
		sql.reset();
		Long orderId = inTxReturning(() -> {
			PurchaseOrder orderA = new PurchaseOrder("ORD-A");
			orderA.setCustomer(em.getReference(Customer.class, customerId));
			em.persist(orderA);
			// [LIFECYCLE] orderA: TRANSIENT → MANAGED; customer chỉ là proxy chưa khởi tạo
			assertState(em, orderA, MANAGED);
			return orderA;
		}).getId();
		// [FLUSH] lúc commit: INSERT purchase_order với customer_id = id của proxy

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("purchase_order", 1);
		sql.assertSelects("customer", 0);

		// ── THEN: DB thật ──
		db.print(PURCHASE_ORDER);
		assertThat(db.rowById(PURCHASE_ORDER, orderId).get("customer_id"))
			.as("FK được ghi từ id của proxy, không cần tải customer")
			.isEqualTo(customerId);
	}

	/**
	 * G0-S08c — getReference() với id không tồn tại: lỗi chỉ xuất hiện khi truy cập field
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: {@code getReference} với id không có trong DB có lỗi ngay không?
	 * <p><b>Kết luận</b>: Không, vì chưa hề hỏi DB. Chỉ khi đọc field khác id, proxy mới SELECT, không thấy dòng nào
	 * và ném {@code EntityNotFoundException}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S08c | getReference() với id không tồn tại: EntityNotFoundException khi chạm field")
	void s08c_getReferenceOfMissingRowFailsOnFieldAccess() {
		// ── GIVEN: DB không có customer nào với id này (bảng trống) ──
		assertThat(db.countOf(CUSTOMER)).as("bảng customer trống").isZero();

		// ── WHEN + THEN ──
		sql.reset();
		inTx(() -> {
			Customer missingProxy = em.getReference(Customer.class, MISSING_CUSTOMER_ID);
			// Chưa có lỗi: getReference không hỏi DB
			assertThat(Hibernate.isInitialized(missingProxy)).as("getReference không SELECT nên không biết dòng có tồn tại").isFalse();

			assertThatThrownBy(missingProxy::getName)
				.as("chạm field khác id buộc SELECT; không thấy dòng nên báo không tìm thấy entity")
				.isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
		});
		sql.print();
		sql.assertSelects("customer", 1);
	}

	/**
	 * G0-S08d — Dùng reference trỏ tới dòng không tồn tại làm FK: vi phạm FK lúc flush
	 *
	 * <p>Mapping : g0_lifecycle.purchase_order → customer (FK customer_id)
	 * <p><b>Câu hỏi</b>: Gán {@code getReference} của id sai cho order rồi persist thì lỗi ở đâu, lúc nào?
	 * <p><b>Kết luận</b>: {@code persist} không báo lỗi (không ai kiểm tra), lỗi chỉ xảy ra lúc flush khi DB từ chối
	 * INSERT vì khoá ngoại. Đánh đổi của {@code getReference}: tiết kiệm SELECT nhưng không kiểm tra sự tồn tại.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S08d | getReference() sai id làm FK: persist() êm, flush() vi phạm FK")
	void s08d_wrongReferenceAsForeignKeyFailsAtFlush() {
		// ── GIVEN: bảng trống, id customer không tồn tại ──
		assertThat(db.countOf(CUSTOMER)).as("không có customer nào").isZero();

		// ── WHEN + THEN: persist êm, flush lỗi ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> {
			PurchaseOrder orderA = new PurchaseOrder("ORD-A");
			orderA.setCustomer(em.getReference(Customer.class, MISSING_CUSTOMER_ID));
			em.persist(orderA); // êm: không có SQL, không kiểm tra FK
			sql.assertInserts("purchase_order", 0);

			em.flush(); // INSERT chạy ở đây và DB từ chối vì customer_id không trỏ tới dòng nào
		})).as("DB từ chối INSERT vì vi phạm khoá ngoại customer_id")
			.hasMessageContaining("purchase_order");

		// ── THEN: DB thật ──
		sql.print();
		assertThat(db.countOf(PURCHASE_ORDER)).as("INSERT bị từ chối và transaction rollback").isZero();
	}

	/**
	 * G0-S09 — Cache cấp 1 (first-level cache) và identity
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: {@code find(id)} hai lần trong một transaction thì SELECT mấy lần? Hai object có là một?
	 * Sau {@code clear()} thì sao?
	 * <p><b>Kết luận</b>: Persistence context là cache cấp 1: lần đầu SELECT, lần hai trả về CÙNG instance
	 * (so sánh {@code ==} đúng) và không SELECT. Sau {@code clear()} cache rỗng nên {@code find} lại SELECT và
	 * trả về một instance MỚI khác. Đây cũng là lý do assert dữ liệu phải đọc bằng JDBC, không bằng {@code find}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S09 | Cache cấp 1: find 2 lần = 1 SELECT, cùng instance; sau clear() SELECT lại")
	void s09_firstLevelCacheReturnsSameInstance() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = insertCustomerAn();

		// ── WHEN + THEN: find, find, clear, find — trong cùng một transaction ──
		sql.reset();
		inTx(() -> {
			Customer firstFind = em.find(Customer.class, customerId);
			sql.assertSelects("customer", 1); // lần đầu: cache rỗng nên phải SELECT

			Customer secondFind = em.find(Customer.class, customerId);
			sql.assertSelects("customer", 1); // vẫn 1: lần hai lấy từ persistence context
			assertThat(secondFind).as("cache cấp 1 đảm bảo một dòng DB ↔ một instance trong persistence context").isSameAs(firstFind);

			em.clear();
			// [LIFECYCLE] firstFind: MANAGED → DETACHED (clear xoá sạch persistence context)
			assertState(em, firstFind, DETACHED);

			Customer thirdFind = em.find(Customer.class, customerId);
			sql.assertSelects("customer", 2); // cache đã rỗng nên SELECT lần nữa
			assertThat(thirdFind).as("sau clear() instance cũ đã bị bỏ, find tạo instance mới").isNotSameAs(firstFind);
		});
		sql.print();
	}

	/** GIVEN dùng chung: persist customer "An" và commit, trả về id (entity trả về sẽ DETACHED nên chỉ lấy id). */
	private Long insertCustomerAn() {
		return inTxReturning(() -> {
			Customer customerAn = new Customer("An", "an@example.com");
			em.persist(customerAn);
			return customerAn;
		}).getId();
	}
}
