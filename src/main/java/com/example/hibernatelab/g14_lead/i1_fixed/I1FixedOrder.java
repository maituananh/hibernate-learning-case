package com.example.hibernatelab.g14_lead.i1_fixed;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đơn hàng. Dùng cho sự cố G14-I1.
 *
 * BIẾN THỂ : I1 (fixed), schema g14_i1_fixed
 * BASELINE : không có (G14 được miễn quy tắc một khác biệt, E7)
 * KHÁC BIỆT: giống hệt package còn lại của I1: mapping ĐÃ ĐÚNG quy tắc (mọi quan hệ LAZY). Lỗi không nằm ở mapping
 *            mà ở CÁCH ĐỌC (service/repository); đó là điều khiến N+1 khó thấy khi review.
 * HẬU QUẢ  : LAZY đúng nhưng ai đó chạm vào {@code customer} và {@code items} trong vòng lặp thì mỗi dòng tốn thêm SELECT
 */
@Entity(name = "G14I1F_Order")
@Table(schema = "g14_i1_fixed", name = "purchase_order")
public class I1FixedOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i1fixed_order_seq")
	@SequenceGenerator(name = "g14i1fixed_order_seq", schema = "g14_i1_fixed", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side: cột FK customer_id nằm ở bảng purchase_order. LAZY tường minh.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private I1FixedCustomer customer;

	// INVERSE side: FK nằm ở order_item.order_id. LAZY là mặc định của @OneToMany, ghi tường minh cho rõ.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private List<I1FixedOrderItem> items = new ArrayList<>();

	protected I1FixedOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public I1FixedOrder(String orderNo, I1FixedCustomer customer) {
		this.orderNo = orderNo;
		this.customer = customer;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public I1FixedCustomer getCustomer() {
		return customer;
	}

	public List<I1FixedOrderItem> getItems() {
		return items;
	}
}
