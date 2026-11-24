package com.example.hibernatelab.g13_performance.p1_pc_stateless;

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
 * Đơn hàng của {@link P1Customer}.
 *
 * BIẾN THỂ : P1 (g13_p1_pc_stateless)
 * BASELINE : chính nó
 * KHÁC BIỆT: không có (đây là baseline)
 * HẬU QUẢ  : {@code customer} LAZY để S05 thấy StatelessSession không tải được quan hệ lazy
 */
@Entity(name = "G13P1_Order")
@Table(schema = "g13_p1_pc_stateless", name = "purchase_order")
public class P1Order {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13p1_order_seq")
	@SequenceGenerator(name = "g13p1_order_seq", schema = "g13_p1_pc_stateless", sequenceName = "order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side: giữ FK customer_id. LAZY tường minh.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private P1Customer customer;

	protected P1Order() {
		// JPA bắt buộc có constructor không tham số
	}

	public P1Order(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public P1Customer getCustomer() {
		return customer;
	}

	void setCustomer(P1Customer customer) {
		this.customer = customer;
	}
}
