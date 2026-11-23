package com.example.hibernatelab.g7_many_to_many.m5_list_bag;

import java.util.ArrayList;
import java.util.List;

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
 * BIẾN THỂ : M5 (g7_m5_list_bag)
 * BASELINE : M1
 * KHÁC BIỆT: Product.tags là List<Tag> thay vì Set<Tag> (bag: không thứ tự, cho phép trùng)
 * HẬU QUẢ  : bảng nối không có PK, bỏ 1 phần tử thì ghi lại cả bảng nối của product (G7-S05, S09)
 */
@Entity(name = "G7M5_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m5_list_bag", name = "product")
public class M5Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m5_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m5_product_seq", schema = "g7_m5_list_bag", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — @JoinTable nằm ở đây, nên chỉ product.getTags() mới ghi bảng nối product_tag.
	// cascade ghi tường minh là rỗng: persist/remove product KHÔNG lan sang tag (tag là dữ liệu dùng chung).
	// Khai @JoinTable(schema = ...) tường minh, nếu không bảng nối rơi vào schema "public" (bẫy đã gặp ở G1-S07).
	@ManyToMany(fetch = FetchType.LAZY, cascade = {})
	@JoinTable(schema = "g7_m5_list_bag", name = "product_tag",
		joinColumns = @JoinColumn(name = "product_id"),
		inverseJoinColumns = @JoinColumn(name = "tag_id"))
	private List<M5Tag> tags = new ArrayList<>();

	protected M5Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public M5Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<M5Tag> getTags() {
		return tags;
	}
}
