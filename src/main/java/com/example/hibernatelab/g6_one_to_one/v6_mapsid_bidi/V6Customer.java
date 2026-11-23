package com.example.hibernatelab.g6_one_to_one.v6_mapsid_bidi;

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
 * BIẾN THỂ : V6 (g6_v6_mapsid_bidi)
 * BASELINE : V5 (g6_v5_mapsid_uni)
 * KHÁC BIỆT: thêm Customer.profile @OneToOne(mappedBy = "customer", fetch = LAZY) (inverse side)
 * HẬU QUẢ  : find(customer) còn SELECT thêm customer_profile hay không (G6-S08)
 */
@Entity(name = "G6V6_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v6_mapsid_bidi", name = "customer")
public class V6Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v6_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v6_customer_seq", schema = "g6_v6_mapsid_bidi", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — không có cột nào ở bảng customer. Profile giữ FK (cũng là PK) customer_profile.customer_id.
	@OneToOne(mappedBy = "customer", fetch = FetchType.LAZY)
	private V6CustomerProfile profile;

	protected V6Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public V6Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public V6CustomerProfile getProfile() {
		return profile;
	}

	public void setProfile(V6CustomerProfile profile) {
		this.profile = profile;
	}
}
