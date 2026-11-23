package com.example.hibernatelab.g7_many_to_many.m9_join_entity_surrogate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Kho hàng: đầu thứ hai của quan hệ N—N Product — Warehouse (qua StockItem).
 *
 * BIẾN THỂ : M9 (g7_m9_join_entity_surrogate)
 * BASELINE : M8
 * KHÁC BIỆT: như M8 nhưng StockItem có id SEQUENCE riêng, cặp (product_id, warehouse_id) chỉ bị ràng buộc bằng UNIQUE
 * HẬU QUẢ  : bảng khác muốn trỏ tới StockItem chỉ cần FK 1 cột; đổi lại phải tự giữ UNIQUE (G7-S12)
 */
@Entity(name = "G7M9_Warehouse") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m9_join_entity_surrogate", name = "warehouse")
public class M9Warehouse {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m9_warehouse_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m9_warehouse_seq", schema = "g7_m9_join_entity_surrogate", sequenceName = "warehouse_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected M9Warehouse() {
		// JPA bắt buộc có constructor không tham số
	}

	public M9Warehouse(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
