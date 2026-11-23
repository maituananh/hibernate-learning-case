package com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key;

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
 * Dòng hàng với {@code hashCode} dựa trên business key {@code lineCode}.
 *
 * BIẾN THỂ : G9-S03b (g9_s03b_business_key)
 * BASELINE : G9-S03a (hashCode theo id)
 * KHÁC BIỆT: equals/hashCode dùng {@code lineCode}: gán ngay ở constructor, không có setter, không đổi suốt vòng đời
 * HẬU QUẢ  : item luôn nằm đúng "ngăn" trong HashSet; remove() và orphanRemoval chạy đúng
 */
@Entity(name = "G9S03B_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s03b_business_key", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s03b_order_item_seq")
	@SequenceGenerator(name = "g9s03b_order_item_seq", schema = "g9_s03b_business_key", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// Business key: duy nhất trong một đơn, bất biến (không có setter). Đây là thứ equals/hashCode dựa vào.
	@Column(name = "line_code", nullable = false, updatable = false)
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

	/**
	 * Dùng {@code instanceof} (KHÔNG dùng getClass()) và đọc field qua getter: nếu other là PROXY của Hibernate
	 * (class con sinh ra lúc chạy) thì getClass() khác hẳn nhưng instanceof vẫn đúng, và getter mới kích hoạt nạp dữ liệu proxy.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof OrderItem)) {
			return false;
		}
		OrderItem that = (OrderItem) other;
		return Objects.equals(lineCode, that.getLineCode());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(lineCode);
	}

	/**
	 * BẪY (➕ proxy): phiên bản so sánh dùng getClass(). Với proxy thì getClass() là lớp con sinh ra → luôn false.
	 * Chỉ để test G9-S03d chứng minh; đừng viết equals() như thế này.
	 */
	public boolean equalsUsingGetClass(Object other) {
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		return Objects.equals(lineCode, ((OrderItem) other).lineCode);
	}
}
