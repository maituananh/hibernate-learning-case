package com.example.hibernatelab.g4_update_child_via_parent.u4_dynamic_update;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (bảng master, không có quan hệ nào) của biến thể U4.
 *
 * BIẾN THỂ : U4 (g4_u4_dynamic_update)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: OrderItem có @DynamicUpdate
 * HẬU QUẢ  : UPDATE order_item chỉ liệt kê cột thực sự đổi thay vì tất cả cột (G4-S10)
 */
@Entity(name = "G4U4_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u4_dynamic_update", name = "product")
public class U4Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u4_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u4_product_seq", schema = "g4_u4_dynamic_update", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected U4Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public U4Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
