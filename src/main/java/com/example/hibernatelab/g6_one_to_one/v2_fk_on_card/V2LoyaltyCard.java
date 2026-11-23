package com.example.hibernatelab.g6_one_to_one.v2_fk_on_card;

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
 * BIẾN THỂ : V2 (g6_v2_fk_on_card)
 * BASELINE : V1 (g6_v1_fk_on_customer)
 * KHÁC BIỆT: LoyaltyCard.customer là @OneToOne @JoinColumn (owning), một chiều
 * HẬU QUẢ  : FK ở bảng loyalty_card, nghĩa là thẻ 'thuộc về' customer (G6-S01, S02)
 */
@Entity(name = "G6V2_LoyaltyCard") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v2_fk_on_card", name = "loyalty_card")
public class V2LoyaltyCard {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v2_loyalty_card_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v2_loyalty_card_seq", schema = "g6_v2_fk_on_card", sequenceName = "loyalty_card_seq", allocationSize = 50)
	private Long id;

	@Column(name = "card_no", nullable = false)
	private String cardNo;

	// OWNING side — FK loyalty_card.customer_id nằm Ở BẢNG NÀY. Hibernate đọc/ghi FK từ property này.
	// fetch = LAZY ghi tường minh (mặc định của @OneToOne là EAGER). Không cascade: phải persist customer TRƯỚC.
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private V2Customer customer;

	protected V2LoyaltyCard() {
		// JPA bắt buộc có constructor không tham số
	}

	public V2LoyaltyCard(String cardNo) {
		this.cardNo = cardNo;
	}

	public Long getId() {
		return id;
	}

	public String getCardNo() {
		return cardNo;
	}

	public V2Customer getCustomer() {
		return customer;
	}

	public void setCustomer(V2Customer customer) {
		this.customer = customer;
	}
}
