package com.example.hibernatelab.g1_owning_side.v3_o2m_uni_joincolumn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V3 — {@code @OneToMany + @JoinColumn} một chiều (xem {@link Customer})
 * BASELINE : V1 (g1_v1_m2o_uni) — chỉ có @ManyToOne ở PurchaseOrder, Customer không có collection
 * KHÁC BIỆT: PurchaseOrder KHÔNG có field {@code customer}, nhưng bảng vẫn có cột {@code customer_id}
 * HẬU QUẢ  : class PurchaseOrder không hề "biết" mình thuộc customer nào, dù cột FK nằm trong bảng của nó.
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G1V3_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v3_o2m_uni_joincolumn", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v3_purchase_order_seq")
	@SequenceGenerator(name = "g1v3_purchase_order_seq", schema = "g1_v3_o2m_uni_joincolumn", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// cố ý KHÔNG có field customer — cột purchase_order.customer_id do Customer.orders (@JoinColumn) tạo ra và ghi.

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
