package com.example.hibernatelab.g12_advanced_model.h1_single_table;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Lớp cha TRỪU TƯỢNG của hai cách thanh toán (thẻ, chuyển khoản). Cả hệ phân cấp nằm trong MỘT bảng.
 *
 * BIẾN THỂ : H1 (g12_h1_single_table)
 * BASELINE : H1 (SINGLE_TABLE)
 * KHÁC BIỆT: (baseline)
 * HẬU QUẢ  : một bảng payment cho cả cây; cột payment_type phân biệt lớp con; cột của lớp con đều nullable; query đa hình = 1 SELECT không join (G12-S06)
 */
@Entity(name = "G12H1_Payment")
@Table(schema = "g12_h1_single_table", name = "payment")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "payment_type", discriminatorType = DiscriminatorType.STRING)
public abstract class H1Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12h1_payment_seq")
	@SequenceGenerator(name = "g12h1_payment_seq", schema = "g12_h1_single_table", sequenceName = "payment_seq", allocationSize = 50)
	private Long id;

	@Column(name = "amount", nullable = false)
	private long amount;

	protected H1Payment() {
		// JPA bắt buộc có constructor không tham số
	}

	protected H1Payment(long amount) {
		this.amount = amount;
	}

	public Long getId() {
		return id;
	}

	public long getAmount() {
		return amount;
	}
}
