package com.example.hibernatelab.g9_integrated.s07_tx_boundary;

import java.util.List;

/**
 * DTO trả ra khỏi service (G9-S07d): chỉ chứa dữ liệu thuần (String, int, List<String>), không có proxy hay collection lazy,
 * nên caller đọc thoải mái dù transaction đã đóng.
 */
public record OrderSummary(String orderNo, int itemCount, List<String> skus) {
}
