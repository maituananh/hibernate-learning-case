package com.example.hibernatelab.g12_advanced_model.f1_formula_filter;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Formula;
import org.hibernate.annotations.SQLRestriction;

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
 * Khách hàng có một cột TÍNH TOÁN ({@code @Formula}) và một collection có {@code @SQLRestriction}.
 *
 * BIẾN THỂ : F1 (g12_f1_formula_filter)
 * BASELINE : chính nó (baseline của chủ đề formula / restriction / filter)
 * KHÁC BIỆT: không có (đây là baseline)
 * HẬU QUẢ  : orderCount là subquery chạy MỖI LẦN SELECT customer, không phải cột thật, chỉ đọc (G12-S12);
 *            notes chỉ chứa ghi chú chưa xoá mềm (G12-S13c)
 */
@Entity(name = "G12F1_Customer")
@Table(schema = "g12_f1_formula_filter", name = "customer")
public class F1Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12f1_customer_seq")
	@SequenceGenerator(name = "g12f1_customer_seq", schema = "g12_f1_formula_filter", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// Không có cột order_count trong DB. Mỗi lần SELECT customer, Hibernate nhét subquery này vào danh sách cột.
	// Chỉ được tính lại khi tải lại entity (find / query / refresh), KHÔNG tự cập nhật khi thêm order trong cùng PC.
	@Formula("(select count(*) from g12_f1_formula_filter.purchase_order o where o.customer_id = id)")
	private int orderCount;

	// INVERSE side. @SQLRestriction thêm "and deleted = false" vào câu SELECT nạp collection này.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	@SQLRestriction("deleted = false")
	private List<F1Note> notes = new ArrayList<>();

	protected F1Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public F1Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public int getOrderCount() {
		return orderCount;
	}

	public List<F1Note> getNotes() {
		return notes;
	}
}
