package com.example.hibernatelab.g0_lifecycle;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service tối thiểu cho hai tình huống cần "ranh giới transaction" do Spring quản lý:
 * G0-S12 (readOnly bỏ qua dirty checking) và G0-S14 (self-invocation làm mất {@code @Transactional}).
 *
 * <p>{@code @Transactional} hoạt động nhờ PROXY: Spring bọc bean này, mỗi lệnh gọi TỪ BÊN NGOÀI đi qua proxy
 * (proxy mở transaction rồi mới gọi method thật). Gọi {@code this.method()} từ bên trong thì bỏ qua proxy.
 */
@Service
public class CustomerService {

	private final CustomerRepository customerRepository;

	public CustomerService(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	/**
	 * G0-S12: tải customer rồi sửa name trong transaction {@code readOnly = true}.
	 * Spring/Hibernate đặt session ở chế độ chỉ đọc (không dirty checking, flush tắt) nên KHÔNG có UPDATE.
	 */
	@Transactional(readOnly = true)
	public void renameInReadOnlyTransaction(Long customerId, String newName) {
		Customer customer = customerRepository.findById(customerId).orElseThrow();
		customer.setName(newName); // customer là MANAGED nhưng session chỉ đọc → thay đổi này không được flush
	}

	/** Transaction đọc-ghi bình thường: sửa name trên entity MANAGED, dirty checking sinh UPDATE lúc commit. */
	@Transactional
	public void renameInTransaction(Long customerId, String newName) {
		Customer customer = customerRepository.findById(customerId).orElseThrow();
		customer.setName(newName);
	}

	/**
	 * G0-S14: method này KHÔNG có {@code @Transactional} và gọi {@code this.renameInTransaction(...)}.
	 * Vì gọi qua {@code this} (không qua proxy), annotation trên {@code renameInTransaction} bị bỏ qua:
	 * không có transaction bao ngoài, nên {@code findById} tự mở một transaction ngắn riêng rồi đóng,
	 * customer trở thành DETACHED trước khi {@code setName} chạy.
	 */
	public void renameViaSelfInvocation(Long customerId, String newName) {
		this.renameInTransaction(customerId, newName); // self-invocation: KHÔNG qua proxy
	}
}
