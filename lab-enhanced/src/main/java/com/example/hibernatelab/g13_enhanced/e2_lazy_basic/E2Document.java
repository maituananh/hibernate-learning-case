package com.example.hibernatelab.g13_enhanced.e2_lazy_basic;

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
 * BIẾN THỂ : E2 (g13_e2_lazy_basic), ĐÃ BỊ bytecode enhancement (enableLazyInitialization = true)
 * BASELINE : C2 (g13_c2_plain_basic, src/test/.../control): mapping y hệt, KHÔNG enhancement
 * KHÁC BIỆT: chỉ khác ở enhancement
 * HẬU QUẢ  : SELECT document không có cột body; đọc getBody() mới SELECT thêm (G13-S12a). Bản C2 thì body luôn nằm trong SELECT đầu (G13-S12b)
 */
@Entity(name = "G13E2_Document")
@Table(schema = "g13_e2_lazy_basic", name = "document")
public class E2Document {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13_e2_lazy_basic_seq")
	@SequenceGenerator(name = "g13_e2_lazy_basic_seq", schema = "g13_e2_lazy_basic", sequenceName = "document_seq", allocationSize = 50)
	private Long id;

	@Column(name = "title", nullable = false)
	private String title;

	// Cột lớn (nội dung dài). fetch = LAZY nghĩa là "chỉ tải cột này khi có người đọc".
	// ĐÂY LÀ CHỈ DẪN, không phải mệnh lệnh: không có bytecode enhancement thì Hibernate không có chỗ chèn "chốt chặn"
	// vào field String nên bỏ qua LAZY và luôn SELECT cột này cùng các cột khác (G13-S12).
	@Basic(fetch = FetchType.LAZY)
	@Column(name = "body", columnDefinition = "text")
	private String body;

	protected E2Document() {
		// JPA bắt buộc có constructor không tham số
	}

	public E2Document(String title, String body) {
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
