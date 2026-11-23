package com.example.hibernatelab.g7_many_to_many.m4_no_mappedby;

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
 * Nhãn (Tag): đầu còn lại của quan hệ N—N với Product.
 *
 * BIẾN THỂ : M4 (g7_m4_no_mappedby)
 * BASELINE : M1
 * KHÁC BIỆT: CẢ HAI phía đều là owning (không có mappedBy), mỗi phía một @JoinTable
 * HẬU QUẢ  : sinh 2 bảng nối product_tags và tag_products, dễ mâu thuẫn nhau (G7-S04)
 */
@Entity(name = "G7M4_Tag") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m4_no_mappedby", name = "tag")
public class M4Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m4_tag_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m4_tag_seq", schema = "g7_m4_no_mappedby", sequenceName = "tag_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side (lần 2) — cũng thiếu mappedBy nên có thêm bảng nối thứ hai tag_products: cùng một quan hệ mà có 2 bảng.
	@ManyToMany(fetch = FetchType.LAZY, cascade = {})
	@JoinTable(schema = "g7_m4_no_mappedby", name = "tag_products",
		joinColumns = @JoinColumn(name = "tag_id"),
		inverseJoinColumns = @JoinColumn(name = "product_id"))
	private Set<M4Product> products = new HashSet<>();

	protected M4Tag() {
		// JPA bắt buộc có constructor không tham số
	}

	public M4Tag(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M4Product> getProducts() {
		return products;
	}
}
