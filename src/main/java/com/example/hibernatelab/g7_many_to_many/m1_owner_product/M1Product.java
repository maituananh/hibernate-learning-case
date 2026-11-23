package com.example.hibernatelab.g7_many_to_many.m1_owner_product;

import java.util.HashSet;
import java.util.Set;

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
 * BIẾN THỂ : M1 (g7_m1_owner_product)
 * BASELINE : —
 * KHÁC BIỆT: Product là OWNING side (@JoinTable tường minh), Set<Tag>, Tag.products (mappedBy) là INVERSE side. Đây là BASELINE của nhóm
 * HẬU QUẢ  : add ở Product ghi bảng nối, add ở Tag thì không (G7-S02)
 */
@Entity(name = "G7M1_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m1_owner_product", name = "product")
public class M1Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m1_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m1_product_seq", schema = "g7_m1_owner_product", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — @JoinTable nằm ở đây, nên chỉ product.getTags() mới ghi bảng nối product_tag.
	// cascade ghi tường minh là rỗng: persist/remove product KHÔNG lan sang tag (tag là dữ liệu dùng chung).
	// Khai @JoinTable(schema = ...) tường minh, nếu không bảng nối rơi vào schema "public" (bẫy đã gặp ở G1-S07).
	@ManyToMany(fetch = FetchType.LAZY, cascade = {})
	@JoinTable(schema = "g7_m1_owner_product", name = "product_tag",
		joinColumns = @JoinColumn(name = "product_id"),
		inverseJoinColumns = @JoinColumn(name = "tag_id"))
	private Set<M1Tag> tags = new HashSet<>();

	protected M1Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public M1Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M1Tag> getTags() {
		return tags;
	}
}
