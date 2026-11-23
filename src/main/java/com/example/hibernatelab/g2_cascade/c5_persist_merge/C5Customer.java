package com.example.hibernatelab.g2_cascade.c5_persist_merge;

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
 * BIẾN THỂ : C5 (g2_c5_persist_merge)
 * BASELINE : C0 (g2_c0_none) — mọi quan hệ KHÔNG có cascade
 * KHÁC BIỆT: PurchaseOrder.items có cascade = {PERSIST, MERGE}, THIẾU DETACH, REFRESH, REMOVE
 * HẬU QUẢ  : detach(order) và refresh(order) KHÔNG lan xuống item, nên item vẫn MANAGED (G2-S06, S07).
 */
@Entity(name = "G2C5_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g2_c5_persist_merge", name = "customer")
public class C5Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g2c5_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g2c5_customer_seq", schema = "g2_c5_persist_merge", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK do PurchaseOrder.customer quản lý.
	// Cố ý KHÔNG có cascade ở tầng này (cascade của nhóm G2 chỉ đặt ở PurchaseOrder.items, trừ C4-DEEP).
	// fetch = LAZY ghi tường minh; không orphanRemoval (nhóm G2 không dùng, xem G3).
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<C5PurchaseOrder> orders = new ArrayList<>();

	protected C5Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public C5Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<C5PurchaseOrder> getOrders() {
		return orders;
	}

	/** Helper đồng bộ HAI phía: add vào collection (inverse) và gán order.customer (owning, thứ ghi FK). */
	public void addOrder(C5PurchaseOrder order) {
		orders.add(order);
		order.setCustomer(this);
	}
}
