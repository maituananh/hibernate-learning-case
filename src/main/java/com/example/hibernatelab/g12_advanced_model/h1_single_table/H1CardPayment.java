package com.example.hibernatelab.g12_advanced_model.h1_single_table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;

/** Thanh toán bằng thẻ: cột riêng {@code card_last4}. Xem header ở H1Payment. */
@Entity(name = "G12H1_CardPayment")
@DiscriminatorValue("CARD")
public class H1CardPayment extends H1Payment {

	// SINGLE_TABLE: BẮT BUỘC để nullable (mặc định). Hibernate 7.4 KHÔNG bỏ qua nullable = false: nó sinh NOT NULL,
	// khiến INSERT dòng BANK (card_last4 trống) vỡ ràng buộc. Muốn bắt buộc thì kiểm tra ở Java (G12-S06a).
	@Column(name = "card_last4", nullable = true)
	private String cardLast4;

	protected H1CardPayment() {
		// JPA bắt buộc có constructor không tham số
	}

	public H1CardPayment(long amount, String cardLast4) {
		super(amount);
		this.cardLast4 = cardLast4;
	}

	public String getCardLast4() {
		return cardLast4;
	}
}
