package com.example.hibernatelab.g9_integrated;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.hibernatelab.g9_integrated.s05_bulk_modifying.BulkOrderItemRepository;
import com.example.hibernatelab.g9_integrated.s05_bulk_modifying.BulkProductRepository;
import com.example.hibernatelab.g9_integrated.s05_bulk_modifying.OrderItem;
import com.example.hibernatelab.g9_integrated.s05_bulk_modifying.Product;
import com.example.hibernatelab.g9_integrated.s05_bulk_modifying.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G9-S05 ⭐ Bulk {@code @Modifying} JPQL và persistence context bị lệch với DB.
 *
 * <p>Điểm cần nắm: câu JPQL {@code update}/{@code delete} chạy THẲNG xuống DB, không đi qua persistence context.
 * Entity đã tải không biết DB vừa đổi; cascade REMOVE và orphanRemoval cũng không được kích hoạt.
 * <p>Ở các test này việc đọc giá trị qua entity/EntityManager CHÍNH LÀ thứ đang được kiểm tra (cái cache cấp 1 nói dối);
 * còn dữ liệu thật vẫn đọc bằng JDBC sau khi transaction kết thúc.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G9S05BulkModifyingTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g9_s05_bulk_modifying";

	@Autowired
	private BulkProductRepository productRepository;

	@Autowired
	private BulkOrderItemRepository orderItemRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/** Lưu một product PEN giá 100.00 và trả về id. */
	private Long seedProductPen() {
		Product productPen = new Product("PEN", new BigDecimal("100.00"));
		return inTxReturning(() -> {
			em.persist(productPen);
			return productPen.getId();
		});
	}

	/**
	 * G9-S05a — Bulk update không có clear: giá trong bộ nhớ cũ, DB mới
	 *
	 * <p><b>Câu hỏi</b>: sau {@code update Product set price = price * 1.1}, {@code product.getPrice()} của entity đã tải là bao nhiêu?
	 * <p><b>Kết luận</b>: vẫn 100.00 (giá cũ) trong khi DB đã là 110.00. Persistence context không biết gì về câu UPDATE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S05a | Bulk update không clear: entity giữ giá cũ 100.00, DB đã là 110.00")
	void s05a_bulkUpdateLeavesLoadedEntityStale() {
		// ── GIVEN ──
		Long productId = seedProductPen();

		// ── WHEN ──
		sql.reset();
		BigDecimal priceInMemory = inTxReturning(() -> {
			Product productPen = em.find(Product.class, productId);
			// [LIFECYCLE] productPen: MANAGED, giá 100.00 nằm trong persistence context
			assertState(em, productPen, MANAGED);
			productRepository.raisePricesKeepingPersistenceContext(); // UPDATE chạy ngay xuống DB, persistence context không hay biết
			return productPen.getPrice(); // vẫn là giá lúc tải
			// [TX END] commit: productPen không dirty (giá trong bộ nhớ không đổi) nên KHÔNG có UPDATE nào thêm
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates(SCHEMA + ".product", 1); // đúng 1 câu: chính câu bulk

		// ── THEN: DB thật ──
		assertThat(priceInMemory).as("entity đã tải không biết DB vừa đổi nên vẫn giữ giá cũ").isEqualByComparingTo("100.00");
		assertThat((BigDecimal) db.rowById(SCHEMA + ".product", productId).get("price"))
			.as("DB đã bị bulk update tăng 10%: 100.00 × 1.1").isEqualByComparingTo("110.00");
	}

	/**
	 * G9-S05b — Bulk update với {@code clearAutomatically = true}: đồng bộ lại, nhưng entity cũ thành DETACHED
	 *
	 * <p><b>Câu hỏi</b>: {@code clearAutomatically} sửa được lệch giá trị không, và có cái giá nào?
	 * <p><b>Kết luận</b>: sửa được (tải lại thấy 110.00) nhưng gọi {@code em.clear()}: entity cũ thành DETACHED và mọi thay đổi chưa flush của
	 * MỌI entity khác trong persistence context cũng mất. Chỉ bật khi chắc chắn không còn thay đổi chờ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S05b | Bulk update clearAutomatically: tải lại thấy 110.00 nhưng entity cũ thành DETACHED")
	void s05b_bulkUpdateWithClearAutomaticallySyncsButDetachesEntities() {
		// ── GIVEN ──
		Long productId = seedProductPen();

		// ── WHEN + THEN (trong transaction vì cần quan sát persistence context) ──
		inTx(() -> {
			Product staleProduct = em.find(Product.class, productId);
			assertState(em, staleProduct, MANAGED); // [LIFECYCLE] MANAGED trước khi bulk update
			productRepository.raisePricesAndClearPersistenceContext(); // UPDATE rồi em.clear()
			assertState(em, staleProduct, DETACHED); // [LIFECYCLE] em.clear(): MANAGED → DETACHED
			Product reloaded = em.find(Product.class, productId); // persistence context rỗng nên phải SELECT lại
			assertThat(reloaded.getPrice()).as("tải lại sau clear thấy giá mới từ DB").isEqualByComparingTo("110.00");
			assertThat(staleProduct.getPrice()).as("bản DETACHED cũ vẫn giữ giá cũ: nó không còn được đồng bộ").isEqualByComparingTo("100.00");
		});

		// ── THEN: DB thật ──
		assertThat((BigDecimal) db.rowById(SCHEMA + ".product", productId).get("price"))
			.as("DB có giá mới sau bulk update").isEqualByComparingTo("110.00");
	}

	/**
	 * G9-S05c — Bulk delete {@code OrderItem} bỏ qua orphanRemoval và cascade của cha
	 *
	 * <p>Mapping: {@code PurchaseOrder.items} INVERSE, cascade ALL, orphanRemoval.
	 * <p><b>Câu hỏi</b>: {@code delete from OrderItem where order.id = :id} có đi qua cascade/orphanRemoval không, và order đã tải còn thấy item không?
	 * <p><b>Kết luận</b>: không đi qua gì cả: đúng 1 câu DELETE, DB hết item, nhưng {@code order.getItems()} đã tải trước đó vẫn chứa 2 item "ma".
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S05c | Bulk delete OrderItem: 1 câu DELETE, DB hết item nhưng order.getItems() trong bộ nhớ còn 2")
	void s05c_bulkDeleteBypassesOrphanRemovalAndLeavesStaleCollection() {
		// ── GIVEN ──
		PurchaseOrder orderA = new PurchaseOrder("A-001");
		orderA.addItem(new OrderItem("PEN-01"));
		orderA.addItem(new OrderItem("PEN-02"));
		Long orderId = inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN ──
		sql.reset();
		int[] sizes = new int[2]; // [0] = số item trong bộ nhớ, [1] = số dòng bị bulk delete
		inTx(() -> {
			PurchaseOrder loadedOrder = em.find(PurchaseOrder.class, orderId);
			sizes[0] = loadedOrder.getItems().size(); // nạp collection: 2 item MANAGED trong persistence context
			sizes[1] = orderItemRepository.deleteAllOfOrder(orderId); // 1 câu DELETE thẳng xuống DB
			// [LIFECYCLE] 2 item vẫn MANAGED trong persistence context dù dòng của chúng không còn trong DB
			assertThat(loadedOrder.getItems()).as("collection trong bộ nhớ không được cập nhật theo bulk delete").hasSize(2);
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes(SCHEMA + ".order_item", 1);

		// ── THEN: DB thật ──
		assertThat(sizes[1]).as("câu bulk delete báo đã xoá 2 dòng").isEqualTo(2);
		assertThat(db.countOf(SCHEMA + ".order_item")).as("DB không còn item nào").isEqualTo(0);
		assertThat(db.countOf(SCHEMA + ".purchase_order")).as("order (cha) còn nguyên: bulk delete không đụng tới nó").isEqualTo(1);
	}
}
