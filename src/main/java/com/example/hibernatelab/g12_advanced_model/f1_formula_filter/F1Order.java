package com.example.hibernatelab.g12_advanced_model.f1_formula_filter;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

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
 * Đơn hàng với {@code @Filter} (lọc theo trạng thái), mặc định TẮT. Chỉ có tác dụng sau khi session gọi
 * {@code enableFilter("orderStatus").setParameter("status", ...)}.
 */
@Entity(name = "G12F1_Order")
@Table(schema = "g12_f1_formula_filter", name = "purchase_order")
@FilterDef(name = "orderStatus", parameters = @ParamDef(name = "status", type = String.class))
@Filter(name = "orderStatus", condition = "status = :status")
public class F1Order {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12f1_order_seq")
	@SequenceGenerator(name = "g12f1_order_seq", schema = "g12_f1_formula_filter", sequenceName = "order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	@Column(name = "status", nullable = false)
	private String status;

	// OWNING side: FK customer_id nằm ở bảng purchase_order.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = false)
	private F1Customer customer;

	protected F1Order() {
		// JPA bắt buộc có constructor không tham số
	}

	public F1Order(String orderNo, String status, F1Customer customer) {
		this.orderNo = orderNo;
		this.status = status;
		this.customer = customer;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}
}
