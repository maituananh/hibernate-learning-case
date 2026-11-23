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
 * Sản phẩm: một đầu của quan hệ N—N với Tag.
 *
 * BIẾN THỂ : M2 (g7_m2_defaults)
 * BASELINE : M1
 * KHÁC BIỆT: BỎ @JoinTable: để Hibernate tự đặt tên bảng nối và tên cột
 * HẬU QUẢ  : xem tên bảng/cột mặc định và schema của bảng nối trong G7-S01b
 */
@Entity(name = "G7M2_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m2_defaults", name = "product")
public class M2Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m2_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m2_product_seq", schema = "g7_m2_defaults", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — KHÔNG có @JoinTable: tên bảng nối, tên cột và schema của bảng nối do Hibernate tự suy ra (G7-S01b).
	// cascade ghi tường minh là rỗng: persist/remove product KHÔNG lan sang tag (tag là dữ liệu dùng chung).
	@ManyToMany(fetch = FetchType.LAZY, cascade = {})
	private Set<M2Tag> tags = new HashSet<>();

	protected M2Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public M2Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M2Tag> getTags() {
		return tags;
	}
}
