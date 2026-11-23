package com.example.hibernatelab.g6_one_to_one.v3_bidi_owner_card;

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
 * BIẾN THỂ : V3 (g6_v3_bidi_owner_card)
 * BASELINE : V2 (g6_v2_fk_on_card)
 * KHÁC BIỆT: thêm Customer.loyaltyCard @OneToOne(mappedBy = "customer", fetch = LAZY) (inverse side)
 * HẬU QUẢ  : find(customer) vẫn SELECT thêm loyalty_card dù khai báo LAZY; set một phía không ghi FK (G6-S03, S05)
 */
@Entity(name = "G6V3_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v3_bidi_owner_card", name = "customer")
public class V3Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v3_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v3_customer_seq", schema = "g6_v3_bidi_owner_card", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — KHÔNG có cột nào ở bảng customer. FK nằm ở loyalty_card.customer_id (do V3LoyaltyCard.customer quản lý).
	// fetch = LAZY khai báo nhưng Hibernate KHÔNG tôn trọng được (G6-S03): customer không có cột FK để biết
	// "có thẻ hay không", nên phải SELECT loyalty_card ngay lúc tải customer.
	@OneToOne(mappedBy = "customer", fetch = FetchType.LAZY)
	private V3LoyaltyCard loyaltyCard;

	protected V3Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public V3Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public V3LoyaltyCard getLoyaltyCard() {
		return loyaltyCard;
	}

	public void setLoyaltyCard(V3LoyaltyCard loyaltyCard) {
		this.loyaltyCard = loyaltyCard;
	}
}
