package com.example.hibernatelab.g6_one_to_one.v7_orphan;

import jakarta.persistence.CascadeType;
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
 * BIẾN THỂ : V7 (g6_v7_orphan)
 * BASELINE : V3 (g6_v3_bidi_owner_card)
 * KHÁC BIỆT: Customer.loyaltyCard thêm cascade = ALL, orphanRemoval = true
 * HẬU QUẢ  : customer.setLoyaltyCard(null) hoặc gán thẻ khác thì thẻ cũ bị DELETE (G6-S09, S10)
 */
@Entity(name = "G6V7_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v7_orphan", name = "customer")
public class V7Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v7_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v7_customer_seq", schema = "g6_v7_orphan", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — FK loyalty_card.customer_id do V7LoyaltyCard.customer quản lý.
	// KHÁC BIỆT CỦA BIẾN THỂ NẰM Ở ĐÂY: cascade = ALL, orphanRemoval = true.
	// orphanRemoval vẫn hoạt động dù đây là inverse side: nó so sánh giá trị property với bản snapshot lúc tải.
	@OneToOne(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private V7LoyaltyCard loyaltyCard;

	protected V7Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public V7Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public V7LoyaltyCard getLoyaltyCard() {
		return loyaltyCard;
	}

	public void setLoyaltyCard(V7LoyaltyCard loyaltyCard) {
		this.loyaltyCard = loyaltyCard;
	}
}
