package com.example.hibernatelab.g7_many_to_many.m6_order_column;

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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm: một đầu của quan hệ N—N với Tag.
 *
 * BIẾN THỂ : M6 (g7_m6_order_column)
 * BASELINE : M5
 * KHÁC BIỆT: thêm @OrderColumn(name = "position") trên List<Tag>
 * HẬU QUẢ  : bảng nối có thêm cột position; xoá phần tử giữa thì dồn lại vị trí (G7-S06)
 */
@Entity(name = "G7M6_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m6_order_column", name = "product")
public class M6Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m6_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m6_product_seq", schema = "g7_m6_order_column", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — @JoinTable nằm ở đây, nên chỉ product.getTags() mới ghi bảng nối product_tag.
	// cascade ghi tường minh là rỗng: persist/remove product KHÔNG lan sang tag (tag là dữ liệu dùng chung).
	// Khai @JoinTable(schema = ...) tường minh, nếu không bảng nối rơi vào schema "public" (bẫy đã gặp ở G1-S07).
	@ManyToMany(fetch = FetchType.LAZY, cascade = {})
	@JoinTable(schema = "g7_m6_order_column", name = "product_tag",
		joinColumns = @JoinColumn(name = "product_id"),
		inverseJoinColumns = @JoinColumn(name = "tag_id"))
	@OrderColumn(name = "position") // thêm cột position vào bảng nối để lưu thứ tự trong List (G7-S06)
	private List<M6Tag> tags = new ArrayList<>();

	protected M6Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public M6Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<M6Tag> getTags() {
		return tags;
	}
}
