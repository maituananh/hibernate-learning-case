package com.example.hibernatelab.g7_many_to_many.m2_defaults;

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
 * BIẾN THỂ : M2 (g7_m2_defaults)
 * BASELINE : M1
 * KHÁC BIỆT: BỎ @JoinTable: để Hibernate tự đặt tên bảng nối và tên cột
 * HẬU QUẢ  : xem tên bảng/cột mặc định và schema của bảng nối trong G7-S01b
 */
@Entity(name = "G7M2_Tag") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m2_defaults", name = "tag")
public class M2Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m2_tag_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m2_tag_seq", schema = "g7_m2_defaults", sequenceName = "tag_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy trỏ tới tên field của owning side (Product.tags).
	@ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY, cascade = {})
	private Set<M2Product> products = new HashSet<>();

	protected M2Tag() {
		// JPA bắt buộc có constructor không tham số
	}

	public M2Tag(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M2Product> getProducts() {
		return products;
	}
}
