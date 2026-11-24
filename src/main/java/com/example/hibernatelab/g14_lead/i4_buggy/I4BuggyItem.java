package com.example.hibernatelab.g14_lead.i4_buggy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dòng dữ liệu nhập từ file import.
 *
 * BIẾN THỂ : I4 (buggy), schema g14_i4_buggy
 * BASELINE : không có (G14 được miễn quy tắc một khác biệt, E7)
 * KHÁC BIỆT: id IDENTITY
 * HẬU QUẢ  : persist là INSERT ngay nên batch bị tắt (G13-S02): N dòng = N lần INSERT; cùng với service không flush/clear,
 *            persistence context giữ cả N entity
 */
@Entity(name = "G14I4B_Item") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g14_i4_buggy", name = "item")
public class I4BuggyItem {

	// IDENTITY: id do DB sinh lúc INSERT nên Hibernate phải INSERT ngay khi persist để biết id → không thể gom lô.
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected I4BuggyItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public I4BuggyItem(String name, int quantity) {
		this.name = name;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}
}
