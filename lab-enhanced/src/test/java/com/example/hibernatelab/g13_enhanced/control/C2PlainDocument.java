package com.example.hibernatelab.g13_enhanced.control;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : C2 (g13_c2_plain_basic), entity ĐỐI CHỨNG nằm ở src/test nên KHÔNG bị bytecode enhancement
 * BASELINE : E2 (g13_e2_lazy_basic) ở src/main của lab-enhanced
 * KHÁC BIỆT: không enhancement (plugin chỉ áp cho source set main)
 * HẬU QUẢ  : @Basic(fetch = LAZY) bị bỏ qua, body luôn được SELECT cùng lúc (G13-S12b)
 */
@Entity(name = "G13C2_PlainDocument")
@Table(schema = "g13_c2_plain_basic", name = "document")
public class C2PlainDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13_c2_plain_basic_seq")
	@SequenceGenerator(name = "g13_c2_plain_basic_seq", schema = "g13_c2_plain_basic", sequenceName = "document_seq", allocationSize = 50)
	private Long id;

	@Column(name = "title", nullable = false)
	private String title;

	// Cột lớn (nội dung dài). fetch = LAZY nghĩa là "chỉ tải cột này khi có người đọc".
	// ĐÂY LÀ CHỈ DẪN, không phải mệnh lệnh: không có bytecode enhancement thì Hibernate không có chỗ chèn "chốt chặn"
	// vào field String nên bỏ qua LAZY và luôn SELECT cột này cùng các cột khác (G13-S12).
	@Basic(fetch = FetchType.LAZY)
	@Column(name = "body", columnDefinition = "text")
	private String body;

	protected C2PlainDocument() {
		// JPA bắt buộc có constructor không tham số
	}

	public C2PlainDocument(String title, String body) {
		this.title = title;
		this.body = body;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}
}
