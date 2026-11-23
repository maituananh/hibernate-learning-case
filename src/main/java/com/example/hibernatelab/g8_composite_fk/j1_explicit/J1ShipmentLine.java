package com.example.hibernatelab.g8_composite_fk.j1_explicit;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Dòng hàng của một chuyến hàng. DERIVED IDENTITY: một nửa PK ({@code shipment_id}) chính là FK trỏ shipment.
 *
 * BIẾN THỂ : J1 (g8_j1_explicit)
 * BASELINE : (đây là baseline của G8)
 * KHÁC BIỆT: FK ghép khai báo tường minh bằng @JoinColumns, mỗi @JoinColumn có name + referencedColumnName
 * HẬU QUẢ  : shipment(wh_country, wh_code) + MỘT FK constraint hai cột tới warehouse(country_code, code) (G8-S01)
 */
@Entity(name = "G8J1_ShipmentLine") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j1_explicit", name = "shipment_line")
public class J1ShipmentLine {

	// PK ghép (shipment_id, line_no). Khởi tạo sẵn object để @MapsId có chỗ điền shipmentId lúc persist.
	@EmbeddedId
	private J1ShipmentLineId id = new J1ShipmentLineId();

	// OWNING side — @MapsId("shipmentId"): cột shipment_id vừa là FK trỏ shipment, vừa là nửa PK (thuộc tính shipmentId của id).
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("shipmentId")
	@JoinColumn(name = "shipment_id")
	private J1Shipment shipment;

	@Column(name = "product_name", nullable = false)
	private String productName;

	// INVERSE side — FK hai cột (shipment_id, line_no) nằm ở bảng shipment_line_note.
	@OneToMany(mappedBy = "line", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<J1ShipmentLineNote> notes = new ArrayList<>();

	protected J1ShipmentLine() {
		// JPA bắt buộc có constructor không tham số
	}

	public J1ShipmentLine(J1Shipment shipment, int lineNo, String productName) {
		this.shipment = shipment;
		// shipmentId để null: @MapsId sẽ lấy từ shipment lúc persist. Chỉ lineNo do người dùng gán.
		this.id = new J1ShipmentLineId(null, lineNo);
		this.productName = productName;
	}

	public J1ShipmentLineNote addNote(String text) {
		J1ShipmentLineNote note = new J1ShipmentLineNote(this, text);
		notes.add(note);
		return note;
	}

	public J1ShipmentLineId getId() {
		return id;
	}

	public J1Shipment getShipment() {
		return shipment;
	}

	public String getProductName() {
		return productName;
	}

	public List<J1ShipmentLineNote> getNotes() {
		return notes;
	}
}
