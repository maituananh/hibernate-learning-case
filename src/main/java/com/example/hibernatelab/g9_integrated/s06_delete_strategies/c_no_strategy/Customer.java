package com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khách hàng (ông) của chuỗi Customer → PurchaseOrder → OrderItem.
 *
 * BIẾN THỂ : G9-S06C (g9_s06c_no_strategy)
 * BASELINE : G9-S06c (không chiến lược nào)
 * KHÁC BIỆT: không cascade JPA, không @OnDelete: baseline trần
 * HẬU QUẢ  : xoá customer còn order → vi phạm FK (S06d); muốn xoá phải bulk delete từ dưới lên (S06c)
 */
@Entity(name = "G9S06C_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s06c_no_strategy", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s06c_customer_seq")
	@SequenceGenerator(name = "g9s06c_customer_seq", schema = "g9_s06c_no_strategy", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	// KHÔNG cascade: em.remove(customer) không đụng tới order.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<PurchaseOrder> orders = new ArrayList<>();

	protected Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<PurchaseOrder> getOrders() {
		return orders;
	}

	public void addOrder(PurchaseOrder order) {
		orders.add(order);
		order.setCustomer(this);
	}
}
