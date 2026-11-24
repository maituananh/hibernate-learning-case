package com.example.hibernatelab.g12_advanced_model.h3_table_per_class;

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
 * Lớp cha TRỪU TƯỢNG của hai cách thanh toán (thẻ, chuyển khoản). Mỗi lớp CỤ THỂ một bảng chứa đủ mọi cột (kể cả cột thừa kế).
 *
 * BIẾN THỂ : H3 (g12_h3_table_per_class)
 * BASELINE : H1 (SINGLE_TABLE)
 * KHÁC BIỆT: chiến lược TABLE_PER_CLASS thay vì SINGLE_TABLE
 * HẬU QUẢ  : không có bảng cho lớp cha trừu tượng; card_payment và bank_payment mỗi bảng lặp lại cột chung; query đa hình = union all (G12-S08)
 */
@Entity(name = "G12H3_Payment")
@Table(schema = "g12_h3_table_per_class", name = "payment")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class H3Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12h3_payment_seq")
	@SequenceGenerator(name = "g12h3_payment_seq", schema = "g12_h3_table_per_class", sequenceName = "payment_seq", allocationSize = 50)
	private Long id;

	@Column(name = "amount", nullable = false)
	private long amount;

	protected H3Payment() {
		// JPA bắt buộc có constructor không tham số
	}

	protected H3Payment(long amount) {
		this.amount = amount;
	}

	public Long getId() {
		return id;
	}

	public long getAmount() {
		return amount;
	}
}
