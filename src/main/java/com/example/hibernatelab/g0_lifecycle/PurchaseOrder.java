package com.example.hibernatelab.g0_lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đơn hàng của nhóm G0. Chỉ có MỘT quan hệ ({@code @ManyToOne customer}) để phục vụ G0-S08:
 * gán FK bằng {@code getReference()} mà không cần SELECT customer.
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G0_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g0_lifecycle", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g0_purchase_order_seq")
	@SequenceGenerator(name = "g0_purchase_order_seq", schema = "g0_lifecycle", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — giữ FK purchase_order.customer_id (bảng này là bảng chứa cột FK).
	// fetch = LAZY ghi tường minh: khi đọc order, customer chỉ là proxy cho tới lúc chạm vào field.
	// cố ý KHÔNG có cascade: persist order sẽ KHÔNG tự persist customer (G0 chưa học cascade, xem nhóm G2).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
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
