package com.example.hibernatelab.g4_update_child_via_parent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0Customer;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0OrderItem;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0Product;
import com.example.hibernatelab.g4_update_child_via_parent.u0_baseline.U0PurchaseOrder;
import com.example.hibernatelab.g4_update_child_via_parent.u4_dynamic_update.U4Customer;
import com.example.hibernatelab.g4_update_child_via_parent.u4_dynamic_update.U4OrderItem;
import com.example.hibernatelab.g4_update_child_via_parent.u4_dynamic_update.U4Product;
import com.example.hibernatelab.g4_update_child_via_parent.u4_dynamic_update.U4PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G4, {@code @DynamicUpdate} (S10): câu {@code UPDATE order_item} liệt kê TẤT CẢ cột (U0) hay chỉ cột đã đổi (U4)?
 *
 * <p>Mặc định Hibernate sinh sẵn MỘT câu UPDATE cho mỗi entity lúc khởi động (liệt kê mọi cột) để tái dùng PreparedStatement.
 * {@code @DynamicUpdate} đổi sang sinh câu UPDATE lúc flush, chỉ chứa cột thực sự đổi.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G4DynamicUpdateTest extends AbstractScenarioTest {

	private static final String U0 = "g4_u0_baseline";
	private static final String U4 = "g4_u4_dynamic_update";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { U0, U4 };
	}

	/**
	 * G4-S10a — U0 (không {@code @DynamicUpdate}): chỉ sửa quantity
	 *
	 * <p>Mapping: {@code OrderItem} không có {@code @DynamicUpdate}.
	 * <p><b>Câu hỏi</b>: sửa đúng 1 field thì câu UPDATE chứa những cột nào?
	 * <p><b>Kết luận</b>: TẤT CẢ cột: {@code set order_id=?,product_id=?,quantity=?,unit_price=?}. Ghi lại cả những cột không đổi.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S10a | U0: không @DynamicUpdate thì UPDATE order_item ghi TẤT CẢ cột dù chỉ đổi quantity")
	void s10a_withoutDynamicUpdateAllColumnsAreWritten() {
		// ── GIVEN ──
		U0Customer customerAn = new U0Customer("An");
		U0Product productPen = new U0Product("PEN");
		U0PurchaseOrder orderA = new U0PurchaseOrder("A-001", "NEW");
		orderA.setCustomer(customerAn);
		orderA.addItem(new U0OrderItem(productPen, 1, new BigDecimal("10.00")));
		Long orderId = inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(productPen);
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN: chỉ sửa quantity ──
		sql.reset();
		inTx(() -> em.find(U0PurchaseOrder.class, orderId).getItems().get(0).setQuantity(5));

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);
		sql.assertSomeStatementContains("set order_id=?,product_id=?,quantity=?,unit_price=? where id=?");

		// ── THEN: DB thật ──
		assertThat(db.rowsOf(U0 + ".order_item").get(0).get("quantity")).as("quantity đã đổi thành 5").isEqualTo(5);
	}

	/**
	 * G4-S10b — U4 ({@code @DynamicUpdate}): chỉ sửa quantity
	 *
	 * <p>Mapping: {@code OrderItem} có {@code @DynamicUpdate}.
	 * <p><b>Câu hỏi</b>: cùng thao tác như S10a, câu UPDATE khác ở đâu?
	 * <p><b>Kết luận</b>: chỉ {@code set quantity=? where id=?}. Đổi lại Hibernate phải sinh câu SQL mới mỗi lần flush
	 * (không tái dùng được câu đã chuẩn bị); chỉ đáng dùng khi bảng nhiều cột hoặc có cột lớn.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G4-S10b | U4: có @DynamicUpdate thì UPDATE order_item chỉ ghi cột quantity")
	void s10b_withDynamicUpdateOnlyChangedColumnIsWritten() {
		// ── GIVEN ──
		U4Customer customerAn = new U4Customer("An");
		U4Product productPen = new U4Product("PEN");
		U4PurchaseOrder orderA = new U4PurchaseOrder("A-001", "NEW");
		orderA.setCustomer(customerAn);
		orderA.addItem(new U4OrderItem(productPen, 1, new BigDecimal("10.00")));
		Long orderId = inTxReturning(() -> {
			em.persist(customerAn);
			em.persist(productPen);
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN: chỉ sửa quantity ──
		sql.reset();
		inTx(() -> em.find(U4PurchaseOrder.class, orderId).getItems().get(0).setQuantity(5));

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("order_item", 1);
		sql.assertSomeStatementContains("set quantity=? where id=?");
		sql.assertNoStatementContains("unit_price=?");

		// ── THEN: DB thật ──
		assertThat(db.rowsOf(U4 + ".order_item").get(0).get("quantity")).as("quantity đã đổi thành 5").isEqualTo(5);
	}
}
