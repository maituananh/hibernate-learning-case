package com.example.hibernatelab.g2_cascade.c4deep_all_chain;

import java.util.ArrayList;
import java.util.List;

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
 * BIẾN THỂ : C4Deep (g2_c4deep_all_chain)
 * BASELINE : C0 (g2_c0_none) — mọi quan hệ KHÔNG có cascade
 * KHÁC BIỆT: như C4, và Customer.orders CŨNG có cascade = ALL (cascade 3 tầng)
 * HẬU QUẢ  : persist(customer) kéo theo order, rồi order kéo theo item (G2-S11). Chỉ dùng cho S11.
 */
@Entity(name = "G2C4Deep_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g2_c4deep_all_chain", name = "customer")
public class C4DeepCustomer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g2c4deep_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g2c4deep_customer_seq", schema = "g2_c4deep_all_chain", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK do PurchaseOrder.customer quản lý. Biến thể này CÓ cascade = ALL
	// trên collection để cascade đi được 3 tầng customer → order → item (G2-S11).
	// fetch = LAZY ghi tường minh; không orphanRemoval (nhóm G2 không dùng, xem G3).
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<C4DeepPurchaseOrder> orders = new ArrayList<>();

	protected C4DeepCustomer() {
		// JPA bắt buộc có constructor không tham số
	}

	public C4DeepCustomer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<C4DeepPurchaseOrder> getOrders() {
		return orders;
	}

	/** Helper đồng bộ HAI phía: add vào collection (inverse) và gán order.customer (owning, thứ ghi FK). */
	public void addOrder(C4DeepPurchaseOrder order) {
		orders.add(order);
		order.setCustomer(this);
	}
}
