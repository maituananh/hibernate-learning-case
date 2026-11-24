package com.example.hibernatelab.g13_enhanced;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi động Spring cho test của subproject lab-enhanced (đặt ở src/test để src/main chỉ chứa entity).
 *
 * <p>{@code @SpringBootTest} tự tìm lớp {@code @SpringBootConfiguration} bằng cách đi ngược lên các package cha của test,
 * nên lớp này PHẢI nằm ở package cha của mọi test (g13_enhanced). Project gốc cũng có một lớp tương tự
 * ({@code HibernateLabApplication}) và nó nằm trên classpath, nhưng ở package cao hơn nên không bị chọn nhầm.
 */
@SpringBootApplication
public class EnhancedTestApplication {
}
