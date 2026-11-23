package com.example.hibernatelab.g6_one_to_one.v7_orphan;

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
 * BIẾN THỂ : V7 (g6_v7_orphan)
 * BASELINE : V3 (g6_v3_bidi_owner_card)
 * KHÁC BIỆT: giống V3 (owning side); khác biệt nằm ở V7Customer
 * HẬU QUẢ  : xem V7Customer
 */
@Entity(name = "G6V7_LoyaltyCard") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v7_orphan", name = "loyalty_card")
public class V7LoyaltyCard {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v7_loyalty_card_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v7_loyalty_card_seq", schema = "g6_v7_orphan", sequenceName = "loyalty_card_seq", allocationSize = 50)
	private Long id;

	@Column(name = "card_no", nullable = false)
	private String cardNo;

	// OWNING side — FK loyalty_card.customer_id nằm Ở BẢNG NÀY. Hibernate đọc/ghi FK từ property này.
	// fetch = LAZY ghi tường minh (mặc định của @OneToOne là EAGER). Không cascade: phải persist customer TRƯỚC.
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private V7Customer customer;

	protected V7LoyaltyCard() {
		// JPA bắt buộc có constructor không tham số
	}

	public V7LoyaltyCard(String cardNo) {
		this.cardNo = cardNo;
	}

	public Long getId() {
		return id;
	}

	public String getCardNo() {
		return cardNo;
	}

	public V7Customer getCustomer() {
		return customer;
	}

	public void setCustomer(V7Customer customer) {
		this.customer = customer;
	}
}
