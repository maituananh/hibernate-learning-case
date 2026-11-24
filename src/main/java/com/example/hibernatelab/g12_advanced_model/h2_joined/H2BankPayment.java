package com.example.hibernatelab.g12_advanced_model.h2_joined;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Thanh toán chuyển khoản: cột riêng {@code bank_name}. Xem header ở H2Payment. */
@Entity(name = "G12H2_BankPayment")
@Table(schema = "g12_h2_joined", name = "bank_payment")
public class H2BankPayment extends H2Payment {

	@Column(name = "bank_name", nullable = false)
	private String bankName;

	protected H2BankPayment() {
		// JPA bắt buộc có constructor không tham số
	}

	public H2BankPayment(long amount, String bankName) {
		super(amount);
		this.bankName = bankName;
	}

	public String getBankName() {
		return bankName;
	}
}
