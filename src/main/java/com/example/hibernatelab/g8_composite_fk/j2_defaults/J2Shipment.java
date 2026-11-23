package com.example.hibernatelab.g8_composite_fk.j2_defaults;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Chuyến hàng với {@code @ManyToOne} TRƠN tới kho có PK ghép: không nói gì về tên cột FK.
 *
 * BIẾN THỂ : J2 (g8_j2_defaults)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: bỏ @JoinColumns, chỉ còn @ManyToOne trơn; Shipment bỏ cả lines (S02 chỉ xem tên cột FK mặc định)
 * HẬU QUẢ  : tên hai cột FK do naming strategy quyết định (G8-S02, kết quả ghi ở docs/RESULTS.md)
 */
@Entity(name = "G8J2_Shipment") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j2_defaults", name = "shipment")
public class J2Shipment {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g8j2_shipment_seq")
	@SequenceGenerator(name = "g8j2_shipment_seq", schema = "g8_j2_defaults", sequenceName = "shipment_seq", allocationSize = 50)
	private Long id;

	@Column(name = "reference", nullable = false)
	private String reference;

	// OWNING side — CỐ Ý để mặc định hoàn toàn (không @JoinColumn/@JoinColumns): Hibernate tự đặt tên hai cột FK (G8-S02).
	@ManyToOne(fetch = FetchType.LAZY)
	private J2Warehouse warehouse;

	protected J2Shipment() {
		// JPA bắt buộc có constructor không tham số
	}

	public J2Shipment(String reference, J2Warehouse warehouse) {
		this.reference = reference;
		this.warehouse = warehouse;
	}

	public Long getId() {
		return id;
	}

	public J2Warehouse getWarehouse() {
		return warehouse;
	}
}
