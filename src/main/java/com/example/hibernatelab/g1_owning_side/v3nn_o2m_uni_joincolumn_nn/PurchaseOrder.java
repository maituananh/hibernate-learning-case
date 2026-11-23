package com.example.hibernatelab.g1_owning_side.v3nn_o2m_uni_joincolumn_nn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V3-NN — xem {@link Customer}
 * BASELINE : V3 (g1_v3_o2m_uni_joincolumn)
 * KHÁC BIỆT: không có ở class này (khác biệt nằm ở {@code Customer.orders})
 * HẬU QUẢ  : như V3, PurchaseOrder không có field customer.
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G1V3NN_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v3nn_o2m_uni_joincolumn_nn", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v3nn_purchase_order_seq")
	@SequenceGenerator(name = "g1v3nn_purchase_order_seq", schema = "g1_v3nn_o2m_uni_joincolumn_nn", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// cố ý KHÔNG có field customer — cột customer_id (NOT NULL) do Customer.orders tạo ra và ghi.

	protected PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}
}
