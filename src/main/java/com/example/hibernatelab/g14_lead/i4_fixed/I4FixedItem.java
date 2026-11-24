package com.example.hibernatelab.g14_lead.i4_fixed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Dòng dữ liệu nhập từ file import.
 *
 * BIẾN THỂ : I4 (fixed), schema g14_i4_fixed
 * BASELINE : I4 buggy
 * KHÁC BIỆT: id SEQUENCE (allocationSize = 50) thay vì IDENTITY
 * HẬU QUẢ  : persist không INSERT ngay, Hibernate gom INSERT thành lô khi flush (G13-S01a)
 */
@Entity(name = "G14I4F_Item")
@Table(schema = "g14_i4_fixed", name = "item")
public class I4FixedItem {

	// Id lấy từ sequence TRƯỚC khi INSERT, nên Hibernate được phép trì hoãn và gom lô. allocationSize = 50 để 50 id chỉ tốn 1 lần hỏi DB.
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i4fixed_item_seq")
	@SequenceGenerator(name = "g14i4fixed_item_seq", schema = "g14_i4_fixed", sequenceName = "item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected I4FixedItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public I4FixedItem(String name, int quantity) {
		this.name = name;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}
}
