package com.example.hibernatelab.g14_lead.i1_fixed;

/** Một dòng của trang danh sách đơn hàng: đúng 3 thứ màn hình cần, không phải entity. */
public record I1FixedOrderRow(String orderNo, String customerName, long itemCount) {
}
