package com.example.hibernatelab.bootfail.selftest;

import java.util.ArrayList;
import java.util.List;

import com.example.hibernatelab.support.selftest.SelfTestItem;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

/**
 * Entity CỐ Ý SAI, dùng để chứng minh {@code MappingBootstrap} bắt được lỗi khởi động.
 * Nằm trong package {@code bootfail} nên KHÔNG được entity scan thấy (xem ScenarioTestConfiguration).
 *
 * <p>Sai ở đâu: {@code mappedBy = "khongTonTai"} trỏ tới thuộc tính không có trong {@link SelfTestItem}.
 */
@Entity(name = "BootFail_BrokenMappedBy")
public class BrokenMappedByEntity {

	@Id
	private Long id;

	@OneToMany(mappedBy = "khongTonTai")
	private List<SelfTestItem> items = new ArrayList<>();
}
