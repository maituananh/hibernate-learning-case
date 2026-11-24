package com.example.hibernatelab.g13_performance.k3_query_cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Nhãn sản phẩm. Cố ý KHÔNG có {@code @Cacheable}: entity không vào cache cấp 2, nhưng query vẫn có thể vào query cache.
 *
 * BIẾN THỂ : K3 (g13_k3_query_cache)
 * BASELINE : chính nó
 * KHÁC BIỆT: không có (đây là baseline); so với K1 thì thiếu @Cacheable
 * HẬU QUẢ  : query cache chỉ lưu danh sách id, entity không cache nên phải tải lại từng cái (S09a)
 */
@Entity(name = "G13K3_Tag")
@Table(schema = "g13_k3_query_cache", name = "tag")
public class K3Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13k3_tag_seq")
	@SequenceGenerator(name = "g13k3_tag_seq", schema = "g13_k3_query_cache", sequenceName = "tag_seq", allocationSize = 50)
	private Long id;

	@Column(name = "label", nullable = false)
	private String label;

	@Column(name = "active", nullable = false)
	private boolean active;

	protected K3Tag() {
		// JPA bắt buộc có constructor không tham số
	}

	public K3Tag(String label, boolean active) {
		this.label = label;
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public boolean isActive() {
		return active;
	}
}
