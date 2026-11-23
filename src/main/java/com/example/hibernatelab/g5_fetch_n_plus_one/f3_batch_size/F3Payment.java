package com.example.hibernatelab.g5_fetch_n_plus_one.f3_batch_size;

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
 * Khoản thanh toán của biến thể F3.
 *
 * BIẾN THỂ : F3 (g5_f3_batch_size)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 + @BatchSize(size = 10) trên collection và trên entity
 * HẬU QUẢ  : N+1 gom thành 1 + ceil(5/10) = 2 SELECT (G5-S08)
 */
@Entity(name = "G5F3_Payment") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f3_batch_size", name = "payment")
public class F3Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f3_payment_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f3_payment_seq", schema = "g5_f3_batch_size", sequenceName = "payment_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK payment.customer_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private F3Customer customer;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	protected F3Payment() {
		// JPA bắt buộc có constructor không tham số
	}

	public Long getId() {
		return id;
	}

	public F3Customer getCustomer() {
		return customer;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}
