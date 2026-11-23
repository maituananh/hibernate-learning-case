package com.example.hibernatelab.bootfail.g1s17;

import java.util.ArrayList;
import java.util.List;

import com.example.hibernatelab.g1_owning_side.v4_bidi_mappedby.PurchaseOrder;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * [BOOT-FAIL] G1-S17: entity CỐ Ý SAI, nằm ở package {@code bootfail} nên entity scan không thấy.
 *
 * <p>Sai ở đâu: {@code mappedBy = "khachHang"} trỏ tới một property KHÔNG tồn tại trong {@link PurchaseOrder}
 * (property thật tên là {@code customer}). Gõ sai tên field trong mappedBy là lỗi rất hay gặp.
 */
@Entity(name = "BootFail_BrokenMappedByCustomer")
@Table(schema = "g1_v4_bidi_mappedby", name = "customer_broken")
public class BrokenMappedByCustomer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToMany(mappedBy = "khachHang")
	private List<PurchaseOrder> orders = new ArrayList<>();
}
