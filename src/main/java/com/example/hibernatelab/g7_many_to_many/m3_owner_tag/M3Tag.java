package com.example.hibernatelab.g7_many_to_many.m3_owner_tag;

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
 * BIẾN THỂ : M3 (g7_m3_owner_tag)
 * BASELINE : M1
 * KHÁC BIỆT: LẬT owner: @JoinTable nằm trên Tag.products, Product.tags là mappedBy
 * HẬU QUẢ  : ngược với M1: add ở Tag mới ghi bảng nối (G7-S03)
 */
@Entity(name = "G7M3_Tag") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m3_owner_tag", name = "tag")
public class M3Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m3_tag_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m3_tag_seq", schema = "g7_m3_owner_tag", sequenceName = "tag_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — @JoinTable nằm ở ĐÂY nên chỉ tag.getProducts().add(product) mới ghi bảng nối (G7-S03).
	// cascade ghi tường minh là rỗng: persist/remove product KHÔNG lan sang tag (tag là dữ liệu dùng chung).
	@ManyToMany(fetch = FetchType.LAZY, cascade = {})
	@JoinTable(schema = "g7_m3_owner_tag", name = "product_tag",
		joinColumns = @JoinColumn(name = "tag_id"),
		inverseJoinColumns = @JoinColumn(name = "product_id"))
	private Set<M3Product> products = new HashSet<>();

	protected M3Tag() {
		// JPA bắt buộc có constructor không tham số
	}

	public M3Tag(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<M3Product> getProducts() {
		return products;
	}
}
