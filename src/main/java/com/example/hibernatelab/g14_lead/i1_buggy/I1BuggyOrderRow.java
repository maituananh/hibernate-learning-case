package com.example.hibernatelab.g14_lead.i1_buggy;

/** Một dòng của trang danh sách đơn hàng: đúng 3 thứ màn hình cần, không phải entity. */
public record I1BuggyOrderRow(String orderNo, String customerName, long itemCount) {
}
