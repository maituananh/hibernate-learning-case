package com.example.hibernatelab.g12_advanced_model.h2_joined;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Thanh toán bằng thẻ: cột riêng {@code card_last4}. Xem header ở H2Payment. */
@Entity(name = "G12H2_CardPayment")
@Table(schema = "g12_h2_joined", name = "card_payment")
public class H2CardPayment extends H2Payment {

	// Bảng riêng của lớp con nên nullable = false được DDL giữ nguyên (khác SINGLE_TABLE ở H1).
	@Column(name = "card_last4", nullable = false)
	private String cardLast4;

	protected H2CardPayment() {
		// JPA bắt buộc có constructor không tham số
	}

	public H2CardPayment(long amount, String cardLast4) {
		super(amount);
		this.cardLast4 = cardLast4;
	}

	public String getCardLast4() {
		return cardLast4;
	}
}
