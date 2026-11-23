package com.example.hibernatelab.g9_integrated.s01_category_tree;

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

/**
 * Sản phẩm nằm trong một danh mục: thêm bảng thứ hai để thấy FK chặn việc xoá node đang được tham chiếu.
 *
 * BIẾN THỂ : G9-S01 (g9_s01_category_tree)
 * BASELINE : mapping hai chiều chuẩn của G1-V4
 * KHÁC BIỆT: Product.category là ManyToOne một chiều, KHÔNG cascade
 * HẬU QUẢ  : xoá Category còn product trỏ tới thì PostgreSQL từ chối (vi phạm FK)
 */
@Entity(name = "G9S01_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s01_category_tree", name = "product")
public class CategoryProduct {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s01_product_seq")
	@SequenceGenerator(name = "g9s01_product_seq", schema = "g9_s01_category_tree", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — FK product.category_id. Không cascade: sản phẩm không "thuộc vòng đời" của danh mục.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private Category category;

	protected CategoryProduct() {
		// JPA bắt buộc có constructor không tham số
	}

	public CategoryProduct(String name, Category category) {
		this.name = name;
		this.category = category;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Category getCategory() {
		return category;
	}
}
