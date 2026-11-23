package com.example.hibernatelab.g8_composite_fk.j2_defaults;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Khoá chính ghép (countryCode, code) của kho hàng: "VN"+"HN01" là một kho, "SG"+"HN01" là kho khác.
 *
 * <p>Phải {@code Serializable} và có {@code equals/hashCode} theo GIÁ TRỊ (xem giải thích ở G7 {@code M8StockItemId}).
 * Class này CÓ setter để G8-S07 chứng minh: sửa một phần id của entity đang MANAGED là lỗi.
 *
 * BIẾN THỂ : J2 (g8_j2_defaults)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: bỏ @JoinColumns, chỉ còn @ManyToOne trơn; Shipment bỏ cả lines (S02 chỉ xem tên cột FK mặc định)
 * HẬU QUẢ  : tên hai cột FK do naming strategy quyết định (G8-S02, kết quả ghi ở docs/RESULTS.md)
 */
@Embeddable
public class J2WarehouseId implements Serializable {

	@Column(name = "country_code", length = 2)
	private String countryCode;

	@Column(name = "code", length = 10)
	private String code;

	protected J2WarehouseId() {
		// JPA bắt buộc có constructor không tham số
	}

	public J2WarehouseId(String countryCode, String code) {
		this.countryCode = countryCode;
		this.code = code;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getCode() {
		return code;
	}

	/** Chỉ để G8-S07 chứng minh "PK là bất biến": ĐỪNG làm vậy trong code thật. */
	public void setCode(String code) {
		this.code = code;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof J2WarehouseId that)) {
			return false;
		}
		return Objects.equals(countryCode, that.countryCode) && Objects.equals(code, that.code);
	}

	@Override
	public int hashCode() {
		return Objects.hash(countryCode, code);
	}
}
