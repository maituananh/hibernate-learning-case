package com.example.hibernatelab.g9_integrated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g9_integrated.s04_recursive_tostring.OrderItem;
import com.example.hibernatelab.g9_integrated.s04_recursive_tostring.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G9-S04 ➕ {@code toString()} đệ quy trong quan hệ hai chiều (đúng bẫy của Lombok {@code @Data}/{@code @ToString}).
 * Không cần DB: chỉ dựng đối tượng trong bộ nhớ, nên cũng không có SQL nào để assert.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G9S04RecursiveToStringTest extends AbstractScenarioTest {

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { "g9_s04_recursive_tostring" };
	}

	/**
	 * G9-S04a — toString() in quan hệ ở cả hai phía
	 *
	 * <p><b>Câu hỏi</b>: gọi {@code toString()} trên order đã nối hai chiều với item thì sao?
	 * <p><b>Kết luận</b>: {@code StackOverflowError}: order in items, mỗi item in lại order, order lại in items... không bao giờ dừng.
	 * Lỗi xuất hiện cả khi chỉ ghi log (log.info("order = {}", order)). Hibernate không liên quan; đây là lỗi thiết kế toString().
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S04a | toString() in cả hai phía quan hệ hai chiều: StackOverflowError")
	void s04a_toStringPrintingBothSidesOfBidirectionalRelationOverflowsStack() {
		// ── GIVEN ──
		PurchaseOrder orderA = new PurchaseOrder("A-001");
		orderA.addItem(new OrderItem("PEN-01")); // nối hai chiều: order.items chứa item, item.order trỏ lại order

		// ── WHEN + THEN ──
		assertThatThrownBy(orderA::toString)
			.as("order.toString() → items.toString() → item.toString() → order.toString() → ... vòng lặp vô hạn")
			.isInstanceOf(StackOverflowError.class);
	}

	/**
	 * G9-S04b — Cách sửa: toString() chỉ in field của chính mình
	 *
	 * <p><b>Câu hỏi</b>: sửa thế nào?
	 * <p><b>Kết luận</b>: không in quan hệ (hoặc chỉ in id của nó). Cũng tránh được việc toString() vô tình kích hoạt lazy loading.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S04b | toString() không in quan hệ: an toàn")
	void s04b_toStringWithoutRelationsIsSafe() {
		// ── GIVEN ──
		PurchaseOrder orderA = new PurchaseOrder("A-001");
		OrderItem itemPen = new OrderItem("PEN-01");
		orderA.addItem(itemPen);

		// ── WHEN ──
		String orderText = orderA.toSafeString();
		String itemText = itemPen.toSafeString();

		// ── THEN ──
		assertThat(orderText).as("chỉ có field của chính order, không có items").isEqualTo("PurchaseOrder{id=null, orderNo=A-001}");
		assertThat(itemText).as("chỉ có field của chính item, không có order").isEqualTo("OrderItem{id=null, sku=PEN-01}");
	}
}
