package com.example.hibernatelab.g7_many_to_many.m7_cascade_all;

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
 * BIẾN THỂ : M7 (g7_m7_cascade_all)
 * BASELINE : M1
 * KHÁC BIỆT: Product.tags có cascade = ALL (ANTI-PATTERN)
 * HẬU QUẢ  : xoá một Product kéo theo xoá Tag đang được product khác dùng (G7-S08)
 */
@Entity(name = "G7M7_Tag") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m7_cascade_all", name = "tag")
public class M7Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m7_tag_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m7_tag_seq", schema = "g7_m7_cascade_all", sequenceName = "tag_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy trỏ tới tên field của owning side (Product.tags). Thêm product vào đây KHÔNG ghi gì xuống DB (G7-S02).
	@ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY, cascade = {})
	private Set<M7Product> products = new HashSet<>();

	protected M7Tag() {
		// JPA bắt buộc có constructor không tham số
	}

	public M7Tag(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M7Product> getProducts() {
		return products;
	}
}
