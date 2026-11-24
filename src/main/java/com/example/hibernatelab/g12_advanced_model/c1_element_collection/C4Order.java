package com.example.hibernatelab.g12_advanced_model.c1_element_collection;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đơn hàng có các bước xử lý là {@code @ElementCollection List<String>} kèm {@code @OrderColumn}.
 *
 * BIẾN THỂ : C4 (@OrderColumn)
 * BASELINE : C1 (bag: List<String> không có thứ tự cột)
 * KHÁC BIỆT: thêm @OrderColumn trên List
 * HẬU QUẢ  : bảng con c4_order_step có PK (order_id, step_order); thứ tự được lưu thành cột; bỏ phần tử giữa thì UPDATE dồn chỉ số + DELETE cuối (G12-S05b)
 */
@Entity(name = "G12C4Order")
@Table(schema = "g12_c1_element_collection", name = "c4_order")
public class C4Order {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12c4_order_seq")
	@SequenceGenerator(name = "g12c4_order_seq", schema = "g12_c1_element_collection", sequenceName = "c4_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(schema = "g12_c1_element_collection", name = "c4_order_step", joinColumns = @JoinColumn(name = "order_id"))
	@OrderColumn(name = "step_order") // cột lưu vị trí 0,1,2…; nhờ nó Hibernate biết dòng nào là dòng nào
	@Column(name = "step", nullable = false)
	private List<String> steps = new ArrayList<>();

	protected C4Order() {
		// JPA bắt buộc có constructor không tham số
	}

	public C4Order(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public List<String> getSteps() {
		return steps;
	}
}
