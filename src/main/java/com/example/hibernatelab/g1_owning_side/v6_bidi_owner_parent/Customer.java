package com.example.hibernatelab.g1_owning_side.v6_bidi_owner_parent;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V6 — hai chiều nhưng OWNING side nằm ở phía "một" (hiếm gặp)
 * BASELINE : V4 (g1_v4_bidi_mappedby)
 * KHÁC BIỆT: lật ngược: Customer.orders có {@code @JoinColumn} (ghi FK), còn PurchaseOrder.customer bị tắt
 *            quyền ghi bằng {@code insertable = false, updatable = false}
 * HẬU QUẢ  : set {@code order.customer} KHÔNG ghi được FK; phải add vào {@code customer.orders} mới ghi (G1-S16).
 */
@Entity(name = "G1V6_Customer") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g1_v6_bidi_owner_parent", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v6_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g1v6_customer_seq", schema = "g1_v6_bidi_owner_parent", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — có @JoinColumn và KHÔNG có mappedBy → phía này ghi cột purchase_order.customer_id.
	// Cách ghi giống V3: INSERT order rồi UPDATE bù customer_id (🔬 xem G1-S16).
	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
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
