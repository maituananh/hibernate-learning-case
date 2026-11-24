package com.example.hibernatelab.g13_enhanced.e1_inverse_one_to_one;

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
 * BIẾN THỂ : E1 (g13_e1_inverse_one_to_one)
 * BASELINE : G6-V3LoyaltyCard
 * KHÁC BIỆT: không có ở entity này (khác biệt duy nhất của E1 là enhancement, xem E1Customer)
 * HẬU QUẢ  : xem E1Customer
 */
@Entity(name = "G13E1_LoyaltyCard")
@Table(schema = "g13_e1_inverse_one_to_one", name = "loyalty_card")
public class E1LoyaltyCard {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13e1_card_seq")
	@SequenceGenerator(name = "g13e1_card_seq", schema = "g13_e1_inverse_one_to_one", sequenceName = "loyalty_card_seq", allocationSize = 50)
	private Long id;

	@Column(name = "card_no", nullable = false)
	private String cardNo;

	// OWNING side: giữ FK customer_id (có UNIQUE vì là 1-1).
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private E1Customer customer;

	protected E1LoyaltyCard() {
		// JPA bắt buộc có constructor không tham số
	}

	public E1LoyaltyCard(String cardNo) {
		this.cardNo = cardNo;
	}

	public Long getId() {
		return id;
	}

	public String getCardNo() {
		return cardNo;
	}

	public E1Customer getCustomer() {
		return customer;
	}

	public void setCustomer(E1Customer customer) {
		this.customer = customer;
	}
}
