package com.example.hibernatelab.g6_one_to_one.v1_fk_on_customer;

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
 * BIẾN THỂ : V1 (g6_v1_fk_on_customer)
 * BASELINE : (baseline của G6)
 * KHÁC BIỆT: FK ở bảng customer: Customer.loyaltyCard là @OneToOne @JoinColumn, một chiều
 * HẬU QUẢ  : cột customer.loyalty_card_id; LoyaltyCard không biết gì về Customer (G6-S01, S11)
 */
@Entity(name = "G6V1_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v1_fk_on_customer", name = "customer")
public class V1Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v1_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v1_customer_seq", schema = "g6_v1_fk_on_customer", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — FK customer.loyalty_card_id nằm Ở BẢNG NÀY. Hibernate đọc/ghi FK từ property này.
	// fetch = LAZY ghi tường minh (mặc định của @OneToOne là EAGER). Không cascade: phải persist card TRƯỚC.
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "loyalty_card_id")
	private V1LoyaltyCard loyaltyCard;

	protected V1Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public V1Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public V1LoyaltyCard getLoyaltyCard() {
		return loyaltyCard;
	}

	public void setLoyaltyCard(V1LoyaltyCard loyaltyCard) {
		this.loyaltyCard = loyaltyCard;
	}
}
