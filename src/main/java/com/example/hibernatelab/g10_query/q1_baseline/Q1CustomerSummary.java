package com.example.hibernatelab.g10_query.q1_baseline;

/**
 * DTO (record) cho G10-S10. Không phải entity nên KHÔNG có vòng đời: không MANAGED, không dirty checking,
 * không nằm trong persistence context.
 */
public record Q1CustomerSummary(String customerName, long orderCount) {
}
