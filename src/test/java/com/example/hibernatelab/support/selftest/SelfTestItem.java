package com.example.hibernatelab.support.selftest;

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
 * Entity CHỈ để tự kiểm thử các helper trong {@code support} (schema {@code support_selftest}).
 * Có đủ: PK, cột NOT NULL, cột nullable, cột UNIQUE và một FK để {@code SchemaInspector} có cái mà đọc.
 */
@Entity(name = "SelfTest_Item")
@Table(schema = "support_selftest", name = "item")
public class SelfTestItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "selftest_item_seq")
	@SequenceGenerator(name = "selftest_item_seq", schema = "support_selftest", sequenceName = "item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "code", nullable = false, unique = true, length = 30)
	private String code;

	@Column(name = "note") // cố ý nullable (mặc định) để test đọc được "cột cho phép NULL"
	private String note;

	// OWNING side: giữ FK item.owner_id
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private SelfTestOwner owner;

	protected SelfTestItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public SelfTestItem(String code, String note) {
		this.code = code;
		this.note = note;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getNote() {
		return note;
	}

	public void setOwner(SelfTestOwner owner) {
		this.owner = owner;
	}
}
