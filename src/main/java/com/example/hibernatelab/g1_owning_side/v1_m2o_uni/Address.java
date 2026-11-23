package com.example.hibernatelab.g1_owning_side.v1_m2o_uni;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Địa chỉ, chỉ có ở V1 để phục vụ G1-S05: một order có HAI FK cùng trỏ tới bảng {@code address}
 * (địa chỉ thanh toán và địa chỉ giao hàng).
 */
@Entity(name = "G1V1_Address") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v1_m2o_uni", name = "address")
public class Address {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v1_address_seq")
	@SequenceGenerator(name = "g1v1_address_seq", schema = "g1_v1_m2o_uni", sequenceName = "address_seq", allocationSize = 50)
	private Long id;

	@Column(name = "street", nullable = false)
	private String street;

	protected Address() {
		// JPA bắt buộc có constructor không tham số
	}

	public Address(String street) {
		this.street = street;
	}

	public Long getId() {
		return id;
	}

	public String getStreet() {
		return street;
	}
}
