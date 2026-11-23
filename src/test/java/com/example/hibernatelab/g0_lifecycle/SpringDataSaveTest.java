package com.example.hibernatelab.g0_lifecycle;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G0: Spring Data bọc EntityManager thế nào. {@code save()} (S11), {@code delete(detached)} (S06c),
 * transaction readOnly (S12) và self-invocation (S14).
 *
 * <p>Điểm cần nhớ: mỗi method của repository tự có {@code @Transactional}, nên gọi repository ngoài
 * transaction vẫn chạy (mỗi lần gọi là một transaction riêng) và entity trả về đã DETACHED.
 * Các test ở đây gọi repository/service TRỰC TIẾP (không dùng {@code inTx}) vì cần đúng ranh giới transaction đó.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class SpringDataSaveTest extends AbstractScenarioTest {

	private static final String CUSTOMER = "g0_lifecycle.customer";
	private static final String SKU_PRODUCT = "g0_lifecycle.sku_product";
	private static final String VERSIONED_SKU_PRODUCT = "g0_lifecycle.versioned_sku_product";

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private SkuProductRepository skuProductRepository;

	@Autowired
	private VersionedSkuProductRepository versionedSkuProductRepository;

	@Autowired
	private CustomerService customerService;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { "g0_lifecycle" };
	}

	/**
	 * G0-S06c — repository.delete(detached): Spring Data tự lo phần "đưa về MANAGED" 🔬
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: {@code em.remove(detached)} bị từ chối (S06b). Còn {@code repository.delete(detached)}?
	 * <p><b>Kết luận</b> (🔬, xem docs/RESULTS.md): Xoá được. Spring Data tự {@code find} lại để có bản
	 * MANAGED (1 SELECT) rồi mới {@code remove} (1 DELETE). Tức repository "che" giúp người dùng lỗi ở S06b,
	 * đổi lại tốn thêm 1 SELECT.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S06c | repository.delete(entity DETACHED): ghi lại SQL thực tế")
	void s06c_repositoryDeleteOnDetachedEntity() {
		// ── GIVEN: customer "An" trong DB, và bản DETACHED của nó ──
		Customer detachedCustomer = customerRepository.save(new Customer("An", "an@example.com"));
		assertState(em, detachedCustomer, DETACHED);
		assertThat(db.countOf(CUSTOMER)).as("điều kiện đầu: có 1 customer").isEqualTo(1);

		// ── WHEN: xoá bằng repository (không có inTx: delete tự mở transaction riêng) ──
		sql.reset();
		customerRepository.delete(detachedCustomer);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 1); // Spring Data load lại entity để có bản MANAGED
		sql.assertDeletes("customer", 1);
		sql.assertInOrder("select", "delete from g0_lifecycle.customer");

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		assertThat(db.countOf(CUSTOMER)).as("DELETE đã commit nên dòng biến mất").isZero();
	}

	/**
	 * G0-S11a — save() với id tự sinh: isNew() = true → persist, chỉ 1 INSERT
	 *
	 * <p>Mapping : g0_lifecycle.customer (SEQUENCE, id kiểu Long)
	 * <p><b>Câu hỏi</b>: {@code save(new Customer())} chạy persist hay merge?
	 * <p><b>Kết luận</b>: Id là {@code null} nên Spring Data coi là entity MỚI và gọi {@code persist}:
	 * 1 INSERT, không SELECT thừa, và trả về CHÍNH object truyền vào.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S11a | save(entity id tự sinh): persist, chỉ INSERT")
	void s11a_saveWithGeneratedIdPersists() {
		// ── GIVEN: object mới, id còn null ──
		Customer newCustomer = new Customer("An", "an@example.com");

		// ── WHEN: save (repository tự mở và commit transaction) ──
		sql.reset();
		Customer returnedCustomer = customerRepository.save(newCustomer);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 0);
		sql.assertInserts("customer", 1);
		assertThat(returnedCustomer).as("persist dùng lại chính object truyền vào").isSameAs(newCustomer);

		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		assertThat(db.countOf(CUSTOMER)).as("INSERT đã commit").isEqualTo(1);
	}

	/**
	 * G0-S11b — save() với id GÁN TAY, không @Version: isNew() = false → merge, SELECT thừa rồi mới INSERT
	 *
	 * <p>Mapping : g0_lifecycle.sku_product (id String gán tay, không {@code @Version})
	 * <p><b>Câu hỏi</b>: {@code save(new SkuProduct("SKU-1", ...))} chạy persist hay merge?
	 * <p><b>Kết luận</b>: Id đã khác {@code null} nên Spring Data đoán đây là entity CŨ và gọi {@code merge}:
	 * SELECT (kiểm tra có dòng chưa) rồi mới INSERT, tức thừa 1 SELECT. Giá trị trả về là bản MANAGED KHÁC
	 * object truyền vào (xem S05). Khắc phục: thêm {@code @Version} (S11c) hoặc implement {@code Persistable}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S11b | save(entity id gán tay, không @Version): merge → SELECT thừa rồi INSERT")
	void s11b_saveWithAssignedIdMergesWithExtraSelect() {
		// ── GIVEN: object mới nhưng id đã được gán tay ──
		SkuProduct newProduct = new SkuProduct("SKU-1", "Bàn phím");

		// ── WHEN: save ──
		sql.reset();
		SkuProduct returnedProduct = skuProductRepository.save(newProduct);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("sku_product", 1); // thừa: merge phải hỏi DB xem đã có dòng chưa
		sql.assertInserts("sku_product", 1);
		sql.assertInOrder("select", "insert into g0_lifecycle.sku_product");
		assertThat(returnedProduct).as("merge trả về bản MANAGED khác object truyền vào").isNotSameAs(newProduct);

		// ── THEN: DB thật ──
		db.print(SKU_PRODUCT);
		assertThat(db.countOf(SKU_PRODUCT)).as("merge không thấy dòng nên INSERT").isEqualTo(1);
	}

	/**
	 * G0-S11c — save() với id gán tay CÓ @Version: version == null → isNew() = true → persist, chỉ INSERT
	 *
	 * <p>Mapping : g0_lifecycle.versioned_sku_product (id String gán tay + {@code @Version Long})
	 * <p><b>Câu hỏi</b>: Thêm {@code @Version} thì {@code save()} có còn SELECT thừa không?
	 * <p><b>Kết luận</b>: Không. Với entity có {@code @Version} kiểu wrapper, Spring Data dùng {@code version == null}
	 * để nhận biết entity mới nên gọi {@code persist}: chỉ 1 INSERT, trả về chính object truyền vào,
	 * cột version khởi đầu bằng 0.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S11c | save(entity id gán tay + @Version): persist, chỉ INSERT")
	void s11c_saveWithAssignedIdAndVersionPersists() {
		// ── GIVEN: id gán tay, version còn null ──
		VersionedSkuProduct newProduct = new VersionedSkuProduct("SKU-2", "Chuột");

		// ── WHEN: save ──
		sql.reset();
		VersionedSkuProduct returnedProduct = versionedSkuProductRepository.save(newProduct);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("versioned_sku_product", 0);
		sql.assertInserts("versioned_sku_product", 1);
		assertThat(returnedProduct).as("persist dùng lại chính object truyền vào").isSameAs(newProduct);

		// ── THEN: DB thật ──
		db.print(VERSIONED_SKU_PRODUCT);
		assertThat(db.rowById(VERSIONED_SKU_PRODUCT, "SKU-2").get("version"))
			.as("Hibernate khởi tạo version = 0 lúc INSERT")
			.isEqualTo(0L);
	}

	/**
	 * G0-S12 — @Transactional(readOnly = true) bỏ qua dirty checking
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: Service chỉ-đọc tải customer rồi {@code setName}. Có UPDATE không?
	 * <p><b>Kết luận</b>: Không. Với {@code readOnly = true} Spring đặt session ở chế độ chỉ đọc (flush tắt, entity
	 * không được chụp ảnh để so sánh), nên thay đổi bị bỏ qua lặng lẽ. Dễ gây hiểu nhầm: không có lỗi nào, chỉ là không lưu.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S12 | @Transactional(readOnly = true): sửa entity nhưng 0 UPDATE")
	void s12_readOnlyTransactionSkipsDirtyChecking() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = customerRepository.save(new Customer("An", "an@example.com")).getId();

		// ── WHEN: service readOnly tải customer và sửa tên ──
		sql.reset();
		customerService.renameInReadOnlyTransaction(customerId, "X");

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 1);
		sql.assertUpdates("customer", 0);

		// ── THEN: DB thật ──
		assertThat(db.rowById(CUSTOMER, customerId).get("name"))
			.as("readOnly tắt dirty checking nên tên không đổi")
			.isEqualTo("An");
	}

	/**
	 * G0-S14a — Gọi @Transactional method QUA proxy (từ bean khác): chạy đúng, có UPDATE
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: Làm đối chứng cho S14b: gọi {@code renameInTransaction} từ bên ngoài bean thì sao?
	 * <p><b>Kết luận</b>: Lệnh gọi đi qua proxy của Spring nên transaction được mở; customer MANAGED,
	 * dirty checking sinh UPDATE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S14a | Gọi method @Transactional qua proxy: có transaction, có UPDATE")
	void s14a_callThroughProxyOpensTransaction() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = customerRepository.save(new Customer("An", "an@example.com")).getId();

		// ── WHEN: gọi từ test (bean khác) nên đi qua proxy ──
		sql.reset();
		customerService.renameInTransaction(customerId, "X");

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertUpdates("customer", 1);
		assertThat(db.rowById(CUSTOMER, customerId).get("name"))
			.as("proxy đã mở transaction nên dirty checking ghi tên mới")
			.isEqualTo("X");
	}

	/**
	 * G0-S14b — Self-invocation: {@code this.inner()} bỏ qua proxy nên mất @Transactional
	 *
	 * <p>Mapping : g0_lifecycle.customer
	 * <p><b>Câu hỏi</b>: {@code outer()} (không transaction) gọi {@code this.inner()} (có {@code @Transactional}). Có UPDATE không?
	 * <p><b>Kết luận</b>: Không. Gọi qua {@code this} không đi qua proxy nên annotation bị bỏ qua: không có
	 * transaction bao ngoài. {@code findById} tự mở transaction riêng rồi đóng, customer thành DETACHED trước
	 * {@code setName}, nên thay đổi không được ghi (giống S04). Nếu chạm vào quan hệ LAZY sẽ là LazyInitializationException.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G0-S14b | Self-invocation mất @Transactional: 0 UPDATE")
	void s14b_selfInvocationBypassesTransactionalProxy() {
		// ── GIVEN: customer "An" trong DB ──
		Long customerId = customerRepository.save(new Customer("An", "an@example.com")).getId();

		// ── WHEN: gọi outer() → this.inner() ──
		sql.reset();
		customerService.renameViaSelfInvocation(customerId, "X");

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertSelects("customer", 1);
		sql.assertUpdates("customer", 0);
		assertThat(db.rowById(CUSTOMER, customerId).get("name"))
			.as("không có transaction bao ngoài nên entity DETACHED, thay đổi không xuống DB")
			.isEqualTo("An");
	}
}
