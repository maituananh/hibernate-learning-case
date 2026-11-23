package com.example.hibernatelab.g9_integrated;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.hibernatelab.g9_integrated.s02_order_update.Customer;
import com.example.hibernatelab.g9_integrated.s02_order_update.OrderItem;
import com.example.hibernatelab.g9_integrated.s02_order_update.OrderUpdateRequest;
import com.example.hibernatelab.g9_integrated.s02_order_update.OrderUpdateRequest.ItemRequest;
import com.example.hibernatelab.g9_integrated.s02_order_update.OrderUpdateService;
import com.example.hibernatelab.g9_integrated.s02_order_update.Product;
import com.example.hibernatelab.g9_integrated.s02_order_update.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G9-S02 ⭐ "Cập nhật đơn hàng từ request" (DTO → entity) qua {@link OrderUpdateService}.
 *
 * <p>Request: sửa số lượng item PEN (có id), thêm item MOUSE (không id), bỏ item BOOK (vắng mặt → phải bị xoá).
 * Request KHÔNG chứa status, createdAt, customer: đó là dữ liệu client không được sửa.
 * <p>So hai cách: A = tải order MANAGED rồi áp diff (khuyến nghị); B = {@code new} entity từ DTO rồi merge (bẫy).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G9S02OrderUpdateTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g9_s02_order_update";
	private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired
	private OrderUpdateService orderUpdateService;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/** Lưu order A-001 (status PAID, createdAt cố định, customer An) có 2 item: PEN × 1 (10.00) và BOOK × 2 (20.00). Trả về orderId. */
	private Long seedOrder() {
		Customer customerAn = new Customer("An");
		Product productPen = new Product("PEN");
		Product productBook = new Product("BOOK");
		Product productMouse = new Product("MOUSE");
		PurchaseOrder orderA = new PurchaseOrder("A-001", "PAID");
		orderA.setCustomer(customerAn);
		orderA.setCreatedAt(CREATED_AT);
		orderA.setNote("ghi chú cũ");
		orderA.addItem(new OrderItem(productPen, 1, new BigDecimal("10.00")));
		orderA.addItem(new OrderItem(productBook, 2, new BigDecimal("20.00")));
		orderA.recalculateTotal();
		return inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(productPen);
			em.persist(productBook);
			em.persist(productMouse);
			em.persist(orderA);
			return orderA.getId();
		});
	}

	private Long productId(String name) {
		for (Map<String, Object> product : db.rowsOf(SCHEMA + ".product")) {
			if (name.equals(product.get("name"))) {
				return ((Number) product.get("id")).longValue();
			}
		}
		throw new AssertionError("Không có product " + name);
	}

	/** Id của order_item theo tên product (JDBC). */
	private Long itemIdOfProduct(String productName) {
		Long productId = productId(productName);
		for (Map<String, Object> item : db.rowsOf(SCHEMA + ".order_item")) {
			if (productId.equals(((Number) item.get("product_id")).longValue())) {
				return ((Number) item.get("id")).longValue();
			}
		}
		throw new AssertionError("Không có item của " + productName);
	}

	/** Request mô tả trạng thái MONG MUỐN: PEN × 5 (sửa), MOUSE × 1 (thêm mới), BOOK vắng mặt (xoá). */
	private OrderUpdateRequest buildRequest(Long orderId) {
		Long version = ((Number) db.rowById(SCHEMA + ".purchase_order", orderId).get("version")).longValue();
		List<ItemRequest> items = new ArrayList<>();
		items.add(new ItemRequest(itemIdOfProduct("PEN"), productId("PEN"), 5, new BigDecimal("10.00")));
		items.add(new ItemRequest(null, productId("MOUSE"), 1, new BigDecimal("15.00")));
		return new OrderUpdateRequest(orderId, version, "A-001", "giao nhanh", items);
	}

	/**
	 * G9-S02a — Cách A: tải order MANAGED rồi áp diff
	 *
	 * <p>Mapping: {@code PurchaseOrder.items} INVERSE, cascade ALL, orphanRemoval; {@code OrderItem.order} OWNING.
	 * <p><b>Câu hỏi</b>: sau khi cập nhật, các cột KHÔNG có trong request (status, createdAt, customer) còn giữ nguyên không?
	 * <p><b>Kết luận</b>: có. Chỉ field được gán mới đổi; item mới INSERT, item vắng mặt DELETE (orphanRemoval), item có id UPDATE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S02a | Cách A (tải rồi áp diff): giữ nguyên status/createdAt/customer, sửa-thêm-xoá item đúng")
	void s02a_applyingDiffToManagedOrderKeepsColumnsNotInRequest() {
		// ── GIVEN ──
		Long orderId = seedOrder();
		Map<String, Object> rowBefore = db.rowSnapshot(SCHEMA + ".purchase_order", orderId);
		OrderUpdateRequest request = buildRequest(orderId);

		// ── WHEN ──
		sql.reset();
		orderUpdateService.updateByDiff(request);
		// [TX END] transaction của service kết thúc → order và các item: MANAGED → DETACHED

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates(SCHEMA + ".purchase_order", 1);
		sql.assertUpdates(SCHEMA + ".order_item", 1);
		sql.assertInserts(SCHEMA + ".order_item", 1);
		sql.assertDeletes(SCHEMA + ".order_item", 1);

		// ── THEN: DB thật ──
		db.print(SCHEMA + ".purchase_order");
		db.print(SCHEMA + ".order_item");
		Map<String, Object> rowAfter = db.rowById(SCHEMA + ".purchase_order", orderId);
		assertThat(rowAfter.get("status")).as("status không có trong request nên cách A không đụng tới").isEqualTo("PAID");
		assertThat(rowAfter.get("created_at")).as("createdAt không có trong request nên giữ nguyên").isEqualTo(rowBefore.get("created_at"));
		assertThat(rowAfter.get("customer_id")).as("customer không có trong request nên FK giữ nguyên").isEqualTo(rowBefore.get("customer_id"));
		assertThat(rowAfter.get("note")).as("note có trong request nên đã được cập nhật").isEqualTo("giao nhanh");
		assertThat((BigDecimal) rowAfter.get("total")).as("total tính lại: 5 × 10.00 + 1 × 15.00").isEqualByComparingTo("65.00");
		assertThat(db.countOf(SCHEMA + ".order_item")).as("PEN (sửa) + MOUSE (thêm) còn lại; BOOK bị orphanRemoval xoá").isEqualTo(2);
	}

	/**
	 * G9-S02b — Cách B (BẪY): {@code new} entity từ DTO rồi merge
	 *
	 * <p><b>Câu hỏi</b>: cùng request như S02a nhưng dựng entity mới + merge thì các cột không có trong request ra sao?
	 * <p><b>Kết luận</b>: merge() copy TOÀN BỘ state của bản "mới" đè lên bản trong DB nên status, createdAt, customer_id, total thành NULL,
	 * không lỗi nào báo. Đây là lỗi mất dữ liệu âm thầm. Số câu SQL ghi giống cách A: lỗi chỉ lộ ra khi đọc dữ liệu thật.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S02b | Cách B (new + merge): status/createdAt/customer/total bị ghi đè thành NULL")
	void s02b_mergingEntityRebuiltFromDtoOverwritesMissingColumnsWithNull() {
		// ── GIVEN ──
		Long orderId = seedOrder();
		OrderUpdateRequest request = buildRequest(orderId);

		// ── WHEN ──
		sql.reset();
		orderUpdateService.updateByMerge(request);
		// [TX END] merge() trả về bản MANAGED (khác bản rebuilt); commit → UPDATE ghi cả những field null

		// ── THEN: SQL ──
		sql.print();
		// Số câu ghi GIỐNG HỆT cách A: Hibernate mặc định UPDATE mọi cột, chỉ khác GIÁ TRỊ bind (NULL thay vì giá trị cũ).
		// Vì vậy chỉ đếm SQL không phát hiện được lỗi này; phải đọc dữ liệu thật.
		sql.assertUpdates(SCHEMA + ".purchase_order", 1);
		sql.assertUpdates(SCHEMA + ".order_item", 1);
		sql.assertInserts(SCHEMA + ".order_item", 1);
		sql.assertDeletes(SCHEMA + ".order_item", 1);
		sql.assertSelects(SCHEMA + ".purchase_order", 1); // merge tải bản cũ (kèm items bằng left join) để so với bản mới

		// ── THEN: DB thật ──
		db.print(SCHEMA + ".purchase_order");
		Map<String, Object> rowAfter = db.rowById(SCHEMA + ".purchase_order", orderId);
		assertThat(rowAfter.get("note")).as("note có trong request nên vẫn được cập nhật đúng").isEqualTo("giao nhanh");
		assertThat(rowAfter.get("status")).as("BẪY: status không có trong DTO → null đè lên 'PAID'").isNull();
		assertThat(rowAfter.get("created_at")).as("BẪY: createdAt không có trong DTO → mất ngày tạo đơn").isNull();
		assertThat(rowAfter.get("customer_id")).as("BẪY: customer không có trong DTO → đơn mất khách hàng").isNull();
		assertThat(rowAfter.get("total")).as("BẪY: total không có trong DTO → mất tổng tiền").isNull();
	}
}
