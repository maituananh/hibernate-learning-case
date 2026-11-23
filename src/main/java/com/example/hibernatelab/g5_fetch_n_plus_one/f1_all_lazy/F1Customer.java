package com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy;

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
 * Khách hàng của biến thể F1.
 *
 * BIẾN THỂ : F1 (g5_f1_all_lazy)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: mọi quan hệ LAZY ghi tường minh, collection là List (BASELINE, khuyến nghị)
 * HẬU QUẢ  : không tải gì thừa; truy cập quan hệ nào mới tốn SQL đó; ngoài transaction thì LazyInitializationException (G5-S03..S07, S10, S12, S14..S16)
 */
@Entity(name = "G5F1_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f1_all_lazy", name = "customer")
public class F1Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f1_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f1_customer_seq", schema = "g5_f1_all_lazy", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<F1PurchaseOrder> orders = new ArrayList<>();

	// INVERSE side — mappedBy = "customer": FK payment.customer_id do Payment.customer quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<F1Payment> payments = new ArrayList<>();

	protected F1Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public F1Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<F1PurchaseOrder> getOrders() {
		return orders;
	}

	public List<F1Payment> getPayments() {
		return payments;
	}
}
