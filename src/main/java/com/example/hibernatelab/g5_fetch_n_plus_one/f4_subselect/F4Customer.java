package com.example.hibernatelab.g5_fetch_n_plus_one.f4_subselect;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

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
 * Khách hàng của biến thể F4.
 *
 * BIẾN THỂ : F4 (g5_f4_subselect)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 + @Fetch(FetchMode.SUBSELECT) trên Customer.orders
 * HẬU QUẢ  : N+1 gom thành 2 SELECT, câu thứ hai lặp lại query gốc dưới dạng subquery (G5-S09)
 */
@Entity(name = "G5F4_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f4_subselect", name = "customer")
public class F4Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f4_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f4_customer_seq", schema = "g5_f4_subselect", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	// fetch = LAZY ghi tường minh.
	@Fetch(FetchMode.SUBSELECT) // tải orders của MỌI customer bằng 1 câu, lặp lại query gốc làm subquery
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<F4PurchaseOrder> orders = new ArrayList<>();

	// INVERSE side — mappedBy = "customer": FK payment.customer_id do Payment.customer quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<F4Payment> payments = new ArrayList<>();

	protected F4Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public F4Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<F4PurchaseOrder> getOrders() {
		return orders;
	}

	public List<F4Payment> getPayments() {
		return payments;
	}
}
