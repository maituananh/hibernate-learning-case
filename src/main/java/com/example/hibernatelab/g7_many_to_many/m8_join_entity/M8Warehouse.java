package com.example.hibernatelab.g7_many_to_many.m8_join_entity;

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
 * BIẾN THỂ : M8 (g7_m8_join_entity)
 * BASELINE : M1 (đổi từ @ManyToMany sang join entity)
 * KHÁC BIỆT: bỏ @ManyToMany; StockItem là entity trung gian có thêm cột quantity, PK ghép @EmbeddedId (productId, warehouseId) suy ra từ 2 @MapsId
 * HẬU QUẢ  : thêm/sửa/xoá số lượng tồn là thao tác trên entity StockItem; sửa quantity chỉ UPDATE stock_item (G7-S10)
 */
@Entity(name = "G7M8_Warehouse") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m8_join_entity", name = "warehouse")
public class M8Warehouse {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m8_warehouse_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m8_warehouse_seq", schema = "g7_m8_join_entity", sequenceName = "warehouse_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected M8Warehouse() {
		// JPA bắt buộc có constructor không tham số
	}

	public M8Warehouse(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
