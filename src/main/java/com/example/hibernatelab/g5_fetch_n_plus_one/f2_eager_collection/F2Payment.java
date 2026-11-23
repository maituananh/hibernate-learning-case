package com.example.hibernatelab.g5_fetch_n_plus_one.f2_eager_collection;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khoản thanh toán của biến thể F2.
 *
 * BIẾN THỂ : F2 (g5_f2_eager_collection)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: Customer.orders = EAGER (anti-pattern), phần còn lại như F1
 * HẬU QUẢ  : find(customer) luôn tải orders; findAll() tự sinh N+1 (G5-S13)
 */
@Entity(name = "G5F2_Payment") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f2_eager_collection", name = "payment")
public class F2Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f2_payment_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f2_payment_seq", schema = "g5_f2_eager_collection", sequenceName = "payment_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK payment.customer_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private F2Customer customer;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	protected F2Payment() {
		// JPA bắt buộc có constructor không tham số
	}

	public Long getId() {
		return id;
	}

	public F2Customer getCustomer() {
		return customer;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}
