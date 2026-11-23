package com.example.hibernatelab.g5_fetch_n_plus_one.f0_defaults;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đơn hàng của biến thể F0.
 *
 * BIẾN THỂ : F0 (g5_f0_defaults)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: KHÔNG ghi `fetch` ở bất kỳ quan hệ nào: dùng mặc định JPA (@ManyToOne = EAGER, @OneToMany = LAZY)
 * HẬU QUẢ  : @ManyToOne EAGER nên find(order) JOIN luôn customer, còn JPQL thì sinh N+1 (G5-S01, S02)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G5F0_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f0_defaults", name = "purchase_order")
public class F0PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f0_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f0_purchase_order_seq", schema = "g5_f0_defaults", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — ghi FK purchase_order.customer_id.
	// fetch: CỐ Ý để mặc định. Mặc định của @ManyToOne là EAGER (ngược với @OneToMany là LAZY).
	@ManyToOne
	@JoinColumn(name = "customer_id")
	private F0Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// fetch: CỐ Ý để mặc định. Mặc định của @OneToMany là LAZY.
	@OneToMany(mappedBy = "order")
	private List<F0OrderItem> items = new ArrayList<>();

	protected F0PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public F0PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public F0Customer getCustomer() {
		return customer;
	}

	public List<F0OrderItem> getItems() {
		return items;
	}
}
