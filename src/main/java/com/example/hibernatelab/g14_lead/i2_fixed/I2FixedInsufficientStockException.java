package com.example.hibernatelab.g14_lead.i2_fixed;

/** Hết hàng: kho không đủ số lượng khách muốn mua. Đây là lỗi nghiệp vụ BÌNH THƯỜNG, không phải lỗi hệ thống. */
public class I2FixedInsufficientStockException extends RuntimeException {

	public I2FixedInsufficientStockException(int available, int requested) {
		super("Kho còn " + available + ", khách muốn mua " + requested);
	}
}
