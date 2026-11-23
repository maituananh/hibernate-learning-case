package com.example.hibernatelab.g7_many_to_many.m6_order_column;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Nhãn (Tag): đầu còn lại của quan hệ N—N với Product.
 *
 * BIẾN THỂ : M6 (g7_m6_order_column)
 * BASELINE : M5
 * KHÁC BIỆT: thêm @OrderColumn(name = "position") trên List<Tag>
 * HẬU QUẢ  : bảng nối có thêm cột position; xoá phần tử giữa thì dồn lại vị trí (G7-S06)
 */
@Entity(name = "G7M6_Tag") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m6_order_column", name = "tag")
public class M6Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m6_tag_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m6_tag_seq", schema = "g7_m6_order_column", sequenceName = "tag_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy trỏ tới tên field của owning side (Product.tags). Thêm product vào đây KHÔNG ghi gì xuống DB (G7-S02).
	@ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY, cascade = {})
	private Set<M6Product> products = new HashSet<>();

	protected M6Tag() {
		// JPA bắt buộc có constructor không tham số
	}

	public M6Tag(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M6Product> getProducts() {
		return products;
	}
}
