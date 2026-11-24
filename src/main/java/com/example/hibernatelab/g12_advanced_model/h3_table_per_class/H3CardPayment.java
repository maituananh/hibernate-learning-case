package com.example.hibernatelab.g12_advanced_model.h3_table_per_class;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Thanh toán bằng thẻ: cột riêng {@code card_last4}. Xem header ở H3Payment. */
@Entity(name = "G12H3_CardPayment")
@Table(schema = "g12_h3_table_per_class", name = "card_payment")
public class H3CardPayment extends H3Payment {

	// Bảng riêng của lớp con nên nullable = false được DDL giữ nguyên (khác SINGLE_TABLE ở H1).
	@Column(name = "card_last4", nullable = false)
	private String cardLast4;

	protected H3CardPayment() {
		// JPA bắt buộc có constructor không tham số
	}

	public H3CardPayment(long amount, String cardLast4) {
		super(amount);
		this.cardLast4 = cardLast4;
	}

	public String getCardLast4() {
		return cardLast4;
	}
}
