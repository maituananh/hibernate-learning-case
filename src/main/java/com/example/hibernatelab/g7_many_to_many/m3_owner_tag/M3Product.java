package com.example.hibernatelab.g7_many_to_many.m3_owner_tag;

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
 * Sản phẩm: một đầu của quan hệ N—N với Tag.
 *
 * BIẾN THỂ : M3 (g7_m3_owner_tag)
 * BASELINE : M1
 * KHÁC BIỆT: LẬT owner: @JoinTable nằm trên Tag.products, Product.tags là mappedBy
 * HẬU QUẢ  : ngược với M1: add ở Tag mới ghi bảng nối (G7-S03)
 */
@Entity(name = "G7M3_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m3_owner_tag", name = "product")
public class M3Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m3_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m3_product_seq", schema = "g7_m3_owner_tag", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — Product không có bảng nối riêng; bảng nối product_tag do Tag.products (owning) quản lý.
	@ManyToMany(mappedBy = "products", fetch = FetchType.LAZY, cascade = {})
	private Set<M3Tag> tags = new HashSet<>();

	protected M3Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public M3Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M3Tag> getTags() {
		return tags;
	}
}
