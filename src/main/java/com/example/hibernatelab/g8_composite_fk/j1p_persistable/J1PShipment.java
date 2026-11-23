package com.example.hibernatelab.g8_composite_fk.j1p_persistable;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Chuyến hàng (không có warehouse: G8-S04c chỉ quan tâm tới dòng hàng).
 *
 * BIẾN THỂ : J1P (g8_j1p_persistable)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: J1ShipmentLine implement Persistable (cờ isNew) để Spring Data save() luôn persist; bỏ warehouse khỏi Shipment vì S04 không cần
 * HẬU QUẢ  : save(line) với id ghép đã gán một phần KHÔNG còn merge + SELECT thừa (G8-S04c)
 */
@Entity(name = "G8J1P_Shipment") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j1p_persistable", name = "shipment")
public class J1PShipment {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g8j1p_shipment_seq")
	@SequenceGenerator(name = "g8j1p_shipment_seq", schema = "g8_j1p_persistable", sequenceName = "shipment_seq", allocationSize = 50)
	private Long id;

	@Column(name = "reference", nullable = false)
	private String reference;

	// INVERSE side — FK nằm ở bảng shipment_line.
	@OneToMany(mappedBy = "shipment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<J1PShipmentLine> lines = new ArrayList<>();

	protected J1PShipment() {
		// JPA bắt buộc có constructor không tham số
	}

	public J1PShipment(String reference) {
		this.reference = reference;
	}

	public Long getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public List<J1PShipmentLine> getLines() {
		return lines;
	}
}
