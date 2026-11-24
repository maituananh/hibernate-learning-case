package com.example.hibernatelab.g13_performance.b1_seq_batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Dòng hàng hoá đơn giản dùng để insert hàng loạt.
 *
 * BIẾN THỂ : B1 (g13_b1_seq_batch)
 * BASELINE : B1 (SEQUENCE, allocationSize = 50)
 * KHÁC BIỆT: không có (baseline)
 * HẬU QUẢ  : SEQUENCE + batch size đặt theo session thì gom được INSERT (G13-S01); allocationSize 50 nên ít gọi nextval (G13-S03)
 */
@Entity(name = "G13B1_Item")
@Table(schema = "g13_b1_seq_batch", name = "item")
public class B1Item {

	// SEQUENCE: Hibernate lấy id từ sequence TRƯỚC khi INSERT nên không phải INSERT ngay để biết id
	// → có thể gom nhiều INSERT vào một batch JDBC. allocationSize = 50: mỗi lần hỏi sequence được cấp 50 id.
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13b1_item_seq")
	@SequenceGenerator(name = "g13b1_item_seq", schema = "g13_b1_seq_batch", sequenceName = "item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected B1Item() {
		// JPA bắt buộc có constructor không tham số
	}

	public B1Item(String name, int quantity) {
		this.name = name;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getQuantity() {
		return quantity;
	}
}
