package com.example.hibernatelab.g5_fetch_n_plus_one.f3_batch_size;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

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
 * Khách hàng của biến thể F3.
 *
 * BIẾN THỂ : F3 (g5_f3_batch_size)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 + @BatchSize(size = 10) trên collection và trên entity
 * HẬU QUẢ  : N+1 gom thành 1 + ceil(5/10) = 2 SELECT (G5-S08)
 */
@Entity(name = "G5F3_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f3_batch_size", name = "customer")
@BatchSize(size = 10) // gom việc tải PROXY của entity này theo lô 10
public class F3Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f3_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f3_customer_seq", schema = "g5_f3_batch_size", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	// fetch = LAZY ghi tường minh.
	@BatchSize(size = 10) // gom việc tải COLLECTION này theo lô 10 owner
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<F3PurchaseOrder> orders = new ArrayList<>();

	// INVERSE side — mappedBy = "customer": FK payment.customer_id do Payment.customer quản lý.
	// fetch = LAZY ghi tường minh.
	@BatchSize(size = 10) // gom việc tải COLLECTION này theo lô 10 owner
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<F3Payment> payments = new ArrayList<>();

	protected F3Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public F3Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<F3PurchaseOrder> getOrders() {
		return orders;
	}

	public List<F3Payment> getPayments() {
		return payments;
	}
}
