package com.example.hibernatelab.g12_advanced_model.v1_converter;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển {@link Tags} ⇄ chuỗi CSV ("a,b,c") trong cột varchar. Không tự áp dụng (autoApply mặc định false). */
@Converter
public class TagsConverter implements AttributeConverter<Tags, String> {

	@Override
	public String convertToDatabaseColumn(Tags tags) {
		return tags == null ? null : String.join(",", tags.values());
	}

	@Override
	public Tags convertToEntityAttribute(String csv) {
		return csv == null ? null : new Tags(List.copyOf(Arrays.asList(csv.split(","))));
	}
}
