package com.example.hibernatelab.g5_fetch_n_plus_one.f2_eager_collection;

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
 * Khách hàng của biến thể F2.
 *
 * BIẾN THỂ : F2 (g5_f2_eager_collection)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: Customer.orders = EAGER (anti-pattern), phần còn lại như F1
 * HẬU QUẢ  : find(customer) luôn tải orders; findAll() tự sinh N+1 (G5-S13)
 */
@Entity(name = "G5F2_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f2_eager_collection", name = "customer")
public class F2Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f2_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f2_customer_seq", schema = "g5_f2_eager_collection", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	// fetch = EAGER: anti-pattern, find(customer) nào cũng kéo luôn toàn bộ orders.
	@OneToMany(mappedBy = "customer", fetch = FetchType.EAGER)
	private List<F2PurchaseOrder> orders = new ArrayList<>();

	// INVERSE side — mappedBy = "customer": FK payment.customer_id do Payment.customer quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<F2Payment> payments = new ArrayList<>();

	protected F2Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public F2Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<F2PurchaseOrder> getOrders() {
		return orders;
	}

	public List<F2Payment> getPayments() {
		return payments;
	}
}
