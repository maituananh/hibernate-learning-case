package com.example.hibernatelab.g12_advanced_model.h1_single_table;

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
 * Biên lai trỏ tới {@link H1Payment} (lớp cha TRỪU TƯỢNG) bằng {@code @ManyToOne LAZY}, dùng cho G12-S09.
 *
 * <p>Điểm bẫy: khi tải biên lai, Hibernate chưa biết payment trỏ tới là thẻ hay chuyển khoản
 * (cần SELECT mới biết), nên proxy phải tạo từ lớp CHA, không thể là proxy của lớp con.
 */
@Entity(name = "G12H1_Receipt")
@Table(schema = "g12_h1_single_table", name = "receipt")
public class H1Receipt {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12h1_receipt_seq")
	@SequenceGenerator(name = "g12h1_receipt_seq", schema = "g12_h1_single_table", sequenceName = "receipt_seq", allocationSize = 50)
	private Long id;

	@Column(name = "receipt_no", nullable = false)
	private String receiptNo;

	// OWNING side: FK payment_id nằm ở bảng receipt.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id", nullable = false)
	private H1Payment payment;

	protected H1Receipt() {
		// JPA bắt buộc có constructor không tham số
	}

	public H1Receipt(String receiptNo, H1Payment payment) {
		this.receiptNo = receiptNo;
		this.payment = payment;
	}

	public Long getId() {
		return id;
	}

	public H1Payment getPayment() {
		return payment;
	}
}
