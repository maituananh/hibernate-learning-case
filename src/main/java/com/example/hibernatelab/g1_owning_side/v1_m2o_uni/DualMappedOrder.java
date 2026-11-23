package com.example.hibernatelab.g1_owning_side.v1_m2o_uni;

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
 * G1-S06: một order mà cột FK {@code customer_id} được map HAI lần. Nằm ở bảng riêng
 * {@code order_dual_mapping} (không đụng tới {@link PurchaseOrder}) để V1 giữ nguyên là baseline.
 *
 * <p>Hai property cùng trỏ vào cột {@code customer_id}, nhưng CHỈ MỘT được quyền ghi:
 * <ul>
 *   <li>{@code customerId} (kiểu {@code Long}): quyền ghi, đây là OWNING side thật sự của cột.</li>
 *   <li>{@code customer} (kiểu entity): {@code insertable = false, updatable = false} → chỉ đọc.</li>
 * </ul>
 * Bỏ {@code insertable/updatable = false} thì Hibernate không khởi động được (xem entity [BOOT-FAIL] ở
 * {@code bootfail/g1s06}).
 */
@Entity(name = "G1V1_DualMappedOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v1_m2o_uni", name = "order_dual_mapping")
public class DualMappedOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v1_dual_order_seq")
	@SequenceGenerator(name = "g1v1_dual_order_seq", schema = "g1_v1_m2o_uni", sequenceName = "dual_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// Cột FK dạng số thuần: property DUY NHẤT có quyền ghi customer_id.
	@Column(name = "customer_id")
	private Long customerId;

	// Phía object chỉ để ĐỌC (điều hướng order → customer). Tắt quyền ghi nên set field này KHÔNG ra FK.
	// cố ý KHÔNG ghi fetch → mặc định EAGER.
	@ManyToOne
	@JoinColumn(name = "customer_id", insertable = false, updatable = false)
	private Customer customer;

	protected DualMappedOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public DualMappedOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public Long getCustomerId() {
		return customerId;
	}

	public void setCustomerId(Long customerId) {
		this.customerId = customerId;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}
}
