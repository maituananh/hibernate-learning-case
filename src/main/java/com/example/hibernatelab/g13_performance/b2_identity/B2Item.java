package com.example.hibernatelab.g13_performance.b2_identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dòng hàng hoá đơn giản dùng để insert hàng loạt.
 *
 * BIẾN THỂ : B2 (g13_b2_identity)
 * BASELINE : B1 (SEQUENCE, allocationSize = 50)
 * KHÁC BIỆT: id IDENTITY thay vì SEQUENCE
 * HẬU QUẢ  : persist là INSERT ngay, batch size bị bỏ qua: N entity = N lần INSERT (G13-S02)
 */
@Entity(name = "G13B2_Item")
@Table(schema = "g13_b2_identity", name = "item")
public class B2Item {

	// IDENTITY: id do DB sinh lúc INSERT (cột identity), nên Hibernate PHẢI INSERT ngay khi persist để biết id.
	// Hệ quả: không thể trì hoãn và gom INSERT → JDBC batching bị tắt cho entity này.
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected B2Item() {
		// JPA bắt buộc có constructor không tham số
	}

	public B2Item(String name, int quantity) {
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
