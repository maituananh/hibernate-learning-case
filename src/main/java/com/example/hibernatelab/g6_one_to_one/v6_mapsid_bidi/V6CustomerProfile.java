package com.example.hibernatelab.g6_one_to_one.v6_mapsid_bidi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V6 (g6_v6_mapsid_bidi)
 * BASELINE : V5 (g6_v5_mapsid_uni)
 * KHÁC BIỆT: giống V5 (owning side); khác biệt nằm ở V6Customer
 * HẬU QUẢ  : xem V6Customer
 */
@Entity(name = "G6V6_CustomerProfile") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v6_mapsid_bidi", name = "customer_profile")
public class V6CustomerProfile {

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
	private V6Customer customer;

	@Column(name = "bio")
	private String bio;

	protected V6CustomerProfile() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Truyền customer (đã persist) vào constructor: id của profile sẽ lấy theo customer lúc persist. */
	public V6CustomerProfile(V6Customer customer, String bio) {
		this.customer = customer;
		this.bio = bio;
	}

	public Long getId() {
		return id;
	}

	public V6Customer getCustomer() {
		return customer;
	}

	public String getBio() {
		return bio;
	}
}
