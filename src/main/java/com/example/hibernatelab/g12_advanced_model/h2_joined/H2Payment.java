package com.example.hibernatelab.g12_advanced_model.h2_joined;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Lớp cha TRỪU TƯỢNG của hai cách thanh toán (thẻ, chuyển khoản). Mỗi lớp một bảng, lớp con nối với lớp cha qua PK.
 *
 * BIẾN THỂ : H2 (g12_h2_joined)
 * BASELINE : H1 (SINGLE_TABLE)
 * KHÁC BIỆT: chiến lược JOINED thay vì SINGLE_TABLE
 * HẬU QUẢ  : bảng payment (cột chung) + card_payment + bank_payment (PK vừa là FK tới payment); cột lớp con được NOT NULL; query đa hình LEFT JOIN mọi bảng lớp con (G12-S07)
 */
@Entity(name = "G12H2_Payment")
@Table(schema = "g12_h2_joined", name = "payment")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class H2Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12h2_payment_seq")
	@SequenceGenerator(name = "g12h2_payment_seq", schema = "g12_h2_joined", sequenceName = "payment_seq", allocationSize = 50)
	private Long id;

	@Column(name = "amount", nullable = false)
	private long amount;

	protected H2Payment() {
		// JPA bắt buộc có constructor không tham số
	}

	protected H2Payment(long amount) {
		this.amount = amount;
	}

	public Long getId() {
		return id;
	}

	public long getAmount() {
		return amount;
	}
}
