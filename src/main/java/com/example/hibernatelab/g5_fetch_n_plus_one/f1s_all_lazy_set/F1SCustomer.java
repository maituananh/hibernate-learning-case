package com.example.hibernatelab.g5_fetch_n_plus_one.f1s_all_lazy_set;

import java.util.LinkedHashSet;
import java.util.Set;

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
 * Khách hàng của biến thể F1S.
 *
 * BIẾN THỂ : F1S (g5_f1s_all_lazy_set)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 nhưng collection là Set thay vì List
 * HẬU QUẢ  : Set không phải 'bag' nên fetch 2 collection cùng lúc không bị MultipleBagFetchException, nhưng sinh tích Descartes (G5-S11)
 */
@Entity(name = "G5F1S_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f1s_all_lazy_set", name = "customer")
public class F1SCustomer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f1s_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f1s_customer_seq", schema = "g5_f1s_all_lazy_set", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private Set<F1SPurchaseOrder> orders = new LinkedHashSet<>();

	// INVERSE side — mappedBy = "customer": FK payment.customer_id do Payment.customer quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private Set<F1SPayment> payments = new LinkedHashSet<>();

	protected F1SCustomer() {
		// JPA bắt buộc có constructor không tham số
	}

	public F1SCustomer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<F1SPurchaseOrder> getOrders() {
		return orders;
	}

	public Set<F1SPayment> getPayments() {
		return payments;
	}
}
