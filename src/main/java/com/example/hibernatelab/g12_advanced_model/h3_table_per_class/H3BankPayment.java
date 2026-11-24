package com.example.hibernatelab.g12_advanced_model.h3_table_per_class;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Thanh toán chuyển khoản: cột riêng {@code bank_name}. Xem header ở H3Payment. */
@Entity(name = "G12H3_BankPayment")
@Table(schema = "g12_h3_table_per_class", name = "bank_payment")
public class H3BankPayment extends H3Payment {

	@Column(name = "bank_name", nullable = false)
	private String bankName;

	protected H3BankPayment() {
		// JPA bắt buộc có constructor không tham số
	}

	public H3BankPayment(long amount, String bankName) {
		super(amount);
		this.bankName = bankName;
	}

	public String getBankName() {
		return bankName;
	}
}
