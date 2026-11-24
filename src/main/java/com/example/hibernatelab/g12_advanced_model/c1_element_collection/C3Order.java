package com.example.hibernatelab.g12_advanced_model.c1_element_collection;

import java.util.HashSet;
import java.util.Set;

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
 * Đơn hàng có nhãn là {@code @ElementCollection Set<String>}.
 *
 * BIẾN THỂ : C3 (set)
 * BASELINE : C1 (bag: List<String> không có thứ tự cột)
 * KHÁC BIỆT: kiểu collection là Set thay vì List
 * HẬU QUẢ  : bảng con c3_order_label có PK (order_id, label); bỏ 1 phần tử thì DELETE đúng dòng đó (G12-S05a)
 */
@Entity(name = "G12C3Order")
@Table(schema = "g12_c1_element_collection", name = "c3_order")
public class C3Order {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12c3_order_seq")
	@SequenceGenerator(name = "g12c3_order_seq", schema = "g12_c1_element_collection", sequenceName = "c3_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(schema = "g12_c1_element_collection", name = "c3_order_label", joinColumns = @JoinColumn(name = "order_id"))
	@Column(name = "label", nullable = false)
	private Set<String> labels = new HashSet<>();

	protected C3Order() {
		// JPA bắt buộc có constructor không tham số
	}

	public C3Order(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public Set<String> getLabels() {
		return labels;
	}
}
