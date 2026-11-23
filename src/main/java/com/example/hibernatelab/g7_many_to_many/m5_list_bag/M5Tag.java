package com.example.hibernatelab.g7_many_to_many.m5_list_bag;

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
 * BIẾN THỂ : M5 (g7_m5_list_bag)
 * BASELINE : M1
 * KHÁC BIỆT: Product.tags là List<Tag> thay vì Set<Tag> (bag: không thứ tự, cho phép trùng)
 * HẬU QUẢ  : bảng nối không có PK, bỏ 1 phần tử thì ghi lại cả bảng nối của product (G7-S05, S09)
 */
@Entity(name = "G7M5_Tag") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m5_list_bag", name = "tag")
public class M5Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m5_tag_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m5_tag_seq", schema = "g7_m5_list_bag", sequenceName = "tag_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy trỏ tới tên field của owning side (Product.tags). Thêm product vào đây KHÔNG ghi gì xuống DB (G7-S02).
	@ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY, cascade = {})
	private Set<M5Product> products = new HashSet<>();

	protected M5Tag() {
		// JPA bắt buộc có constructor không tham số
	}

	public M5Tag(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M5Product> getProducts() {
		return products;
	}
}
