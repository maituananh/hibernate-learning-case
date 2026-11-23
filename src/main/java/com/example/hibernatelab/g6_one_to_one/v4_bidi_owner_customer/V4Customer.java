package com.example.hibernatelab.g6_one_to_one.v4_bidi_owner_customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V4 (g6_v4_bidi_owner_customer)
 * BASELINE : V1 (g6_v1_fk_on_customer)
 * KHÁC BIỆT: giống V1 (owning side); khác biệt nằm ở V4LoyaltyCard
 * HẬU QUẢ  : xem V4LoyaltyCard
 */
@Entity(name = "G6V4_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v4_bidi_owner_customer", name = "customer")
public class V4Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v4_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v4_customer_seq", schema = "g6_v4_bidi_owner_customer", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — FK customer.loyalty_card_id nằm Ở BẢNG NÀY. Hibernate đọc/ghi FK từ property này.
	// fetch = LAZY ghi tường minh (mặc định của @OneToOne là EAGER). Không cascade: phải persist card TRƯỚC.
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "loyalty_card_id")
	private V4LoyaltyCard loyaltyCard;

	protected V4Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public V4Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public V4LoyaltyCard getLoyaltyCard() {
		return loyaltyCard;
	}

	public void setLoyaltyCard(V4LoyaltyCard loyaltyCard) {
		this.loyaltyCard = loyaltyCard;
	}
}
