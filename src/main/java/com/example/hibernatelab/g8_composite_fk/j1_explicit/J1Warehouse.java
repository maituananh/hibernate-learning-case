package com.example.hibernatelab.g8_composite_fk.j1_explicit;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Kho hàng, PK ghép {@code @EmbeddedId (country_code, code)}. Id do người dùng GÁN TAY (không có sequence).
 *
 * BIẾN THỂ : J1 (g8_j1_explicit)
 * BASELINE : (đây là baseline của G8)
 * KHÁC BIỆT: FK ghép khai báo tường minh bằng @JoinColumns, mỗi @JoinColumn có name + referencedColumnName
 * HẬU QUẢ  : shipment(wh_country, wh_code) + MỘT FK constraint hai cột tới warehouse(country_code, code) (G8-S01)
 */
@Entity(name = "G8J1_Warehouse") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j1_explicit", name = "warehouse")
public class J1Warehouse {

	@EmbeddedId
	private J1WarehouseId id;

	@Column(name = "name", nullable = false)
	private String name;

	protected J1Warehouse() {
		// JPA bắt buộc có constructor không tham số
	}

	public J1Warehouse(J1WarehouseId id, String name) {
		this.id = id;
		this.name = name;
	}

	public J1WarehouseId getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
