package com.example.hibernatelab.g8_composite_fk.j1_explicit;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Chuyến hàng xuất từ một kho. FK tới kho là FK HAI CỘT vì PK của kho có hai cột.
 *
 * BIẾN THỂ : J1 (g8_j1_explicit)
 * BASELINE : (đây là baseline của G8)
 * KHÁC BIỆT: FK ghép khai báo tường minh bằng @JoinColumns, mỗi @JoinColumn có name + referencedColumnName
 * HẬU QUẢ  : shipment(wh_country, wh_code) + MỘT FK constraint hai cột tới warehouse(country_code, code) (G8-S01)
 */
@Entity(name = "G8J1_Shipment") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j1_explicit", name = "shipment")
public class J1Shipment {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g8j1_shipment_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g8j1_shipment_seq", schema = "g8_j1_explicit", sequenceName = "shipment_seq", allocationSize = 50)
	private Long id;

	@Column(name = "reference", nullable = false)
	private String reference;

	// OWNING side — FK nằm ở bảng shipment, gồm HAI cột. Mỗi @JoinColumn ghép cột FK (name) với cột PK của warehouse
	// (referencedColumnName). Khi FK nhiều cột, referencedColumnName là BẮT BUỘC cho từng cột để Hibernate biết cột nào ghép cột nào.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
		@JoinColumn(name = "wh_country", referencedColumnName = "country_code", nullable = false),
		@JoinColumn(name = "wh_code", referencedColumnName = "code", nullable = false)
	})
	private J1Warehouse warehouse;

	// INVERSE side — FK nằm ở bảng shipment_line (J1ShipmentLine.shipment là owning side).
	// cascade = ALL + orphanRemoval: dòng hàng sống chết theo chuyến hàng.
	@OneToMany(mappedBy = "shipment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<J1ShipmentLine> lines = new ArrayList<>();

	protected J1Shipment() {
		// JPA bắt buộc có constructor không tham số
	}

	public J1Shipment(String reference, J1Warehouse warehouse) {
		this.reference = reference;
		this.warehouse = warehouse;
	}

	/** Thêm một dòng hàng với số thứ tự lineNo; dòng mới tự gắn ngược về chuyến hàng này. */
	public J1ShipmentLine addLine(int lineNo, String productName) {
		J1ShipmentLine line = new J1ShipmentLine(this, lineNo, productName);
		lines.add(line);
		return line;
	}

	public Long getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public J1Warehouse getWarehouse() {
		return warehouse;
	}

	public List<J1ShipmentLine> getLines() {
		return lines;
	}
}
