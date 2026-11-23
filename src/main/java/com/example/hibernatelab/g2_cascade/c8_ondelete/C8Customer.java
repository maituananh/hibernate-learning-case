package com.example.hibernatelab.g2_cascade.c8_ondelete;

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
 * BIẾN THỂ : C8 (g2_c8_ondelete)
 * BASELINE : C0 (g2_c0_none) — mọi quan hệ KHÔNG có cascade
 * KHÁC BIỆT: OrderItem.order có @OnDelete(action = CASCADE); PurchaseOrder.items KHÔNG có cascade JPA
 * HẬU QUẢ  : FK order_item.order_id có ON DELETE CASCADE ở DB: xoá order thì DB tự xoá item, Hibernate không DELETE từng item (G2-S10, S12).
 */
@Entity(name = "G2C8_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g2_c8_ondelete", name = "customer")
public class C8Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g2c8_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g2c8_customer_seq", schema = "g2_c8_ondelete", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK do PurchaseOrder.customer quản lý.
	// Cố ý KHÔNG có cascade ở tầng này (cascade của nhóm G2 chỉ đặt ở PurchaseOrder.items, trừ C4-DEEP).
	// fetch = LAZY ghi tường minh; không orphanRemoval (nhóm G2 không dùng, xem G3).
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<C8PurchaseOrder> orders = new ArrayList<>();

	protected C8Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public C8Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<C8PurchaseOrder> getOrders() {
		return orders;
	}

	/** Helper đồng bộ HAI phía: add vào collection (inverse) và gán order.customer (owning, thứ ghi FK). */
	public void addOrder(C8PurchaseOrder order) {
		orders.add(order);
		order.setCustomer(this);
	}
}
