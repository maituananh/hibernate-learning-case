package com.example.hibernatelab.g12_advanced_model.v1_converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Giống {@link Tags} (mutable) nhưng CÓ {@code equals}/{@code hashCode} theo giá trị. Dùng cho G12-S11b.
 */
public class TagsWithEquals {

	private final List<String> values = new ArrayList<>();

	public TagsWithEquals(List<String> initial) {
		values.addAll(initial);
	}

	public void add(String tag) {
		values.add(tag);
	}

	public List<String> values() {
		return values;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof TagsWithEquals that && Objects.equals(values, that.values);
	}

	@Override
	public int hashCode() {
		return Objects.hash(values);
	}
}
