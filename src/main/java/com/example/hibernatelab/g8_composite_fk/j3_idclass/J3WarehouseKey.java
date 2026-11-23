package com.example.hibernatelab.g8_composite_fk.j3_idclass;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lớp id cho {@code @IdClass}: KHÔNG có annotation, chỉ cần tên và kiểu field khớp với các {@code @Id} trong entity.
 * Vẫn phải {@code Serializable} và có {@code equals/hashCode} theo giá trị.
 *
 * BIẾN THỂ : J3 (g8_j3_idclass)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: Warehouse dùng @IdClass (hai @Id nằm thẳng trong entity) thay vì @EmbeddedId; Shipment bỏ lines (S01 chỉ cần so DDL và find)
 * HẬU QUẢ  : DDL y hệt J1; find nhận một object J3WarehouseKey; code truy cập id là warehouse.getCountryCode() thay vì getId().getCountryCode()
 */
public class J3WarehouseKey implements Serializable {

	private String countryCode;

	private String code;

	protected J3WarehouseKey() {
		// JPA bắt buộc có constructor không tham số
	}

	public J3WarehouseKey(String countryCode, String code) {
		this.countryCode = countryCode;
		this.code = code;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof J3WarehouseKey that)) {
			return false;
		}
		return Objects.equals(countryCode, that.countryCode) && Objects.equals(code, that.code);
	}

	@Override
	public int hashCode() {
		return Objects.hash(countryCode, code);
	}
}
