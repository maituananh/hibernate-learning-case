package com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete;

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
 * BIẾN THỂ : G9-S06B (g9_s06b_on_delete)
 * BASELINE : G9-S06c (không chiến lược nào)
 * KHÁC BIỆT: @OnDelete(CASCADE) trên PurchaseOrder.customer và OrderItem.order; KHÔNG cascade JPA
 * HẬU QUẢ  : chỉ 1 DELETE customer, PostgreSQL xoá dây chuyền; Hibernate không biết con đã mất (G9-S06b)
 */
@Entity(name = "G9S06B_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s06b_on_delete", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s06b_customer_seq")
	@SequenceGenerator(name = "g9s06b_customer_seq", schema = "g9_s06b_on_delete", sequenceName = "customer_seq", allocationSize = 50)
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
