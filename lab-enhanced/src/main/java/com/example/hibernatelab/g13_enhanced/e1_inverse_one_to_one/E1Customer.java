package com.example.hibernatelab.g13_enhanced.e1_inverse_one_to_one;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : E1 (g13_e1_inverse_one_to_one), nằm trong subproject lab-enhanced nên ĐÃ BỊ bytecode enhancement
 * BASELINE : G6-V3 (g6_v3_bidi_owner_card) ở project gốc: mapping y hệt, KHÔNG enhancement
 * KHÁC BIỆT: chỉ khác ở chỗ entity này được Hibernate enhance sau khi biên dịch (enableLazyInitialization = true)
 * HẬU QUẢ  : find(customer) KHÔNG còn SELECT loyalty_card; chạm getLoyaltyCard() mới SELECT (G13-S11, đối chiếu G6-S03a)
 */
@Entity(name = "G13E1_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g13_e1_inverse_one_to_one", name = "customer")
public class E1Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13e1_customer_seq")
	@SequenceGenerator(name = "g13e1_customer_seq", schema = "g13_e1_inverse_one_to_one", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side: bảng customer không có cột nào cho thẻ, FK nằm ở loyalty_card.customer_id.
	// Không enhancement thì LAZY ở đây bị bỏ qua (G6-S03a). Có enhancement thì Hibernate chèn "interceptor" vào chính
	// field này: lúc load customer field để trống, lần đầu getLoyaltyCard() mới đi SELECT loyalty_card.
	@OneToOne(mappedBy = "customer", fetch = FetchType.LAZY)
	private E1LoyaltyCard loyaltyCard;

	protected E1Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public E1Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public E1LoyaltyCard getLoyaltyCard() {
		return loyaltyCard;
	}

	public void setLoyaltyCard(E1LoyaltyCard loyaltyCard) {
		this.loyaltyCard = loyaltyCard;
	}
}
