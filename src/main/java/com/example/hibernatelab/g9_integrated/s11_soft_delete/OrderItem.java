package com.example.hibernatelab.g9_integrated.s11_soft_delete;

import org.hibernate.annotations.SoftDelete;

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
 * Dòng hàng dùng xoá mềm (soft delete).
 *
 * BIẾN THỂ : G9-S11 (g9_s11_soft_delete)
 * BASELINE : G3-O0
 * KHÁC BIỆT: {@code @SoftDelete(columnName = "deleted")}: Hibernate thêm cột boolean `deleted`
 * HẬU QUẢ  : em.remove() sinh UPDATE đặt cờ thay vì DELETE; mọi SELECT tự thêm điều kiện loại dòng đã xoá
 */
// @SoftDelete (Hibernate 6.4+): cột `deleted` do Hibernate quản lý, không khai báo field trong entity.
@SoftDelete(columnName = "deleted")
@Entity(name = "G9S11_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s11_soft_delete", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s11_order_item_seq")
	@SequenceGenerator(name = "g9s11_order_item_seq", schema = "g9_s11_soft_delete", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "sku", nullable = false)
	private String sku;

	// OWNING side — FK order_item.order_id.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private PurchaseOrder order;

	protected OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public OrderItem(String sku) {
		this.sku = sku;
	}

	public Long getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public void setOrder(PurchaseOrder order) {
		this.order = order;
	}
}
