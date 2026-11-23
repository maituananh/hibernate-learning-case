package com.example.hibernatelab.g1_owning_side.v5_bidi_no_mappedby;

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
 * BIẾN THỂ : V5 — hai chiều nhưng quên mappedBy (xem {@link Customer})
 * BASELINE : V4 (g1_v4_bidi_mappedby)
 * KHÁC BIỆT: không có ở class này (khác biệt nằm ở {@code Customer.orders})
 * HẬU QUẢ  : PurchaseOrder.customer là owning side số 1 (ghi cột customer_id).
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G1V5_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v5_bidi_no_mappedby", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v5_purchase_order_seq")
	@SequenceGenerator(name = "g1v5_purchase_order_seq", schema = "g1_v5_bidi_no_mappedby", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side số 1 — ghi cột purchase_order.customer_id.
	// cố ý KHÔNG ghi fetch → mặc định EAGER.
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
