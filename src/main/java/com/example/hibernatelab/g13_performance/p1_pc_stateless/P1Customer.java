package com.example.hibernatelab.g13_performance.p1_pc_stateless;

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
 * Khách hàng dùng cho G13-S04 (vòng lặp xử lý nhiều dòng) và G13-S05 (StatelessSession).
 *
 * BIẾN THỂ : P1 (g13_p1_pc_stateless)
 * BASELINE : chính nó (baseline của chủ đề persistence context / StatelessSession)
 * KHÁC BIỆT: không có (đây là baseline)
 * HẬU QUẢ  : có {@code orders} LAZY + cascade PERSIST để S05 chứng minh StatelessSession bỏ qua cả cascade lẫn lazy loading
 */
@Entity(name = "G13P1_Customer")
@Table(schema = "g13_p1_pc_stateless", name = "customer")
public class P1Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13p1_customer_seq")
	@SequenceGenerator(name = "g13p1_customer_seq", schema = "g13_p1_pc_stateless", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "status", nullable = false)
	private String status;

	// INVERSE side (FK customer_id nằm ở bảng purchase_order). LAZY tường minh; cascade PERSIST để thấy StatelessSession không cascade.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private List<P1Order> orders = new ArrayList<>();

	protected P1Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public P1Customer(String name, String status) {
		this.name = name;
		this.status = status;
	}

	public void addOrder(P1Order order) {
		orders.add(order);
		order.setCustomer(this);
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public List<P1Order> getOrders() {
		return orders;
	}
}
