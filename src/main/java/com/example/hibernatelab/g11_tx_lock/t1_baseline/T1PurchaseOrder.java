package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import java.util.ArrayList;
import java.util.List;

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
 * Đơn hàng có collection {@code items} LAZY, dùng ở G11-S11 (lock + left join fetch) và G11-S12 (OSIV).
 *
 * BIẾN THỂ : T1 (g11_t1_baseline)
 * BASELINE : T1 (g11_t1_baseline)
 * KHÁC BIỆT: không có
 * HẬU QUẢ  : chạm {@code getItems()} ngoài transaction/session thì LazyInitializationException (G11-S12a)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G11T1_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g11_t1_baseline", name = "purchase_order")
public class T1PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g11t1_purchase_order_seq")
	@SequenceGenerator(name = "g11t1_purchase_order_seq", schema = "g11_t1_baseline", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
	private List<T1OrderItem> items = new ArrayList<>();

	protected T1PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public T1PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public List<T1OrderItem> getItems() {
		return items;
	}
}
