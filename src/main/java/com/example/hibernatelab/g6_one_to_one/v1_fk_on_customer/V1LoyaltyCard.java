package com.example.hibernatelab.g6_one_to_one.v1_fk_on_customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V1 (g6_v1_fk_on_customer)
 * BASELINE : (baseline của G6)
 * KHÁC BIỆT: xem V1Customer
 * HẬU QUẢ  : LoyaltyCard không có cột FK, không có property trỏ về Customer
 */
@Entity(name = "G6V1_LoyaltyCard") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v1_fk_on_customer", name = "loyalty_card")
public class V1LoyaltyCard {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v1_loyalty_card_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v1_loyalty_card_seq", schema = "g6_v1_fk_on_customer", sequenceName = "loyalty_card_seq", allocationSize = 50)
	private Long id;

	@Column(name = "card_no", nullable = false)
	private String cardNo;

	protected V1LoyaltyCard() {
		// JPA bắt buộc có constructor không tham số
	}

	public V1LoyaltyCard(String cardNo) {
		this.cardNo = cardNo;
	}

	public Long getId() {
		return id;
	}

	public String getCardNo() {
		return cardNo;
	}
}
