package com.example.hibernatelab.g1_owning_side.v4_bidi_mappedby;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V4 — hai chiều đúng chuẩn (xem {@link Customer})
 * BASELINE : V1 (g1_v1_m2o_uni) — chỉ có @ManyToOne ở PurchaseOrder, Customer không có collection
 * KHÁC BIỆT: phía cha có thêm {@code orders(mappedBy = "customer")}; phía con giữ nguyên {@code @ManyToOne}
 * HẬU QUẢ  : PurchaseOrder vẫn là OWNING side duy nhất, nên SQL giống hệt V1.
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G1V4_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v4_bidi_mappedby", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v4_purchase_order_seq")
	@SequenceGenerator(name = "g1v4_purchase_order_seq", schema = "g1_v4_bidi_mappedby", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — duy nhất ghi FK purchase_order.customer_id. Customer.orders (mappedBy) chỉ là INVERSE side.
	// cố ý KHÔNG ghi fetch → mặc định EAGER (giống V1).
	@ManyToOne
	@JoinColumn(name = "customer_id")
	private Customer customer;

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

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}
}
