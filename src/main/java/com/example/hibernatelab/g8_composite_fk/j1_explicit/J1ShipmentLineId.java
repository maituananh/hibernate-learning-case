package com.example.hibernatelab.g8_composite_fk.j1_explicit;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Khoá chính ghép (shipmentId, lineNo) của dòng hàng: số thứ tự dòng chỉ duy nhất TRONG một chuyến hàng.
 *
 * <p>{@code shipmentId} không do người dùng gán: {@code @MapsId("shipmentId")} bên {@code J1ShipmentLine} sẽ điền nó
 * từ shipment lúc persist (derived identity). Vì vậy constructor cho phép truyền {@code null} cho shipmentId.
 *
 * BIẾN THỂ : J1 (g8_j1_explicit)
 * BASELINE : (đây là baseline của G8)
 * KHÁC BIỆT: FK ghép khai báo tường minh bằng @JoinColumns, mỗi @JoinColumn có name + referencedColumnName
 * HẬU QUẢ  : shipment(wh_country, wh_code) + MỘT FK constraint hai cột tới warehouse(country_code, code) (G8-S01)
 */
@Embeddable
public class J1ShipmentLineId implements Serializable {

	@Column(name = "shipment_id")
	private Long shipmentId;

	@Column(name = "line_no")
	private int lineNo;

	protected J1ShipmentLineId() {
		// JPA bắt buộc có constructor không tham số
	}

	public J1ShipmentLineId(Long shipmentId, int lineNo) {
		this.shipmentId = shipmentId;
		this.lineNo = lineNo;
	}

	public Long getShipmentId() {
		return shipmentId;
	}

	public int getLineNo() {
		return lineNo;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof J1ShipmentLineId that)) {
			return false;
		}
		return lineNo == that.lineNo && Objects.equals(shipmentId, that.shipmentId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(shipmentId, lineNo);
	}
}
