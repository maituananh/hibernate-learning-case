package com.example.hibernatelab.g1_owning_side.v4_bidi_mappedby;

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
 * BIẾN THỂ : V4 — hai chiều ĐÚNG CHUẨN ({@code @OneToMany(mappedBy)} + {@code @ManyToOne})
 * BASELINE : V1 (g1_v1_m2o_uni) — chỉ có @ManyToOne ở PurchaseOrder, Customer không có collection
 * KHÁC BIỆT: Customer thêm collection {@code orders} với {@code mappedBy = "customer"}
 * HẬU QUẢ  : DDL y hệt V1 (FK ở bảng con, KHÔNG có bảng nối). Collection chỉ là cách ĐIỀU HƯỚNG, không ghi FK:
 *            phải gán {@code order.customer} mới có FK. Vì vậy cần helper đồng bộ hai phía (G1-S12, S13, S14).
 */
@Entity(name = "G1V4_Customer") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g1_v4_bidi_mappedby", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v4_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g1v4_customer_seq", schema = "g1_v4_bidi_mappedby", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer" nghĩa là "FK do field PurchaseOrder.customer quản lý, tôi chỉ đọc".
	// Hibernate BỎ QUA collection này khi ghi DB (G1-S12). Nó chỉ dùng để duyệt customer → orders.
	// fetch = LAZY ghi tường minh. cố ý KHÔNG có cascade và KHÔNG có orphanRemoval (xem nhóm G2, G3).
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<PurchaseOrder> orders = new ArrayList<>();

	protected Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	/**
	 * Trả về CHÍNH list bên trong (không phải bản sao bất biến) để test được thao tác trực tiếp
	 * {@code customer.getOrders().add(...)}.
	 */
	public List<PurchaseOrder> getOrders() {
		return orders;
	}

	/**
	 * Helper đồng bộ HAI phía (pattern khuyến nghị, xem G1-S14): vừa thêm vào collection (inverse side,
	 * chỉ để bộ nhớ khớp), vừa gán {@code order.customer} (owning side, thứ thật sự quyết định FK).
	 */
	public void addOrder(PurchaseOrder order) {
		orders.add(order);
		order.setCustomer(this);
	}

	/** Gỡ cả hai phía. Không có orphanRemoval nên dòng order VẪN CÒN trong DB, chỉ {@code customer_id} thành NULL. */
	public void removeOrder(PurchaseOrder order) {
		orders.remove(order);
		order.setCustomer(null);
	}
}
