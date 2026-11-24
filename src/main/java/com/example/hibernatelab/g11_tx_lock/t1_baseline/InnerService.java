package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Service "B" của G11-S02..S04. Phải là bean RIÊNG (không cùng class với service "A") vì {@code @Transactional}
 * chỉ có tác dụng khi lời gọi đi qua proxy của Spring (nối với G0-S14: self-invocation bị bỏ qua).
 */
@Service
public class InnerService {

	@PersistenceContext
	private EntityManager em;

	private final T1AuditLogRepository auditLogs;

	public InnerService(T1AuditLogRepository auditLogs) {
		this.auditLogs = auditLogs;
	}

	/** REQUIRED (mặc định): tham gia transaction của caller; ném RuntimeException thì đánh dấu transaction chung là rollback-only. */
	@Transactional(propagation = Propagation.REQUIRED)
	public void writeAuditThenFail(String message) {
		auditLogs.save(new T1AuditLog(message));
		throw new IllegalStateException("inner lỗi (RuntimeException)");
	}

	/** REQUIRES_NEW: treo transaction của caller, mở transaction + persistence context MỚI trên connection khác, commit riêng. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void writeAuditInNewTransaction(String message) {
		auditLogs.save(new T1AuditLog(message));
	}

	/**
	 * REQUIRES_NEW: nhận entity do caller đã tải và cho biết trong persistence context MỚI này có phải cùng instance không.
	 * Trả {@code "sameInstance|contains"} để test assert cả hai.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public String describeProductInNewTransaction(T1Product productFromOuter) {
		T1Product loadedHere = em.find(T1Product.class, productFromOuter.getId());
		boolean same = loadedHere == productFromOuter;
		boolean contains = em.contains(productFromOuter);
		return same + "|" + contains;
	}

	/** G11-S04: sửa tồn kho rồi ném CHECKED exception, KHÔNG khai báo rollbackFor → Spring vẫn COMMIT. */
	@Transactional
	public void decreaseStockThenThrowChecked(Long productId) throws Exception {
		T1Product product = em.find(T1Product.class, productId);
		product.decreaseStock(1);
		throw new Exception("checked exception");
	}

	/** G11-S04: giống trên nhưng {@code rollbackFor = Exception.class} → Spring ROLLBACK. */
	@Transactional(rollbackFor = Exception.class)
	public void decreaseStockThenThrowCheckedWithRollbackFor(Long productId) throws Exception {
		T1Product product = em.find(T1Product.class, productId);
		product.decreaseStock(1);
		throw new Exception("checked exception");
	}
}
