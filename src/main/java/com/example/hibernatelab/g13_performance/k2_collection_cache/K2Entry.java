package com.example.hibernatelab.g13_performance.k2_collection_cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Mục con của {@link K2Category}. Cố ý KHÔNG {@code @Cacheable}.
 *
 * BIẾN THỂ : K2 (g13_k2_collection_cache)
 * BASELINE : chính nó
 * KHÁC BIỆT: không có (đây là baseline)
 * HẬU QUẢ  : khi collection cache trúng, Hibernate có id nhưng không có dữ liệu phần tử → SELECT từng phần tử (S08)
 */
@Entity(name = "G13K2_Entry")
@Table(schema = "g13_k2_collection_cache", name = "entry")
public class K2Entry {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13k2_entry_seq")
	@SequenceGenerator(name = "g13k2_entry_seq", schema = "g13_k2_collection_cache", sequenceName = "entry_seq", allocationSize = 50)
	private Long id;

	@Column(name = "label", nullable = false)
	private String label;

	// OWNING side: giữ FK category_id. LAZY tường minh.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private K2Category category;

	protected K2Entry() {
		// JPA bắt buộc có constructor không tham số
	}

	public K2Entry(String label, K2Category category) {
		this.label = label;
		this.category = category;
	}

	public Long getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}
}
