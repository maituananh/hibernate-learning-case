package com.example.hibernatelab.g12_advanced_model.v1_converter;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển {@link TagsWithEquals} ⇄ chuỗi CSV. */
@Converter
public class TagsWithEqualsConverter implements AttributeConverter<TagsWithEquals, String> {

	@Override
	public String convertToDatabaseColumn(TagsWithEquals tags) {
		return tags == null ? null : String.join(",", tags.values());
	}

	@Override
	public TagsWithEquals convertToEntityAttribute(String csv) {
		return csv == null ? null : new TagsWithEquals(List.copyOf(Arrays.asList(csv.split(","))));
	}
}
