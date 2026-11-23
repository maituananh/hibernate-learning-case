package com.example.hibernatelab.bootfail.g6s13;

import com.example.hibernatelab.g6_one_to_one.v5_mapsid_uni.V5Customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * [BOOT-FAIL] G6-S13: entity CỐ Ý MÂU THUẪN, nằm ở package {@code bootfail} nên entity scan không thấy.
 *
 * <p>Mâu thuẫn: {@code @MapsId} nói "id của tôi lấy từ customer", còn {@code @GeneratedValue} nói "id do DB/sequence sinh".
 * Bản ĐÚNG (xem {@code V5CustomerProfile}) bỏ {@code @GeneratedValue}.
 */
@Entity(name = "BootFail_MapsIdWithGeneratedValueProfile")
@Table(schema = "g6_v5_mapsid_uni", name = "customer_profile_generated")
public class MapsIdWithGeneratedValueProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // ← mâu thuẫn với @MapsId bên dưới
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "customer_id")
	private V5Customer customer;

	@Column(name = "bio")
	private String bio;

	protected MapsIdWithGeneratedValueProfile() {
		// JPA bắt buộc có constructor không tham số
	}

	public MapsIdWithGeneratedValueProfile(V5Customer customer, String bio) {
		this.customer = customer;
		this.bio = bio;
	}
}
