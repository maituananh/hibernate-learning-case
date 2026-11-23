package com.example.hibernatelab.g8_composite_fk.j1p_persistable;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Dòng hàng implement {@link Persistable}: tự nói cho Spring Data biết "tôi là entity mới" (cờ {@code isNew}).
 *
 * <p>Vì sao cần: id ghép đã khác null (lineNo có giá trị) nên Spring Data mặc định coi là entity CŨ và gọi merge
 * (G8-S04b). Với Persistable, Spring Data hỏi {@code isNew()}: còn true thì persist thẳng. Cờ được hạ xuống false
 * sau khi persist hoặc sau khi tải từ DB, để lần save sau (cập nhật) đi đúng đường merge.
 *
 * BIẾN THỂ : J1P (g8_j1p_persistable)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: J1ShipmentLine implement Persistable (cờ isNew) để Spring Data save() luôn persist; bỏ warehouse khỏi Shipment vì S04 không cần
 * HẬU QUẢ  : save(line) với id ghép đã gán một phần KHÔNG còn merge + SELECT thừa (G8-S04c)
 */
@Entity(name = "G8J1P_ShipmentLine") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j1p_persistable", name = "shipment_line")
public class J1PShipmentLine implements Persistable<J1PShipmentLineId> {

	@EmbeddedId
	private J1PShipmentLineId id = new J1PShipmentLineId();

	// OWNING side — @MapsId("shipmentId"): shipment_id vừa là FK vừa là nửa PK.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("shipmentId")
	@JoinColumn(name = "shipment_id")
	private J1PShipment shipment;

	@Column(name = "product_name", nullable = false)
	private String productName;

	// @Transient: không phải cột. Entity vừa new ra thì là "mới"; persist hoặc load xong thì không còn mới.
	@Transient
	private boolean isNewEntity = true;

	protected J1PShipmentLine() {
		// JPA bắt buộc có constructor không tham số
	}

	public J1PShipmentLine(J1PShipment shipment, int lineNo, String productName) {
		this.shipment = shipment;
		this.id = new J1PShipmentLineId(null, lineNo);
		this.productName = productName;
	}

	@Override
	public J1PShipmentLineId getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNewEntity;
	}

	@PostPersist
	@PostLoad
	void markNotNew() {
		this.isNewEntity = false;
	}

	public J1PShipment getShipment() {
		return shipment;
	}

	public String getProductName() {
		return productName;
	}
}
