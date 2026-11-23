package com.example.hibernatelab.g1_owning_side.v2_o2m_uni_jointable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V2 — {@code @OneToMany} một chiều không {@code @JoinColumn} (xem {@link Customer})
 * BASELINE : V1 (g1_v1_m2o_uni) — chỉ có @ManyToOne ở PurchaseOrder, Customer không có collection
 * KHÁC BIỆT: PurchaseOrder KHÔNG có field {@code customer}
 * HẬU QUẢ  : bảng {@code purchase_order} không có cột {@code customer_id}; quan hệ nằm hẳn trong bảng nối.
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G1V2_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v2_o2m_uni_jointable", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v2_purchase_order_seq")
	@SequenceGenerator(name = "g1v2_purchase_order_seq", schema = "g1_v2_o2m_uni_jointable", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// cố ý KHÔNG có field customer: quan hệ chỉ khai báo ở Customer.orders (bảng nối customer_orders).

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
