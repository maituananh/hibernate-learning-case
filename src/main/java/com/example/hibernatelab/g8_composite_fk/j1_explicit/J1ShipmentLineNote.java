package com.example.hibernatelab.g8_composite_fk.j1_explicit;

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
 * Ghi chú của một dòng hàng. Tầng thứ ba của chuỗi khoá ghép: FK trỏ tới PK GHÉP của shipment_line (G8-S05).
 *
 * BIẾN THỂ : J1 (g8_j1_explicit)
 * BASELINE : (đây là baseline của G8)
 * KHÁC BIỆT: FK ghép khai báo tường minh bằng @JoinColumns, mỗi @JoinColumn có name + referencedColumnName
 * HẬU QUẢ  : shipment(wh_country, wh_code) + MỘT FK constraint hai cột tới warehouse(country_code, code) (G8-S01)
 */
@Entity(name = "G8J1_ShipmentLineNote") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j1_explicit", name = "shipment_line_note")
public class J1ShipmentLineNote {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g8j1_note_seq")
	@SequenceGenerator(name = "g8j1_note_seq", schema = "g8_j1_explicit", sequenceName = "shipment_line_note_seq", allocationSize = 50)
	private Long id;

	@Column(name = "text", nullable = false)
	private String text;

	// OWNING side — FK HAI cột (shipment_id, line_no) trỏ tới PK ghép của shipment_line.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
		@JoinColumn(name = "shipment_id", referencedColumnName = "shipment_id", nullable = false),
		@JoinColumn(name = "line_no", referencedColumnName = "line_no", nullable = false)
	})
	private J1ShipmentLine line;

	protected J1ShipmentLineNote() {
		// JPA bắt buộc có constructor không tham số
	}

	public J1ShipmentLineNote(J1ShipmentLine line, String text) {
		this.line = line;
		this.text = text;
	}

	public Long getId() {
		return id;
	}

	public String getText() {
		return text;
	}

	public J1ShipmentLine getLine() {
		return line;
	}
}
