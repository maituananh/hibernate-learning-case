package com.example.hibernatelab.g12_advanced_model.h1_single_table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;

/** Thanh toán chuyển khoản: cột riêng {@code bank_name}. Xem header ở H1Payment. */
@Entity(name = "G12H1_BankPayment")
@DiscriminatorValue("BANK")
public class H1BankPayment extends H1Payment {

	// SINGLE_TABLE: để nullable vì dòng CARD không có bank_name (xem H1CardPayment).
	@Column(name = "bank_name", nullable = true)
	private String bankName;

	protected H1BankPayment() {
		// JPA bắt buộc có constructor không tham số
	}

	public H1BankPayment(long amount, String bankName) {
		super(amount);
		this.bankName = bankName;
	}

	public String getBankName() {
		return bankName;
	}
}
