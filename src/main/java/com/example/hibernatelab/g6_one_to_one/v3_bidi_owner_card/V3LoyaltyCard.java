package com.example.hibernatelab.g6_one_to_one.v3_bidi_owner_card;

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
 * BIẾN THỂ : V3 (g6_v3_bidi_owner_card)
 * BASELINE : V2 (g6_v2_fk_on_card)
 * KHÁC BIỆT: giống V2 (owning side); khác biệt nằm ở V3Customer
 * HẬU QUẢ  : xem V3Customer
 */
@Entity(name = "G6V3_LoyaltyCard") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v3_bidi_owner_card", name = "loyalty_card")
public class V3LoyaltyCard {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v3_loyalty_card_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v3_loyalty_card_seq", schema = "g6_v3_bidi_owner_card", sequenceName = "loyalty_card_seq", allocationSize = 50)
	private Long id;

	@Column(name = "card_no", nullable = false)
	private String cardNo;

	// OWNING side — FK loyalty_card.customer_id nằm Ở BẢNG NÀY. Hibernate đọc/ghi FK từ property này.
	// fetch = LAZY ghi tường minh (mặc định của @OneToOne là EAGER). Không cascade: phải persist customer TRƯỚC.
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private V3Customer customer;

	protected V3LoyaltyCard() {
		// JPA bắt buộc có constructor không tham số
	}

	public V3LoyaltyCard(String cardNo) {
		this.cardNo = cardNo;
	}

	public Long getId() {
		return id;
	}

	public String getCardNo() {
		return cardNo;
	}

	public V3Customer getCustomer() {
		return customer;
	}

	public void setCustomer(V3Customer customer) {
		this.customer = customer;
	}
}
