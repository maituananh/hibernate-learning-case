package com.example.hibernatelab.bootfail.g14r2;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * [BAD-MAPPING] G14-R2: entity CỐ Ý vi phạm quy tắc mapping của team, nằm ở package {@code bootfail} nên entity scan không thấy.
 * Khác {@code [BOOT-FAIL]}: mapping này VẪN khởi động được, chỉ là sai quy tắc; lint {@code MappingLint} phải bắt được.
 */
@Entity(name = "G14R2_BadLintOrder")
@Table(name = "g14r2_order")
public class BadLintOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // ← vi phạm IDENTITY-ID
	private Long id;

	// ← vi phạm EAGER-TO-ONE (thiếu fetch = LAZY nên mặc định là EAGER) và CASCADE-REMOVE (xoá đơn kéo theo xoá khách)
	@ManyToOne(cascade = CascadeType.REMOVE)
	private BadLintCustomer customer;

	// ← vi phạm EAGER-TO-ONE: @OneToOne cũng mặc định EAGER
	@OneToOne
	private BadLintProfile profile;

	// ← vi phạm EAGER-COLLECTION
	@OneToMany(mappedBy = "order", fetch = FetchType.EAGER)
	private List<BadLintItem> items = new ArrayList<>();

	// ← vi phạm EAGER-COLLECTION và CASCADE-REMOVE trên @ManyToMany (xoá đơn xoá luôn tag dùng chung)
	@ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	private Set<BadLintTag> tags;

	protected BadLintOrder() {
		// JPA bắt buộc có constructor không tham số
	}
}
