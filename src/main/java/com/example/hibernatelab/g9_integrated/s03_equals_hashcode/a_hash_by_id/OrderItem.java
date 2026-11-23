package com.example.hibernatelab.g9_integrated.s03_equals_hashcode.a_hash_by_id;

import java.util.Objects;

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
 * Dòng hàng với {@code hashCode} dựa trên id.
 *
 * BIẾN THỂ : G9-S03a (g9_s03a_hash_by_id)
 * BASELINE : G9-S03b (hashCode theo business key)
 * KHÁC BIỆT: equals/hashCode dùng field {@code id} (null trước persist, có giá trị sau persist)
 * HẬU QUẢ  : item nằm sai "ngăn" trong HashSet sau persist; remove() trả về false và item không bị xoá
 */
@Entity(name = "G9S03A_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s03a_hash_by_id", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s03a_order_item_seq")
	@SequenceGenerator(name = "g9s03a_order_item_seq", schema = "g9_s03a_hash_by_id", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "line_code", nullable = false)
	private String lineCode;

	// OWNING side — FK order_item.order_id.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private PurchaseOrder order;

	protected OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public OrderItem(String lineCode) {
		this.lineCode = lineCode;
	}

	public Long getId() {
		return id;
	}

	public String getLineCode() {
		return lineCode;
	}

	public PurchaseOrder getOrder() {
		return order;
	}

	public void setOrder(PurchaseOrder order) {
		this.order = order;
	}

	/** Hai item bằng nhau khi cùng id. Id null (chưa persist) thì chỉ bằng chính nó. */
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof OrderItem)) {
			return false;
		}
		OrderItem that = (OrderItem) other;
		return id != null && id.equals(that.id);
	}

	/**
	 * BẪY: hashCode phụ thuộc id, mà id đổi từ null sang một số ngay lúc persist() (Hibernate lấy từ sequence).
	 * Phần tử đã nằm trong HashSet với hash cũ (hash của null) sẽ không bao giờ được tìm thấy lại bằng hash mới.
	 */
	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
