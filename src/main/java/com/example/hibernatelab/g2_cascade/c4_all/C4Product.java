package com.example.hibernatelab.g2_cascade.c4_all;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm của biến thể C4. Không có quan hệ nào: nó chỉ là "bảng master" mà C4OrderItem trỏ tới.
 * Ở C6/C7, cascade trên C4OrderItem.product quyết định số phận của dòng product này (xem G2-S08, S09).
 */
@Entity(name = "G2C4_Product") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g2_c4_all", name = "product")
public class C4Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g2c4_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g2c4_product_seq", schema = "g2_c4_all", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected C4Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public C4Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
