package com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy;

/**
 * Interface projection cho G5-S15: Spring Data tạo proxy implement các getter này từ alias trong câu JPQL
 * ({@code o.orderNo as orderNo}, {@code c.name as customerName}). Cũng không phải entity.
 */
public interface F1OrderView {

	String getOrderNo();

	String getCustomerName();
}
