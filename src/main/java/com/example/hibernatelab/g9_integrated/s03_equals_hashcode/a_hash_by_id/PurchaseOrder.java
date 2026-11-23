package com.example.hibernatelab.g9_integrated.s03_equals_hashcode.a_hash_by_id;

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
 * Đơn hàng (cha) của G9-S03a: collection items là {@link Set} nên hashCode của OrderItem quyết định nó nằm "ngăn" nào trong HashSet.
 *
 * BIẾN THỂ : G9-S03a (g9_s03a_hash_by_id)
 * BASELINE : G3-O0 (INVERSE side, cascade ALL, orphanRemoval), nhưng dùng Set thay List
 * KHÁC BIỆT: equals/hashCode của OrderItem dựa trên id sinh tự động (cách làm rất phổ biến và SAI)
 * HẬU QUẢ  : id là null lúc add vào Set rồi ĐỔI sau persist → hashCode đổi → Set.remove() không tìm thấy → orphanRemoval không chạy (G9-S03a)
 */
@Entity(name = "G9S03A_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s03a_hash_by_id", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s03a_purchase_order_seq")
	@SequenceGenerator(name = "g9s03a_purchase_order_seq", schema = "g9_s03a_hash_by_id", sequenceName = "purchase_order_seq", allocationSize = 50)
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
