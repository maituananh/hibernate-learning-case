package com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
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
 * Đơn hàng (cha) của G9-S03b: collection items là {@link Set} nên hashCode của OrderItem quyết định nó nằm "ngăn" nào trong HashSet.
 *
 * BIẾN THỂ : G9-S03b (g9_s03b_business_key)
 * BASELINE : G3-O0 (INVERSE side, cascade ALL, orphanRemoval), nhưng dùng Set thay List
 * KHÁC BIỆT: equals/hashCode của OrderItem dựa trên business key bất biến (lineCode) gán ngay từ constructor
 * HẬU QUẢ  : hashCode không đổi suốt vòng đời → Set.remove() và orphanRemoval chạy đúng (G9-S03b)
 */
@Entity(name = "G9S03B_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s03b_business_key", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s03b_purchase_order_seq")
	@SequenceGenerator(name = "g9s03b_purchase_order_seq", schema = "g9_s03b_business_key", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// INVERSE side — mappedBy = "order". Set (không phải List) để G9-S03 thấy rõ vai trò của hashCode.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<OrderItem> items = new HashSet<>();

	protected PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public Set<OrderItem> getItems() {
		return items;
	}

	/** Giữ hai phía đồng bộ: thêm vào Set (inverse) VÀ gán item.order (owning, ghi FK). */
	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	/**
	 * Trả về kết quả của {@code Set.remove}: false nghĩa là KHÔNG tìm thấy phần tử (vì hashCode đã đổi)
	 * và orphanRemoval không có gì để xoá. Chỉ khi remove thành công mới gỡ owning side.
	 */
	public boolean removeItem(OrderItem item) {
		boolean removed = items.remove(item);
		if (removed) {
			item.setOrder(null);
		}
		return removed;
	}
}
