package com.example.hibernatelab.g4_update_child_via_parent.u5_child_version;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (bảng master, không có quan hệ nào) của biến thể U5.
 *
 * BIẾN THỂ : U5 (g4_u5_child_version)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: OrderItem có @Version riêng
 * HẬU QUẢ  : hai bản DETACHED của cùng một item cập nhật đồng thời: bản sau bị OptimisticLock (G4-S11)
 */
@Entity(name = "G4U5_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u5_child_version", name = "product")
public class U5Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u5_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u5_product_seq", schema = "g4_u5_child_version", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected U5Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public U5Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
