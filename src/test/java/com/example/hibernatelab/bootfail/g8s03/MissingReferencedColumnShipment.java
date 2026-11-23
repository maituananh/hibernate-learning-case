package com.example.hibernatelab.bootfail.g8s03;

import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1Warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * [BOOT-FAIL] G8-S03: entity CỐ Ý SAI, nằm ở package {@code bootfail} nên entity scan không thấy.
 *
 * <p>Sai ở đâu: FK hai cột nhưng cột {@code wh_code} THIẾU {@code referencedColumnName}, còn {@code wh_country} có.
 * Bản ĐÚNG (xem {@code J1Shipment}) khai báo {@code referencedColumnName} cho CẢ HAI cột.
 */
@Entity(name = "BootFail_MissingReferencedColumnShipment")
@Table(schema = "g8_j1_explicit", name = "shipment_bootfail")
public class MissingReferencedColumnShipment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reference")
	private String reference;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumns({
		@JoinColumn(name = "wh_country", referencedColumnName = "country_code"),
		@JoinColumn(name = "wh_code") // ← thiếu referencedColumnName
	})
	private J1Warehouse warehouse;

	protected MissingReferencedColumnShipment() {
		// JPA bắt buộc có constructor không tham số
	}
}
