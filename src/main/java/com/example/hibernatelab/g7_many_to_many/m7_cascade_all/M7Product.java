package com.example.hibernatelab.g7_many_to_many.m7_cascade_all;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm: một đầu của quan hệ N—N với Tag.
 *
 * BIẾN THỂ : M7 (g7_m7_cascade_all)
 * BASELINE : M1
 * KHÁC BIỆT: Product.tags có cascade = ALL (ANTI-PATTERN)
 * HẬU QUẢ  : xoá một Product kéo theo xoá Tag đang được product khác dùng (G7-S08)
 */
@Entity(name = "G7M7_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m7_cascade_all", name = "product")
public class M7Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m7_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m7_product_seq", schema = "g7_m7_cascade_all", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — @JoinTable nằm ở đây, nên chỉ product.getTags() mới ghi bảng nối product_tag.
	// cascade = ALL trên @ManyToMany: ANTI-PATTERN, xoá product sẽ cố xoá cả tag (G7-S08).
	// Khai @JoinTable(schema = ...) tường minh, nếu không bảng nối rơi vào schema "public" (bẫy đã gặp ở G1-S07).
	@ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@JoinTable(schema = "g7_m7_cascade_all", name = "product_tag",
		joinColumns = @JoinColumn(name = "product_id"),
		inverseJoinColumns = @JoinColumn(name = "tag_id"))
	private Set<M7Tag> tags = new HashSet<>();

	protected M7Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public M7Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M7Tag> getTags() {
		return tags;
	}
}
