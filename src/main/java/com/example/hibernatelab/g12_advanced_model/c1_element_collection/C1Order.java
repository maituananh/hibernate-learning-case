package com.example.hibernatelab.g12_advanced_model.c1_element_collection;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đơn hàng có danh sách ghi chú là {@code @ElementCollection List<String>} (BAG).
 *
 * BIẾN THỂ : C1 (bag)
 * BASELINE : C1 (bag: List<String> không có thứ tự cột)
 * KHÁC BIỆT: (baseline)
 * HẬU QUẢ  : bảng con c1_order_note KHÔNG có PK; bỏ 1 phần tử thì DELETE hết rồi INSERT lại các phần tử còn lại (G12-S04)
 */
@Entity(name = "G12C1Order")
@Table(schema = "g12_c1_element_collection", name = "c1_order")
public class C1Order {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12c1_order_seq")
	@SequenceGenerator(name = "g12c1_order_seq", schema = "g12_c1_element_collection", sequenceName = "c1_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// Phần tử là kiểu cơ bản (String), KHÔNG phải entity: không có id riêng, không cascade, không query riêng được.
	// OWNING side duy nhất là entity này; bảng con chỉ có FK order_id.
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(schema = "g12_c1_element_collection", name = "c1_order_note", joinColumns = @JoinColumn(name = "order_id"))
	@Column(name = "note", nullable = false)
	private List<String> notes = new ArrayList<>();

	protected C1Order() {
		// JPA bắt buộc có constructor không tham số
	}

	public C1Order(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public List<String> getNotes() {
		return notes;
	}
}
