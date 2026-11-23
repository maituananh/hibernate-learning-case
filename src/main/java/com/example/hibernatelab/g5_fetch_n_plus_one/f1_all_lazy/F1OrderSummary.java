package com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy;

/**
 * DTO (record) cho G5-S15. Không phải entity nên KHÔNG có vòng đời: không MANAGED, không dirty checking,
 * không nằm trong persistence context.
 */
public record F1OrderSummary(String orderNo, String customerName) {
}
