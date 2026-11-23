package com.example.hibernatelab.g6_one_to_one.v2_fk_on_card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V2 (g6_v2_fk_on_card)
 * BASELINE : V1 (g6_v1_fk_on_customer)
 * KHÁC BIỆT: chuyển FK sang bảng loyalty_card; Customer không còn property nào về thẻ
 * HẬU QUẢ  : cột loyalty_card.customer_id; customer chưa có thẻ thì không có dòng card nào (G6-S01, S02)
 */
@Entity(name = "G6V2_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v2_fk_on_card", name = "customer")
public class V2Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v2_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v2_customer_seq", schema = "g6_v2_fk_on_card", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected V2Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public V2Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
