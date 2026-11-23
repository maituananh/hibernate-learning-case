package com.example.hibernatelab.g6_one_to_one.v5_mapsid_uni;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V5 (g6_v5_mapsid_uni)
 * BASELINE : V2 (g6_v2_fk_on_card)
 * KHÁC BIỆT: FK thường đổi thành @MapsId: customer_id vừa là PK vừa là FK, không còn sequence riêng
 * HẬU QUẢ  : profile.id == customer.id; không thể có hai profile cho một customer ngay ở PK (G6-S06, S07)
 */
@Entity(name = "G6V5_CustomerProfile") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v5_mapsid_uni", name = "customer_profile")
public class V5CustomerProfile {

	// KHÔNG có @GeneratedValue: id của profile được SUY RA từ customer nhờ @MapsId (G6-S06).
	// Thêm @GeneratedValue vào đây là mapping mâu thuẫn, xem G6-S13 ([BOOT-FAIL]).
	@Id
	private Long id;

	// OWNING side — cột customer_profile.customer_id vừa là FK vừa là PRIMARY KEY (vì @MapsId).
	// @MapsId (không có value) nghĩa là: "id của tôi = id của entity được trỏ tới".
	// fetch = LAZY ghi tường minh (mặc định của @OneToOne là EAGER).
	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "customer_id")
	private V5Customer customer;

	@Column(name = "bio")
	private String bio;

	protected V5CustomerProfile() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Truyền customer (đã persist) vào constructor: id của profile sẽ lấy theo customer lúc persist. */
	public V5CustomerProfile(V5Customer customer, String bio) {
		this.customer = customer;
		this.bio = bio;
	}

	public Long getId() {
		return id;
	}

	public V5Customer getCustomer() {
		return customer;
	}

	public String getBio() {
		return bio;
	}
}
