package com.example.hibernatelab.support.selftest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Entity CHỈ để tự kiểm thử các helper trong {@code support} (schema {@code support_selftest}).
 * Không liên quan tới bài học Hibernate nào.
 */
@Entity(name = "SelfTest_Owner")
@Table(schema = "support_selftest", name = "owner")
public class SelfTestOwner {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "selftest_owner_seq")
	@SequenceGenerator(name = "selftest_owner_seq", schema = "support_selftest", sequenceName = "owner_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected SelfTestOwner() {
		// JPA bắt buộc có constructor không tham số
	}

	public SelfTestOwner(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
