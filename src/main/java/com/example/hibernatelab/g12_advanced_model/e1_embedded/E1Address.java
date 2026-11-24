package com.example.hibernatelab.g12_advanced_model.e1_embedded;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Value object địa chỉ: KHÔNG phải entity (không có id, không có bảng riêng).
 * Các cột của nó được "nhúng" thẳng vào bảng của entity chứa nó.
 *
 * <p>Đây là lớp MUTABLE (có setter) để G12-S03 chứng minh sửa tại chỗ vẫn được dirty checking phát hiện.
 */
@Embeddable
public class E1Address {

	// Tên cột mặc định (street, city, zip_code) CHỈ đúng khi embeddable dùng MỘT lần.
	// Dùng hai lần thì phải @AttributeOverride, nếu không hai field trùng tên cột (G12-S01).
	@Column(name = "street")
	private String street;

	@Column(name = "city")
	private String city;

	@Column(name = "zip_code")
	private String zipCode;

	protected E1Address() {
		// JPA bắt buộc có constructor không tham số
	}

	public E1Address(String street, String city, String zipCode) {
		this.street = street;
		this.city = city;
		this.zipCode = zipCode;
	}

	public String getStreet() {
		return street;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getZipCode() {
		return zipCode;
	}
}
