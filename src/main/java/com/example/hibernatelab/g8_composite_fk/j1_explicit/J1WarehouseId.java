package com.example.hibernatelab.g8_composite_fk.j1_explicit;

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
 * BIẾN THỂ : J1 (g8_j1_explicit)
 * BASELINE : (đây là baseline của G8)
 * KHÁC BIỆT: FK ghép khai báo tường minh bằng @JoinColumns, mỗi @JoinColumn có name + referencedColumnName
 * HẬU QUẢ  : shipment(wh_country, wh_code) + MỘT FK constraint hai cột tới warehouse(country_code, code) (G8-S01)
 */
@Embeddable
public class J1WarehouseId implements Serializable {

	@Column(name = "country_code", length = 2)
	private String countryCode;

	@Column(name = "code", length = 10)
	private String code;

	protected J1WarehouseId() {
		// JPA bắt buộc có constructor không tham số
	}

	public J1WarehouseId(String countryCode, String code) {
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
		if (!(other instanceof J1WarehouseId that)) {
			return false;
		}
		return Objects.equals(countryCode, that.countryCode) && Objects.equals(code, that.code);
	}

	@Override
	public int hashCode() {
		return Objects.hash(countryCode, code);
	}
}
