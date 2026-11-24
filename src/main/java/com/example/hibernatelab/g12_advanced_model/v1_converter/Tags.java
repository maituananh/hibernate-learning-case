package com.example.hibernatelab.g12_advanced_model.v1_converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Kiểu giá trị MUTABLE (có {@code add}) và KHÔNG override {@code equals}/{@code hashCode}: so sánh theo identity.
 * Dùng cho G12-S11a để xem dirty checking xử lý kiểu tự định nghĩa thế nào.
 */
public class Tags {

	private final List<String> values = new ArrayList<>();

	public Tags(List<String> initial) {
		values.addAll(initial);
	}

	public void add(String tag) {
		values.add(tag);
	}

	public List<String> values() {
		return values;
	}
}
