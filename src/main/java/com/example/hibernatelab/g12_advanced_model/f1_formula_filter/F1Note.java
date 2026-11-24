package com.example.hibernatelab.g12_advanced_model.f1_formula_filter;

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

/** Ghi chú của khách hàng, có cờ {@code deleted} (xoá mềm kiểu thủ công). Entity KHÔNG có restriction; collection bên F1Customer thì có. */
@Entity(name = "G12F1_Note")
@Table(schema = "g12_f1_formula_filter", name = "note")
public class F1Note {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12f1_note_seq")
	@SequenceGenerator(name = "g12f1_note_seq", schema = "g12_f1_formula_filter", sequenceName = "note_seq", allocationSize = 50)
	private Long id;

	@Column(name = "text", nullable = false)
	private String text;

	@Column(name = "deleted", nullable = false)
	private boolean deleted;

	// OWNING side: FK customer_id nằm ở bảng note.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = false)
	private F1Customer customer;

	protected F1Note() {
		// JPA bắt buộc có constructor không tham số
	}

	public F1Note(String text, boolean deleted, F1Customer customer) {
		this.text = text;
		this.deleted = deleted;
		this.customer = customer;
	}

	public Long getId() {
		return id;
	}
}
