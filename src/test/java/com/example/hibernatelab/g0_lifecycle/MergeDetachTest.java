package com.example.hibernatelab.g0_lifecycle;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.REMOVED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G0: dirty checking (S03), entity DETACHED (S04), merge (S05), remove (S06), rollback + merge (S13).
 * Tất cả dùng {@link Customer} (SEQUENCE). Mỗi bước tự mở transaction riêng bằng {@code inTx} để thấy rõ
 * lúc nào entity MANAGED, lúc nào DETACHED.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class MergeDetachTest extends AbstractScenarioTest {

	private static final String CUSTOMER = "g0_lifecycle.customer";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { "g0_lifecycle" };
	}

	/**
	 * G0-S03 — Dirty checking: không gọi save()/merge() mà vẫn có UPDATE
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: Chỉ {@code find} rồi {@code setName}, không gọi gì thêm, DB có đổi không?
	 * <p><b>Kết luận</b>: Có. Entity MANAGED được persistence context "chụp ảnh" lúc load; khi flush (ở đây là
	 * lúc commit) Hibernate so sánh trạng thái hiện tại với ảnh chụp, thấy khác thì tự sinh UPDATE.
	 * Đó là dirty checking. Vì vậy với entity MANAGED không cần (và không nên) gọi {@code save()}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S03 | Dirty checking: sửa entity MANAGED, không gọi save() vẫn UPDATE")
	void s03_dirtyCheckingUpdatesWithoutSave() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = insertCustomerAn();

		// ── WHEN: một transaction: find → setName → hết transaction (KHÔNG gọi save/merge) ──
		sql.reset();
		Customer customerAfterTx = inTxReturning(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			// [LIFECYCLE] customerAn: MANAGED (find đưa entity vào persistence context kèm ảnh chụp trạng thái)
			assertState(em, customerAn, MANAGED);

			customerAn.setName("Bình");
			// [FLUSH] CHƯA: sửa field chỉ đổi object trong bộ nhớ, UPDATE chờ tới flush
			sql.assertUpdates("customer", 0);
			return customerAn;
		});
		// [FLUSH] lúc commit: dirty checking phát hiện name đổi → UPDATE
		// [TX END] persistence context đóng → customerAn: MANAGED → DETACHED
		assertState(em, customerAfterTx, DETACHED);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 1);
		sql.assertUpdates("customer", 1);

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		assertThat(db.rowById(CUSTOMER, customerId).get("name"))
			.as("dirty checking đã ghi tên mới xuống DB mà không cần save()")
			.isEqualTo("Bình");
	}

	/**
	 * G0-S04a — Sửa entity DETACHED sau khi transaction kết thúc: không có gì xảy ra
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: {@code find} ở transaction 1, đóng transaction, rồi {@code setName}. Mở transaction 2
	 * (không merge) thì DB có đổi không?
	 * <p><b>Kết luận</b>: Không. Hết transaction 1, persistence context đóng và entity thành DETACHED, không ai
	 * theo dõi nó nữa. Transaction 2 có persistence context MỚI, không biết gì về object này.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S04a | Sửa entity DETACHED (hết transaction): 0 UPDATE, DB giữ giá trị cũ")
	void s04a_editingDetachedEntityAfterTxEndsDoesNothing() {
		// ── GIVEN: customer "An" trong DB, và một bản DETACHED của nó ──
		Long customerId = insertCustomerAn();
		Customer detachedCustomer = inTxReturning(() -> em.find(Customer.class, customerId));
		// [LIFECYCLE] detachedCustomer: MANAGED → DETACHED (transaction 1 đã kết thúc)
		assertState(em, detachedCustomer, DETACHED);

		// ── WHEN: sửa object DETACHED, rồi mở transaction 2 mà KHÔNG merge ──
		sql.reset();
		detachedCustomer.setName("X");
		inTx(() -> {
			// transaction 2 rỗng: persistence context mới tinh, không chứa detachedCustomer
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("customer", 0);

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		assertThat(db.rowById(CUSTOMER, customerId).get("name"))
			.as("thay đổi trên entity DETACHED không ai theo dõi nên không xuống DB")
			.isEqualTo("An");
	}

	/**
	 * G0-S04b — Sửa entity sau em.detach() / em.clear() ngay trong cùng transaction: cũng không có gì xảy ra
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: Cùng một transaction đang mở, nếu {@code detach} (hoặc {@code clear}) rồi mới sửa thì sao?
	 * <p><b>Kết luận</b>: Vẫn 0 UPDATE. Quan trọng là entity còn nằm trong persistence context hay không,
	 * chứ không phải transaction còn mở hay không. {@code detach} gỡ MỘT entity, {@code clear} gỡ TẤT CẢ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S04b | em.detach()/em.clear() rồi mới sửa: 0 UPDATE dù transaction còn mở")
	void s04b_editingAfterDetachOrClearInSameTxDoesNothing() {
		// ── GIVEN: hai customer trong DB ──
		Long customerAnId = insertCustomerAn();
		Long customerBinhId = inTxReturning(() -> {
			Customer customerBinh = new Customer("Bình", "binh@example.com");
			em.persist(customerBinh);
			return customerBinh;
		}).getId();

		// ── WHEN: trong CÙNG một transaction: detach customerAn, clear (gỡ customerBinh), rồi sửa cả hai ──
		sql.reset();
		inTx(() -> {
			Customer customerAn = em.find(Customer.class, customerAnId);
			Customer customerBinh = em.find(Customer.class, customerBinhId);

			em.detach(customerAn);
			// [LIFECYCLE] customerAn: MANAGED → DETACHED (chỉ riêng customerAn bị gỡ)
			assertState(em, customerAn, DETACHED);
			assertState(em, customerBinh, MANAGED);

			em.clear();
			// [LIFECYCLE] customerBinh: MANAGED → DETACHED (clear gỡ TẤT CẢ entity còn lại)
			assertState(em, customerBinh, DETACHED);

			customerAn.setName("X");
			customerBinh.setName("Y");
		});
		// [FLUSH] lúc commit: persistence context rỗng nên không có gì để dirty checking

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("customer", 0);

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		assertThat(db.rowById(CUSTOMER, customerAnId).get("name"))
			.as("customerAn bị detach trước khi sửa nên DB giữ nguyên")
			.isEqualTo("An");
		assertThat(db.rowById(CUSTOMER, customerBinhId).get("name"))
			.as("customerBinh bị clear trước khi sửa nên DB giữ nguyên")
			.isEqualTo("Bình");
	}

	/**
	 * G0-S05 — merge() trả về một bản MANAGED KHÁC; object DETACHED vẫn DETACHED
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: Sau {@code managed = em.merge(detached)}, sửa tiếp {@code detached} thì có lưu không?
	 * <p><b>Kết luận</b>: Không. {@code merge} SELECT dòng hiện tại, COPY trạng thái từ {@code detached} sang
	 * một bản MANAGED mới ({@code managed}), còn {@code detached} vẫn là DETACHED. Chỉ {@code managed} được theo dõi,
	 * nên lần sửa thành "Y" trên {@code detached} bị mất. {@code repository.save(entity)} cũng trả về bản đã
	 * merge, vì vậy luôn phải dùng GIÁ TRỊ TRẢ VỀ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S05 | merge() trả về bản MANAGED khác; sửa tiếp bản DETACHED bị mất")
	void s05_mergeReturnsAnotherManagedInstance() {
		// ── GIVEN: customer "An" trong DB; một bản DETACHED đã được sửa tên thành "X" ──
		Long customerId = insertCustomerAn();
		Customer detachedCustomer = inTxReturning(() -> em.find(Customer.class, customerId));
		detachedCustomer.setName("X");

		// ── WHEN: tx2: merge, rồi sửa tiếp bản DETACHED thành "Y", rồi commit ──
		sql.reset();
		inTx(() -> {
			Customer managedCustomer = em.merge(detachedCustomer);
			// [LIFECYCLE] managedCustomer: MANAGED (bản MỚI do merge tạo/lấy ra); detachedCustomer: vẫn DETACHED
			assertState(em, managedCustomer, MANAGED);
			assertState(em, detachedCustomer, DETACHED);
			assertThat(managedCustomer)
				.as("merge KHÔNG đưa object truyền vào thành MANAGED mà trả về một object khác")
				.isNotSameAs(detachedCustomer);

			detachedCustomer.setName("Y"); // sửa lên bản không ai theo dõi → sẽ bị mất
		});
		// [FLUSH] lúc commit: managedCustomer mang tên "X" (giá trị lúc merge) khác ảnh chụp "An" → UPDATE

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 1); // merge phải SELECT để so sánh với dòng hiện tại
		sql.assertUpdates("customer", 1);

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		assertThat(db.rowById(CUSTOMER, customerId).get("name"))
			.as("DB giữ giá trị lúc merge (X); lần sửa Y trên bản DETACHED bị mất")
			.isEqualTo("X");
	}

	/**
	 * G0-S06a — remove(): REMOVED ngay, nhưng DELETE chỉ chạy lúc flush
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: Sau {@code em.remove(customer)}, dòng trong DB mất ngay chưa? Entity ở trạng thái nào?
	 * <p><b>Kết luận</b>: Chưa. {@code remove} chuyển entity sang REMOVED ({@code em.contains} = false) và xếp hàng
	 * câu DELETE; DELETE chỉ chạy khi flush. Giống persist/update, mọi thay đổi SQL đều bị hoãn tới flush.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S06a | remove(): entity REMOVED ngay, DELETE chỉ chạy lúc flush")
	void s06a_removeSchedulesDeleteUntilFlush() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = insertCustomerAn();

		// ── WHEN: find → remove → flush, trong một transaction ──
		sql.reset();
		inTx(() -> {
			Customer customerAn = em.find(Customer.class, customerId);
			assertState(em, customerAn, MANAGED);

			em.remove(customerAn);
			// [LIFECYCLE] customerAn: MANAGED → REMOVED (DELETE đã xếp hàng, CHƯA chạy)
			assertState(em, customerAn, REMOVED);
			assertThat(em.contains(customerAn)).as("entity REMOVED không còn được coi là 'contained' (MANAGED)").isFalse();
			sql.assertDeletes("customer", 0);
			assertThat(db.countOf(CUSTOMER)).as("DELETE chưa chạy nên dòng vẫn còn trong DB").isEqualTo(1);

			sql.mark("WHEN: flush()");
			em.flush();
			// [FLUSH] DELETE được gửi xuống DB
			sql.assertDeletes("customer", 1);
			assertThat(db.countOf(CUSTOMER)).as("sau flush cùng transaction thấy dòng đã bị xoá").isZero();
		});
		// [TX END] commit

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertDeletes("customer", 1);
		db.print(CUSTOMER);
		long seenByOtherConnection = inNewConnection(() -> db.countOf(CUSTOMER));
		assertThat(seenByOtherConnection).as("sau commit dòng đã mất với mọi connection").isZero();
	}

	/**
	 * G0-S06b — em.remove() trên entity DETACHED bị từ chối
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: Có xoá được bằng {@code em.remove(detached)} không?
	 * <p><b>Kết luận</b>: Không, {@code IllegalArgumentException}. Persistence context không biết object này,
	 * nên không thể xếp hàng lệnh xoá. Muốn xoá phải load lại (find) hoặc merge để có bản MANAGED rồi mới remove.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S06b | em.remove(entity DETACHED) ném IllegalArgumentException")
	void s06b_removeOnDetachedEntityIsRejected() {
		// ── GIVEN: customer "An" trong DB, và một bản DETACHED ──
		Long customerId = insertCustomerAn();
		Customer detachedCustomer = inTxReturning(() -> em.find(Customer.class, customerId));
		assertState(em, detachedCustomer, DETACHED);

		// ── WHEN + THEN: remove entity DETACHED → lỗi ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> em.remove(detachedCustomer)))
			.as("em.remove chỉ nhận entity MANAGED; entity DETACHED bị từ chối")
			.isInstanceOf(IllegalArgumentException.class);

		// ── THEN: DB thật ──
		sql.print();
		sql.assertDeletes("customer", 0);
		assertThat(db.countOf(CUSTOMER)).as("remove bị từ chối nên dòng vẫn còn").isEqualTo(1);
	}

	/**
	 * G0-S13 — Rollback rồi merge một entity "ma" 🔬
	 *
	 * <p>Mapping : g0_lifecycle.customer (SEQUENCE)
	 * <p><b>Câu hỏi</b>: persist trong transaction rồi rollback. Object vẫn giữ id nhưng DB không có dòng nào.
	 * {@code merge(object)} ở transaction mới sẽ ra sao?
	 * <p><b>Kết luận</b> (🔬, xem docs/RESULTS.md): merge KHÔNG tạo dòng mới. Hibernate 7 thấy object có id
	 * nhưng DB không có dòng, nên coi đây là entity đã bị xoá bởi người khác và ném {@code OptimisticLockException}
	 * (hành vi đã đổi từ Hibernate 6.6+; trước đó merge coi như entity mới rồi INSERT). Hệ quả thực tế: không
	 * dùng lại object từ một transaction đã rollback; tạo object mới.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S13 | Rollback rồi merge entity 'ma' (có id nhưng không có dòng trong DB)")
	void s13_mergeGhostEntityAfterRollback() {
		// ── GIVEN: persist trong transaction rồi cố ý ném lỗi để rollback ──
		Customer ghostCustomer = new Customer("Ma", "ma@example.com");
		assertThatThrownBy(() -> inTx(() -> {
			em.persist(ghostCustomer);
			throw new IllegalStateException("cố ý ném lỗi để transaction rollback");
		})).as("exception làm Spring rollback transaction").isInstanceOf(IllegalStateException.class);

		// [LIFECYCLE] ghostCustomer: trông như DETACHED (có id từ sequence) nhưng DB KHÔNG có dòng nào
		assertThat(ghostCustomer.getId()).as("id lấy từ sequence lúc persist, rollback không xoá nó").isNotNull();
		assertState(em, ghostCustomer, DETACHED);
		assertThat(db.countOf(CUSTOMER)).as("rollback: INSERT chưa từng chạy, DB trống").isZero();

		// ── WHEN: merge object "ma" ở transaction mới ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> em.merge(ghostCustomer)))
			.as("merge thấy id nhưng không có dòng trong DB nên báo 'dòng đã bị cập nhật/xoá bởi transaction khác'")
			.isInstanceOf(jakarta.persistence.OptimisticLockException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("customer", 0);

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		assertThat(db.countOf(CUSTOMER)).as("merge thất bại nên vẫn không có dòng nào").isZero();
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
