package com.example.hibernatelab.g12_advanced_model.c1_element_collection;

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
 * Đơn hàng có ghi chú là ENTITY con ({@code @OneToMany} tới {@link C2Note}), để so với {@code @ElementCollection} của C1.
 *
 * BIẾN THỂ : C2 (entity con)
 * BASELINE : C1 (bag @ElementCollection)
 * KHÁC BIỆT: phần tử là entity có id riêng (OneToMany + orphanRemoval) thay vì giá trị String
 * HẬU QUẢ  : bỏ 1 ghi chú thì chỉ DELETE đúng 1 dòng theo id, không recreate (G12-S04, đối chiếu G1-S08)
 */
@Entity(name = "G12C2_Order")
@Table(schema = "g12_c1_element_collection", name = "c2_order")
public class C2Order {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12c2_order_seq")
	@SequenceGenerator(name = "g12c2_order_seq", schema = "g12_c1_element_collection", sequenceName = "c2_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side: FK nằm ở bảng c2_note (cột order_id), field "order" của C2Note là OWNING side.
	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<C2Note> notes = new ArrayList<>();

	protected C2Order() {
		// JPA bắt buộc có constructor không tham số
	}

	public C2Order(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public List<C2Note> getNotes() {
		return notes;
	}

	public void addNote(C2Note note) {
		notes.add(note);
		note.setOrder(this);
	}
}
