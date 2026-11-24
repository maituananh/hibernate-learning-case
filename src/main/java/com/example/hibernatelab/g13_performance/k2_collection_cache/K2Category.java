package com.example.hibernatelab.g13_performance.k2_collection_cache;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Danh mục có tập mục con; CẢ entity và collection {@code entries} đều được cache, còn {@link K2Entry} thì KHÔNG.
 *
 * BIẾN THỂ : K2 (g13_k2_collection_cache)
 * BASELINE : chính nó
 * KHÁC BIỆT: không có (đây là baseline); so với K1 thì thêm collection cache
 * HẬU QUẢ  : cache collection chỉ lưu DANH SÁCH ID của phần tử; phần tử không cache thì mỗi cái một SELECT (S08)
 */
@Entity(name = "G13K2_Category")
@Table(schema = "g13_k2_collection_cache", name = "category")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class K2Category {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13k2_category_seq")
	@SequenceGenerator(name = "g13k2_category_seq", schema = "g13_k2_collection_cache", sequenceName = "category_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side (FK category_id nằm ở bảng entry). Collection có @Cache riêng: cache collection là một REGION khác cache entity.
	@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
	@OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
	private List<K2Entry> entries = new ArrayList<>();

	protected K2Category() {
		// JPA bắt buộc có constructor không tham số
	}

	public K2Category(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<K2Entry> getEntries() {
		return entries;
	}
}
