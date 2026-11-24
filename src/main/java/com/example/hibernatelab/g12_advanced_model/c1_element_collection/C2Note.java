package com.example.hibernatelab.g12_advanced_model.c1_element_collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** Ghi chú là entity con của {@link C2Order} (xem header ở C2Order). */
@Entity(name = "G12C2_Note")
@Table(schema = "g12_c1_element_collection", name = "c2_note")
public class C2Note {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12c2_note_seq")
	@SequenceGenerator(name = "g12c2_note_seq", schema = "g12_c1_element_collection", sequenceName = "c2_note_seq", allocationSize = 50)
	private Long id;

	@Column(name = "text", nullable = false)
	private String text;

	// OWNING side: giữ FK order_id.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false)
	private C2Order order;

	protected C2Note() {
		// JPA bắt buộc có constructor không tham số
	}

	public C2Note(String text) {
		this.text = text;
	}

	void setOrder(C2Order order) {
		this.order = order;
	}
}
