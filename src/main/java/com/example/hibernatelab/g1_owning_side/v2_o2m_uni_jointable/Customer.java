package com.example.hibernatelab.g1_owning_side.v2_o2m_uni_jointable;

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
 * BIẾN THỂ : V2 — {@code @OneToMany} MỘT CHIỀU, KHÔNG có {@code @JoinColumn}
 * BASELINE : V1 (g1_v1_m2o_uni) — chỉ có @ManyToOne ở PurchaseOrder, Customer không có collection
 * KHÁC BIỆT: quan hệ chuyển sang phía cha ({@code Customer.orders}), PurchaseOrder không còn field customer
 * HẬU QUẢ  : không có {@code @JoinColumn} nên Hibernate tạo BẢNG NỐI {@code customer_orders}
 *            (với cột unique trên order). Thêm 1 order = INSERT order + INSERT bảng nối (G1-S07, S08).
 */
@Entity(name = "G1V2_Customer") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g1_v2_o2m_uni_jointable", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v2_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g1v2_customer_seq", schema = "g1_v2_o2m_uni_jointable", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side (duy nhất) — vì không có mappedBy và không có @JoinColumn nên Hibernate dùng BẢNG NỐI.
	// @JoinTable ở đây KHÔNG đổi hành vi (không có nó Hibernate vẫn dùng bảng nối), ta chỉ ghi để đặt bảng nối đúng
	// schema của biến thể và đặt tên cột cho dễ đọc. 🔬 Nếu bỏ hẳn: bảng nối rơi vào schema mặc định "public" (đụng V5)
	// và cột đầu tên theo tên ENTITY ("g1_v2_customer_id"), xem docs/RESULTS.md (G1-S07).
	// Collection kiểu List không có @OrderColumn = "bag": Hibernate không biết vị trí từng phần tử (xem G1-S08).
	// fetch = LAZY ghi tường minh (cũng là mặc định của @OneToMany). cố ý KHÔNG có cascade (G1 không dùng cascade).
	@OneToMany(fetch = FetchType.LAZY)
	@JoinTable(schema = "g1_v2_o2m_uni_jointable", name = "customer_orders",
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
