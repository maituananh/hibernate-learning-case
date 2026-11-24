package com.example.hibernatelab.g13_performance.b3_seq_alloc1;

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
 * BIẾN THỂ : B3 (g13_b3_seq_alloc1)
 * BASELINE : B1 (SEQUENCE, allocationSize = 50)
 * KHÁC BIỆT: allocationSize = 1 thay vì 50
 * HẬU QUẢ  : mỗi entity mới tốn một lần gọi nextval xuống DB (G13-S03)
 */
@Entity(name = "G13B3_Item")
@Table(schema = "g13_b3_seq_alloc1", name = "item")
public class B3Item {

	// SEQUENCE: Hibernate lấy id từ sequence TRƯỚC khi INSERT nên không phải INSERT ngay để biết id
	// → có thể gom nhiều INSERT vào một batch JDBC. allocationSize = 1: mỗi lần hỏi sequence được cấp 1 id.
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13b3_item_seq")
	@SequenceGenerator(name = "g13b3_item_seq", schema = "g13_b3_seq_alloc1", sequenceName = "item_seq", allocationSize = 1)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected B3Item() {
		// JPA bắt buộc có constructor không tham số
	}

	public B3Item(String name, int quantity) {
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
