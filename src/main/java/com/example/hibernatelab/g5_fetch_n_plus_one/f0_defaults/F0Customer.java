package com.example.hibernatelab.g5_fetch_n_plus_one.f0_defaults;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khách hàng của biến thể F0.
 *
 * BIẾN THỂ : F0 (g5_f0_defaults)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: KHÔNG ghi `fetch` ở bất kỳ quan hệ nào: dùng mặc định JPA (@ManyToOne = EAGER, @OneToMany = LAZY)
 * HẬU QUẢ  : @ManyToOne EAGER nên find(order) JOIN luôn customer, còn JPQL thì sinh N+1 (G5-S01, S02)
 */
@Entity(name = "G5F0_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f0_defaults", name = "customer")
public class F0Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f0_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f0_customer_seq", schema = "g5_f0_defaults", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	// fetch: CỐ Ý để mặc định. Mặc định của @OneToMany là LAZY.
	@OneToMany(mappedBy = "customer")
	private List<F0PurchaseOrder> orders = new ArrayList<>();

	// INVERSE side — mappedBy = "customer": FK payment.customer_id do Payment.customer quản lý.
	// fetch: CỐ Ý để mặc định. Mặc định của @OneToMany là LAZY.
	@OneToMany(mappedBy = "customer")
	private List<F0Payment> payments = new ArrayList<>();

	protected F0Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public F0Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<F0PurchaseOrder> getOrders() {
		return orders;
	}

	public List<F0Payment> getPayments() {
		return payments;
	}
}
