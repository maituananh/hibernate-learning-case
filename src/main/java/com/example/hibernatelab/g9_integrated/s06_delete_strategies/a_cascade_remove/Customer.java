package com.example.hibernatelab.g9_integrated.s06_delete_strategies.a_cascade_remove;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
 * BIẾN THỂ : G9-S06A (g9_s06a_cascade_remove)
 * BASELINE : G9-S06c (không chiến lược nào)
 * KHÁC BIỆT: cascade = REMOVE ở Customer.orders và PurchaseOrder.items (JPA tự xoá theo chuỗi)
 * HẬU QUẢ  : Hibernate TẢI từng collection rồi DELETE từng dòng, con trước cha: nhiều SELECT và nhiều DELETE (G9-S06a)
 */
@Entity(name = "G9S06A_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s06a_cascade_remove", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s06a_customer_seq")
	@SequenceGenerator(name = "g9s06a_customer_seq", schema = "g9_s06a_cascade_remove", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	// cascade = REMOVE: em.remove(customer) lan xuống từng order (Hibernate phải TẢI collection này để biết xoá những order nào).
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
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
