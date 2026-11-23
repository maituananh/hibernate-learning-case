package com.example.hibernatelab.g8_composite_fk.j3_idclass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Chuyến hàng với FK hai cột tới kho dùng {@code @IdClass}: khai báo {@code @JoinColumns} giống hệt J1.
 *
 * BIẾN THỂ : J3 (g8_j3_idclass)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: Warehouse dùng @IdClass (hai @Id nằm thẳng trong entity) thay vì @EmbeddedId; Shipment bỏ lines (S01 chỉ cần so DDL và find)
 * HẬU QUẢ  : DDL y hệt J1; find nhận một object J3WarehouseKey; code truy cập id là warehouse.getCountryCode() thay vì getId().getCountryCode()
 */
@Entity(name = "G8J3_Shipment") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j3_idclass", name = "shipment")
public class J3Shipment {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g8j3_shipment_seq")
	@SequenceGenerator(name = "g8j3_shipment_seq", schema = "g8_j3_idclass", sequenceName = "shipment_seq", allocationSize = 50)
	private Long id;

	@Column(name = "reference", nullable = false)
	private String reference;

	// OWNING side — FK hai cột. Cách khai báo KHÔNG phụ thuộc kho dùng @EmbeddedId hay @IdClass:
	// referencedColumnName chỉ nói tên CỘT trong bảng warehouse.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
		@JoinColumn(name = "wh_country", referencedColumnName = "country_code", nullable = false),
		@JoinColumn(name = "wh_code", referencedColumnName = "code", nullable = false)
	})
	private J3Warehouse warehouse;

	protected J3Shipment() {
		// JPA bắt buộc có constructor không tham số
	}

	public J3Shipment(String reference, J3Warehouse warehouse) {
		this.reference = reference;
		this.warehouse = warehouse;
	}

	public Long getId() {
		return id;
	}

	public J3Warehouse getWarehouse() {
		return warehouse;
	}
}
