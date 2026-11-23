package com.example.hibernatelab.g4_update_child_via_parent.u3_optimistic_excluded;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (bảng master, không có quan hệ nào) của biến thể U3.
 *
 * BIẾN THỂ : U3 (g4_u3_optimistic_excluded)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: như U2 + @OptimisticLock(excluded = true) trên items
 * HẬU QUẢ  : thay đổi collection items không còn làm tăng version của cha (G4-S07)
 */
@Entity(name = "G4U3_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u3_optimistic_excluded", name = "product")
public class U3Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u3_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u3_product_seq", schema = "g4_u3_optimistic_excluded", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected U3Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public U3Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
