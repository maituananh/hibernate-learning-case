package com.example.hibernatelab.g7_many_to_many.m1_owner_product;

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
 * BIẾN THỂ : M1 (g7_m1_owner_product)
 * BASELINE : —
 * KHÁC BIỆT: Product là OWNING side (@JoinTable tường minh), Set<Tag>, Tag.products (mappedBy) là INVERSE side. Đây là BASELINE của nhóm
 * HẬU QUẢ  : add ở Product ghi bảng nối, add ở Tag thì không (G7-S02)
 */
@Entity(name = "G7M1_Tag") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m1_owner_product", name = "tag")
public class M1Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m1_tag_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m1_tag_seq", schema = "g7_m1_owner_product", sequenceName = "tag_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy trỏ tới tên field của owning side (Product.tags). Thêm product vào đây KHÔNG ghi gì xuống DB (G7-S02).
	@ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY, cascade = {})
	private Set<M1Product> products = new HashSet<>();

	protected M1Tag() {
		// JPA bắt buộc có constructor không tham số
	}

	public M1Tag(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M1Product> getProducts() {
		return products;
	}
}
