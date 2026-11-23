package com.example.hibernatelab.g8_composite_fk.j3_idclass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Kho hàng với {@code @IdClass}: hai field {@code @Id} nằm THẲNG trong entity (không có object id lồng bên trong).
 *
 * BIẾN THỂ : J3 (g8_j3_idclass)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: Warehouse dùng @IdClass (hai @Id nằm thẳng trong entity) thay vì @EmbeddedId; Shipment bỏ lines (S01 chỉ cần so DDL và find)
 * HẬU QUẢ  : DDL y hệt J1; find nhận một object J3WarehouseKey; code truy cập id là warehouse.getCountryCode() thay vì getId().getCountryCode()
 */
@Entity(name = "G8J3_Warehouse") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j3_idclass", name = "warehouse")
@IdClass(J3WarehouseKey.class) // tên field trong J3WarehouseKey phải trùng tên field @Id bên dưới
public class J3Warehouse {

	@Id
	@Column(name = "country_code", length = 2)
	private String countryCode;

	@Id
	@Column(name = "code", length = 10)
	private String code;

	@Column(name = "name", nullable = false)
	private String name;

	protected J3Warehouse() {
		// JPA bắt buộc có constructor không tham số
	}

	public J3Warehouse(String countryCode, String code, String name) {
		this.countryCode = countryCode;
		this.code = code;
		this.name = name;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}
}
