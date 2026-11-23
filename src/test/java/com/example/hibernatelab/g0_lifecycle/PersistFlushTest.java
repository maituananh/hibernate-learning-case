package com.example.hibernatelab.g0_lifecycle;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.REMOVED;
import static com.example.hibernatelab.support.LifecycleInspector.State.TRANSIENT;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G0: persist / flush / commit. S01 (SEQUENCE) so với S02 (IDENTITY); S07 (persist lại entity REMOVED);
 * S10 (FlushMode AUTO: Hibernate tự flush trước khi chạy query).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class PersistFlushTest extends AbstractScenarioTest {

	private static final String CUSTOMER = "g0_lifecycle.customer";
	private static final String IDENTITY_CUSTOMER = "g0_lifecycle.identity_customer";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { "g0_lifecycle" };
	}

	/**
	 * G0-S01 — persist() với SEQUENCE: có id ngay nhưng INSERT bị hoãn; flush ≠ commit
	 *
	 * <p>Mapping : g0_lifecycle.customer, id {@code @GeneratedValue(SEQUENCE)}, chưa có quan hệ nào
	 * <p><b>Câu hỏi</b>: Gọi {@code em.persist(customer)} thì câu INSERT chạy lúc nào? Và sau khi
	 * {@code em.flush()}, một connection khác đã thấy dòng đó chưa?
	 * <p><b>Kết luận</b>:
	 * <ol>
	 *   <li>{@code persist} chỉ xin id từ sequence và đưa entity vào persistence context: 0 INSERT.</li>
	 *   <li>{@code flush} mới gửi INSERT xuống DB, nhưng transaction chưa commit nên connection khác vẫn
	 *       thấy 0 dòng. Flush đẩy SQL xuống, commit mới làm dữ liệu hiện ra với người khác.</li>
	 *   <li>Sau commit, connection khác thấy 1 dòng; entity chuyển sang DETACHED vì persistence context đóng.</li>
	 * </ol>
	 * Xác minh: Hibernate 7.4.15.Final (do Spring Boot 4.2.0 quản lý)
	 */
	@Test
	@DisplayName("G0-S01 | persist() không INSERT ngay (SEQUENCE); flush ≠ commit")
	void s01_persistDoesNotInsertImmediately_flushIsNotCommit() {
		// ── GIVEN: bảng customer trống (DbCleaner đã TRUNCATE trước test) ──
		assertThat(db.countOf(CUSTOMER)).as("điều kiện đầu: chưa có khách hàng nào").isZero();

		// ── WHEN: trong MỘT transaction — persist, rồi flush, rồi (kết thúc lambda) commit ──
		sql.reset();
		Customer customerAn = inTxReturning(() -> {
			Customer customer = new Customer("An", "an@example.com");
			// [LIFECYCLE] customer: TRANSIENT (mới new, chưa có id, chưa nằm trong persistence context)
			assertState(em, customer, TRANSIENT);

			sql.mark("WHEN 1: persist()");
			em.persist(customer);
			// [LIFECYCLE] customer: TRANSIENT → MANAGED (id đã lấy từ sequence, CHƯA có INSERT nào)
			assertState(em, customer, MANAGED);

			// ── THEN (sau persist): SQL + DB thật ──
			assertThat(customer.getId()).as("persist với SEQUENCE: id có ngay vì Hibernate xin từ sequence").isNotNull();
			sql.assertInserts("customer", 0); // INSERT bị hoãn tới flush (nextval không tính)
			assertThat(db.countOf(CUSTOMER)).as("DB chưa có dòng nào vì INSERT chưa được gửi").isZero();

			sql.mark("WHEN 2: flush()");
			em.flush();
			// [FLUSH] gửi INSERT customer xuống DB. Entity vẫn MANAGED (flush không đổi trạng thái)
			assertState(em, customer, MANAGED);

			// ── THEN (sau flush): SQL + DB thật, nhìn từ hai connection ──
			sql.assertInserts("customer", 1);
			assertThat(db.countOf(CUSTOMER)).as("cùng transaction/connection thì thấy dòng vừa flush").isEqualTo(1);
			long seenByOtherConnection = inNewConnection(() -> db.countOf(CUSTOMER));
			assertThat(seenByOtherConnection)
				.as("flush ≠ commit: transaction khác (connection khác) chưa thấy dòng chưa commit")
				.isZero();

			sql.mark("WHEN 3: commit (hết lambda)");
			return customer;
		});
		// [FLUSH] commit: không còn gì để flush (đã flush ở trên) → không thêm INSERT/UPDATE nào
		// [TX END] persistence context đóng → customer: MANAGED → DETACHED
		assertState(em, customerAn, DETACHED);

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("customer", 1);
		sql.assertUpdates("customer", 0);

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		long seenAfterCommit = inNewConnection(() -> db.countOf(CUSTOMER));
		assertThat(seenAfterCommit).as("sau commit, connection khác đã thấy dòng").isEqualTo(1);
		Map<String, Object> row = db.rowById(CUSTOMER, customerAn.getId());
		assertThat(row.get("name")).as("dữ liệu ghi xuống DB đúng như đã persist").isEqualTo("An");
	}

	/**
	 * G0-S02 — IDENTITY: INSERT chạy NGAY ở dòng persist(), không hoãn được
	 *
	 * <p>Mapping : g0_lifecycle.identity_customer, id {@code @GeneratedValue(IDENTITY)}
	 * <p><b>Câu hỏi</b>: Khác với S01 (SEQUENCE), khi id do DB sinh lúc INSERT thì câu INSERT chạy lúc nào?
	 * <p><b>Kết luận</b>: Hibernate cần id để đưa entity vào persistence context, mà với IDENTITY id chỉ có
	 * sau khi INSERT. Nên INSERT chạy NGAY ở {@code persist()}, trước mọi flush. Cái giá: mất khả năng hoãn
	 * và gom SQL. Lưu ý: INSERT đã chạy nhưng chưa commit nên connection khác vẫn chưa thấy (giống S01).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S02 | IDENTITY: INSERT chạy ngay khi persist(), trước mọi flush")
	void s02_identityInsertsImmediatelyOnPersist() {
		// ── GIVEN: bảng identity_customer trống ──
		assertThat(db.countOf(IDENTITY_CUSTOMER)).as("điều kiện đầu: chưa có dòng nào").isZero();

		// ── WHEN: persist một IdentityCustomer, CHƯA gọi flush ──
		sql.reset();
		inTx(() -> {
			IdentityCustomer customerBinh = new IdentityCustomer("Bình");
			// [LIFECYCLE] customerBinh: TRANSIENT (chưa có id)
			assertState(em, customerBinh, TRANSIENT);

			sql.mark("WHEN: persist() — chưa flush");
			em.persist(customerBinh);
			// [LIFECYCLE] customerBinh: TRANSIENT → MANAGED. Id có ngay vì DB đã sinh khi INSERT
			assertState(em, customerBinh, MANAGED);

			// ── THEN (ngay sau persist, CHƯA flush): INSERT đã chạy rồi ──
			assertThat(customerBinh.getId()).as("id do DB sinh khi INSERT, nên có ngay sau persist").isNotNull();
			sql.assertInserts("identity_customer", 1); // so với S01: ở đó là 0 cho tới flush
			assertThat(db.countOf(IDENTITY_CUSTOMER)).as("cùng connection: thấy dòng đã INSERT dù chưa flush").isEqualTo(1);
			long seenByOtherConnection = inNewConnection(() -> db.countOf(IDENTITY_CUSTOMER));
			assertThat(seenByOtherConnection).as("INSERT sớm không có nghĩa là commit sớm: connection khác chưa thấy").isZero();

			em.flush();
			// [FLUSH] không còn INSERT nào để gửi: tổng vẫn là 1
			sql.assertInserts("identity_customer", 1);
		});
		// [TX END] commit → persistence context đóng

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertInserts("identity_customer", 1);
		db.print(IDENTITY_CUSTOMER);
		assertThat(db.countOf(IDENTITY_CUSTOMER)).as("sau commit dòng nằm trong DB").isEqualTo(1);
	}

	/**
	 * G0-S07 — persist() lại một entity đang REMOVED trong cùng transaction
	 *
	 * <p>Mapping : g0_lifecycle.customer (SEQUENCE)
	 * <p><b>Câu hỏi</b>: {@code find → remove → persist} lại chính entity đó thì có DELETE không?
	 * <p><b>Kết luận</b> (🔬 đã chạy, xem docs/RESULTS.md): theo spec JPA entity quay về MANAGED và việc xoá
	 * bị huỷ. Test này khẳng định đúng như vậy: không có DELETE, dòng vẫn còn trong DB.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S07 | persist() lại entity REMOVED: quay về MANAGED, không DELETE")
	void s07_persistAgainOnRemovedEntityCancelsTheRemoval() {
		// ── GIVEN: một customer đã nằm trong DB ──
		Long customerId = insertCustomerAn();

		// ── WHEN: find → remove → persist lại, tất cả trong CÙNG một transaction ──
		sql.reset();
		inTx(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			// [LIFECYCLE] customerAn: MANAGED (vừa find)
			assertState(em, customerAn, MANAGED);

			em.remove(customerAn);
			// [LIFECYCLE] customerAn: MANAGED → REMOVED (DELETE đã được xếp hàng, chưa chạy)
			assertState(em, customerAn, REMOVED);

			sql.mark("WHEN: persist() lại entity REMOVED");
			em.persist(customerAn);
			// [LIFECYCLE] customerAn: REMOVED → MANAGED (lệnh xoá bị huỷ)
			assertState(em, customerAn, MANAGED);
		});
		// [FLUSH] lúc commit: không còn gì thay đổi nên không có SQL ghi nào

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("customer", 0);
		sql.assertUpdates("customer", 0);

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		assertThat(db.countOf(CUSTOMER)).as("lệnh xoá đã bị persist() huỷ nên dòng vẫn còn").isEqualTo(1);
	}

	/**
	 * G0-S10a — FlushMode AUTO: sửa entity rồi chạy JPQL, Hibernate tự flush UPDATE TRƯỚC
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: Mới {@code setName("Bình")} (chưa flush), JPQL {@code where name = 'Bình'} có thấy không?
	 * <p><b>Kết luận</b>: Có. Với FlushMode AUTO, trước khi chạy query chạm tới bảng customer Hibernate
	 * flush các thay đổi đang chờ, để query thấy dữ liệu nhất quán với những gì code vừa sửa.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S10a | FlushMode AUTO: JPQL tự kích hoạt flush UPDATE trước SELECT")
	void s10a_jpqlTriggersAutoFlushBeforeSelect() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = insertCustomerAn();

		// ── WHEN: sửa tên (chưa flush) rồi chạy JPQL tìm theo tên MỚI ──
		inTx(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			customerAn.setName("Bình");
			// [FLUSH] CHƯA: customerAn là MANAGED và dirty, UPDATE chưa được gửi
			sql.reset(); // chỉ đo từ đây: chỉ còn UPDATE (do auto flush) và SELECT của query

			sql.mark("WHEN: JPQL select ... where c.name = :name");
			List<Customer> found = em.createQuery("select c from G0_Customer c where c.name = :name", Customer.class)
				.setParameter("name", "Bình")
				.getResultList();

			// ── THEN: SQL (UPDATE phải đứng TRƯỚC SELECT) + kết quả query ──
			sql.print();
			sql.assertInOrder("update g0_lifecycle.customer", "select");
			assertThat(found).as("auto flush đã đẩy tên mới xuống DB nên query tìm thấy").hasSize(1);
		});
	}

	/**
	 * G0-S10b — FlushMode AUTO với NATIVE query 🔬
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: Hibernate có tự flush trước native query (SQL thuần) không, khi nó không biết query chạm bảng nào?
	 * <p><b>Kết luận</b> (🔬, xem docs/RESULTS.md): Có, UPDATE vẫn chạy TRƯỚC native query. Khi Hibernate chạy theo
	 * chế độ JPA (cách Spring Boot khởi động), native query được coi là "có thể chạm mọi bảng" nên flush toàn bộ
	 * thay đổi đang chờ. Đây là hành vi THỰC TẾ quan sát được ở Hibernate 7.4.15, không phải theo trí nhớ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S10b | FlushMode AUTO với native query: ghi lại hành vi thực tế")
	void s10b_nativeQueryAutoFlushBehaviour() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = insertCustomerAn();

		// ── WHEN: sửa tên (chưa flush) rồi chạy NATIVE query tìm theo tên mới ──
		inTx(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			customerAn.setName("Bình");
			sql.reset();

			sql.mark("WHEN: native query select ... where name = ?");
			List<?> found = em.createNativeQuery("select id from g0_lifecycle.customer where name = ?")
				.setParameter(1, "Bình")
				.getResultList();

			// ── THEN: SQL + kết quả ──
			sql.print();
			sql.assertInOrder("update g0_lifecycle.customer", "select id from g0_lifecycle.customer");
			assertThat(found).as("native query cũng kích hoạt auto flush nên thấy tên mới").hasSize(1);
		});
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
