package com.example.hibernatelab.g9_integrated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.hibernatelab.g9_integrated.s07_tx_boundary.OrderItem;
import com.example.hibernatelab.g9_integrated.s07_tx_boundary.OrderQueryService;
import com.example.hibernatelab.g9_integrated.s07_tx_boundary.OrderSummary;
import com.example.hibernatelab.g9_integrated.s07_tx_boundary.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G9-S07 ⭐ Ranh giới transaction trong service ({@link OrderQueryService}).
 *
 * <p>Mapping: {@code PurchaseOrder.items} là LAZY (INVERSE). {@code open-in-view = false} nên ngoài transaction không còn persistence context,
 * chạm collection chưa nạp là {@code LazyInitializationException}.
 * <p>Bốn tình huống: (a) service không transaction, (b) self-invocation, (c) trả về entity, (d) trả về DTO.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G9S07TransactionBoundaryTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g9_s07_tx_boundary";

	@Autowired
	private OrderQueryService orderQueryService;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/** Lưu order A-001 có 2 item (PEN-01, PEN-02) và trả về id. */
	private Long seedOrderWithTwoItems() {
		PurchaseOrder orderA = new PurchaseOrder("A-001");
		orderA.addItem(new OrderItem("PEN-01"));
		orderA.addItem(new OrderItem("PEN-02"));
		return inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});
	}

	/**
	 * G9-S07a — Service KHÔNG có transaction: tải order rồi chạm quan hệ lazy
	 *
	 * <p><b>Câu hỏi</b>: {@code findById} rồi {@code getItems().size()} trong một method không {@code @Transactional} thì sao?
	 * <p><b>Kết luận</b>: {@code LazyInitializationException}. {@code find} tự mở transaction ngắn rồi đóng ngay, order thành DETACHED trước khi chạm items.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S07a | Service không @Transactional: chạm items lazy → LazyInitializationException")
	void s07a_serviceWithoutTransactionTouchingLazyCollectionFails() {
		// ── GIVEN ──
		Long orderId = seedOrderWithTwoItems();

		// ── WHEN + THEN ──
		assertThatThrownBy(() -> orderQueryService.countItemsWithoutTransaction(orderId))
			.as("find() xong là persistence context đóng, collection LAZY chưa nạp không còn session để nạp")
			.isInstanceOf(LazyInitializationException.class);
	}

	/**
	 * G9-S07b — Self-invocation: {@code this.method()} bỏ qua proxy nên mất {@code @Transactional}
	 *
	 * <p><b>Câu hỏi</b>: gọi method {@code @Transactional} từ một method khác của CÙNG bean thì có transaction không?
	 * <p><b>Kết luận</b>: không. Gọi từ bên ngoài (qua proxy) → có transaction; gọi qua {@code this} → không. (Nối với G0-S14.)
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S07b | Self-invocation: gọi qua proxy có transaction, gọi qua this thì không")
	void s07b_selfInvocationLosesTransactionalAnnotation() {
		// ── WHEN + THEN ──
		assertThat(orderQueryService.isTransactionActive())
			.as("gọi từ test (bên ngoài bean) đi qua proxy nên @Transactional có hiệu lực").isTrue();
		assertThat(orderQueryService.isTransactionActiveViaSelfInvocation())
			.as("method ngoài không có transaction và gọi this.isTransactionActive() bỏ qua proxy → vẫn không có transaction").isFalse();
	}

	/**
	 * G9-S07c — Service có {@code @Transactional} nhưng TRẢ VỀ ENTITY
	 *
	 * <p><b>Câu hỏi</b>: transaction trong service đã đóng khi trả về, caller chạm quan hệ lazy thì sao?
	 * <p><b>Kết luận</b>: {@code LazyInitializationException}. Transaction kết thúc ở cuối method service, entity trả ra là DETACHED.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S07c | Service @Transactional trả về entity: caller chạm items lazy → LazyInitializationException")
	void s07c_returningEntityFromTransactionalServiceLeavesLazyCollectionUnusable() {
		// ── GIVEN ──
		Long orderId = seedOrderWithTwoItems();

		// ── WHEN ──
		PurchaseOrder detachedOrder = orderQueryService.findOrderEntity(orderId);
		// [TX END] transaction của service kết thúc → detachedOrder: MANAGED → DETACHED, items chưa nạp

		// ── THEN ──
		assertThat(detachedOrder.getOrderNo()).as("field thường đã nạp sẵn nên đọc được ngoài transaction").isEqualTo("A-001");
		assertThatThrownBy(() -> detachedOrder.getItems().size())
			.as("items là LAZY và chưa nạp, persistence context đã đóng")
			.isInstanceOf(LazyInitializationException.class);
	}

	/**
	 * G9-S07d — Service {@code @Transactional} trả về DTO
	 *
	 * <p><b>Câu hỏi</b>: làm sao trả dữ liệu của cả items ra ngoài an toàn?
	 * <p><b>Kết luận</b>: đọc xong mọi thứ cần dùng BÊN TRONG transaction rồi sao chép sang DTO thuần ({@code record}); caller không cần persistence context.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S07d | Service @Transactional trả về DTO: caller đọc an toàn, không cần persistence context")
	void s07d_returningDtoFromTransactionalServiceIsSafe() {
		// ── GIVEN ──
		Long orderId = seedOrderWithTwoItems();

		// ── WHEN ──
		sql.reset();
		OrderSummary summary = orderQueryService.findOrderSummary(orderId);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects(SCHEMA + ".purchase_order", 1); // find order
		sql.assertSelects(SCHEMA + ".order_item", 1); // nạp items lazy ngay trong transaction

		// ── THEN: kết quả ──
		assertThat(summary.orderNo()).as("DTO mang số đơn").isEqualTo("A-001");
		assertThat(summary.itemCount()).as("DTO mang số item, đọc xong trong transaction").isEqualTo(2);
		assertThat(summary.skus()).as("DTO mang danh sách sku thuần, không có proxy").containsExactlyInAnyOrder("PEN-01", "PEN-02");
	}
}
