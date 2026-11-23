package com.example.hibernatelab.g6_one_to_one.v4_bidi_owner_customer;

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
 * BIẾN THỂ : V4 (g6_v4_bidi_owner_customer)
 * BASELINE : V1 (g6_v1_fk_on_customer)
 * KHÁC BIỆT: thêm LoyaltyCard.customer @OneToOne(mappedBy = "loyaltyCard", fetch = LAZY) (inverse side)
 * HẬU QUẢ  : FK lật ngược so với V3: find(customer) LAZY đúng, còn find(loyaltyCard) mới là phía bị SELECT thêm (G6-S04)
 */
@Entity(name = "G6V4_LoyaltyCard") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v4_bidi_owner_customer", name = "loyalty_card")
public class V4LoyaltyCard {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v4_loyalty_card_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v4_loyalty_card_seq", schema = "g6_v4_bidi_owner_customer", sequenceName = "loyalty_card_seq", allocationSize = 50)
	private Long id;

	@Column(name = "card_no", nullable = false)
	private String cardNo;

	// INVERSE side — không có cột nào ở bảng loyalty_card. FK nằm ở customer.loyalty_card_id (do V4Customer.loyaltyCard quản lý).
	// LAZY ở phía inverse bị bỏ qua giống G6-S03: find(loyaltyCard) phải SELECT thêm để biết customer nào trỏ tới thẻ.
	@OneToOne(mappedBy = "loyaltyCard", fetch = FetchType.LAZY)
	private V4Customer customer;

	protected V4LoyaltyCard() {
		// JPA bắt buộc có constructor không tham số
	}

	public V4LoyaltyCard(String cardNo) {
		this.cardNo = cardNo;
	}

	public Long getId() {
		return id;
	}

	public String getCardNo() {
		return cardNo;
	}

	public V4Customer getCustomer() {
		return customer;
	}

	public void setCustomer(V4Customer customer) {
		this.customer = customer;
	}
}
