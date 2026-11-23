package com.example.hibernatelab.g1_owning_side.v5_bidi_no_mappedby;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V5 — hai chiều nhưng QUÊN {@code mappedBy} (lỗi kinh điển)
 * BASELINE : V4 (g1_v4_bidi_mappedby)
 * KHÁC BIỆT: bỏ {@code mappedBy = "customer"} khỏi {@code @OneToMany}
 * HẬU QUẢ  : có HAI owning side độc lập cho cùng một ý nghĩa: cột {@code purchase_order.customer_id}
 *            VÀ bảng nối {@code customer_orders}. Dữ liệu bị ghi 2 lần và có thể mâu thuẫn (G1-S15).
 */
@Entity(name = "G1V5_Customer") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g1_v5_bidi_no_mappedby", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v5_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g1v5_customer_seq", schema = "g1_v5_bidi_no_mappedby", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side số 2 — cố ý QUÊN mappedBy! Không có mappedBy và không có @JoinColumn nên Hibernate coi đây là
	// một quan hệ một chiều RIÊNG và tạo bảng nối customer_orders, hoàn toàn tách biệt với PurchaseOrder.customer.
	// @JoinTable chỉ để đặt bảng nối đúng schema và đặt tên cột (giống V2), không đổi hành vi.
	@OneToMany(fetch = FetchType.LAZY)
	@JoinTable(schema = "g1_v5_bidi_no_mappedby", name = "customer_orders",
		joinColumns = @JoinColumn(name = "customer_id"), inverseJoinColumns = @JoinColumn(name = "orders_id"))
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
}
