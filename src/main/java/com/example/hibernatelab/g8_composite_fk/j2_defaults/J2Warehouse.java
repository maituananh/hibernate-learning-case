package com.example.hibernatelab.g8_composite_fk.j2_defaults;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Kho hàng, PK ghép {@code @EmbeddedId (country_code, code)}. Id do người dùng GÁN TAY (không có sequence).
 *
 * BIẾN THỂ : J2 (g8_j2_defaults)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: bỏ @JoinColumns, chỉ còn @ManyToOne trơn; Shipment bỏ cả lines (S02 chỉ xem tên cột FK mặc định)
 * HẬU QUẢ  : tên hai cột FK do naming strategy quyết định (G8-S02, kết quả ghi ở docs/RESULTS.md)
 */
@Entity(name = "G8J2_Warehouse") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j2_defaults", name = "warehouse")
public class J2Warehouse {

	@EmbeddedId
	private J2WarehouseId id;

	@Column(name = "name", nullable = false)
	private String name;

	protected J2Warehouse() {
		// JPA bắt buộc có constructor không tham số
	}

	public J2Warehouse(J2WarehouseId id, String name) {
		this.id = id;
		this.name = name;
	}

	public J2WarehouseId getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
