package com.example.hibernatelab.g8_composite_fk.j1p_persistable;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Khoá chính ghép (shipmentId, lineNo) của dòng hàng: số thứ tự dòng chỉ duy nhất TRONG một chuyến hàng.
 *
 * <p>{@code shipmentId} không do người dùng gán: {@code @MapsId("shipmentId")} bên {@code J1PShipmentLine} sẽ điền nó
 * từ shipment lúc persist (derived identity). Vì vậy constructor cho phép truyền {@code null} cho shipmentId.
 *
 * BIẾN THỂ : J1P (g8_j1p_persistable)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: J1ShipmentLine implement Persistable (cờ isNew) để Spring Data save() luôn persist; bỏ warehouse khỏi Shipment vì S04 không cần
 * HẬU QUẢ  : save(line) với id ghép đã gán một phần KHÔNG còn merge + SELECT thừa (G8-S04c)
 */
@Embeddable
public class J1PShipmentLineId implements Serializable {

	@Column(name = "shipment_id")
	private Long shipmentId;

	@Column(name = "line_no")
	private int lineNo;

	protected J1PShipmentLineId() {
		// JPA bắt buộc có constructor không tham số
	}

	public J1PShipmentLineId(Long shipmentId, int lineNo) {
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
		if (!(other instanceof J1PShipmentLineId that)) {
			return false;
		}
		return lineNo == that.lineNo && Objects.equals(shipmentId, that.shipmentId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(shipmentId, lineNo);
	}
}
