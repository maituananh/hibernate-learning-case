package com.example.hibernatelab.g1_owning_side.v1nn_m2o_nullable_false;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V1-NN — {@code @ManyToOne} + {@code @JoinColumn(nullable = false)}
 * BASELINE : V1 (g1_v1_m2o_uni) — chỉ có @ManyToOne ở PurchaseOrder, Customer không có collection
 * KHÁC BIỆT: thêm {@code nullable = false} vào {@code @JoinColumn}
 * HẬU QUẢ  : cột {@code customer_id} thành NOT NULL trong DDL. 🔬 Ở Hibernate 7.4.15, Hibernate CŨNG đọc
 *            {@code nullable = false}: chặn order thiếu customer trước khi gửi SQL và tải bằng INNER JOIN (G1-S04b, S04e).
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G1V1NN_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v1nn_m2o_nullable_false", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v1nn_purchase_order_seq")
	@SequenceGenerator(name = "g1v1nn_purchase_order_seq", schema = "g1_v1nn_m2o_nullable_false", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — FK purchase_order.customer_id. nullable = false → cột NOT NULL.
	// cố ý KHÔNG ghi fetch → mặc định EAGER; cố ý KHÔNG ghi optional → mặc định optional = true.
	@ManyToOne
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

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

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}
}
