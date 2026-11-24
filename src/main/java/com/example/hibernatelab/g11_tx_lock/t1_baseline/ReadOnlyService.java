package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * G11-S01: {@code @Transactional(readOnly = true)} tắt những gì? Mỗi method làm đúng một việc "ghi" bên trong
 * transaction chỉ đọc để test quan sát kết quả.
 */
@Service
public class ReadOnlyService {

	@PersistenceContext
	private EntityManager em;

	private final T1AuditLogRepository auditLogs;

	public ReadOnlyService(T1AuditLogRepository auditLogs) {
		this.auditLogs = auditLogs;
	}

	/** Sửa entity MANAGED (không gọi save): với readOnly, Hibernate bỏ flush nên không có UPDATE. */
	@Transactional(readOnly = true)
	public int decreaseStockButReadOnly(Long productId) {
		T1Product product = em.find(T1Product.class, productId);
		product.decreaseStock(1);
		return product.getStock(); // trong bộ nhớ đã là giá trị mới, nhưng DB không đổi
	}

	/** {@code save(new)} trong transaction chỉ đọc: INSERT có xảy ra không? */
	@Transactional(readOnly = true)
	public void saveNewInReadOnly(String message) {
		auditLogs.save(new T1AuditLog(message));
	}

	/** Như trên nhưng ép {@code flush()} ngay: lúc này Hibernate buộc phải gửi INSERT cho Postgres. */
	@Transactional(readOnly = true)
	public void saveNewAndFlushInReadOnly(String message) {
		auditLogs.save(new T1AuditLog(message));
		em.flush();
	}

	/** Đối chứng: cùng việc sửa entity nhưng KHÔNG readOnly → có UPDATE. */
	@Transactional
	public void decreaseStockReadWrite(Long productId) {
		T1Product product = em.find(T1Product.class, productId);
		product.decreaseStock(1);
	}
}
