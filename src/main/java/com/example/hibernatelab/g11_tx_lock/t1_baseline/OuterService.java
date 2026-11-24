package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Service "A" của G11-S02 và S03: mở transaction rồi gọi {@link InnerService} (service "B"). */
@Service
public class OuterService {

	@PersistenceContext
	private EntityManager em;

	private final InnerService inner;
	private final T1AuditLogRepository auditLogs;

	public OuterService(InnerService inner, T1AuditLogRepository auditLogs) {
		this.inner = inner;
		this.auditLogs = auditLogs;
	}

	/**
	 * G11-S02: A ghi "outer", gọi B (REQUIRED) ném RuntimeException, A BẮT lại và đi tiếp.
	 * Tưởng A vẫn commit được; thực tế B đã đánh dấu transaction chung rollback-only → lúc commit Spring ném
	 * UnexpectedRollbackException.
	 */
	@Transactional
	public void callInnerAndSwallowFailure() {
		auditLogs.save(new T1AuditLog("outer"));
		try {
			inner.writeAuditThenFail("inner-fail");
		} catch (IllegalStateException swallowed) {
			// A nuốt lỗi: không đủ để cứu transaction, vì cờ rollback-only nằm ở transaction CHUNG.
		}
	}

	/**
	 * G11-S03: A ghi "outer", B (REQUIRES_NEW) ghi "inner" và commit riêng, rồi A ném lỗi → A rollback.
	 * Kết quả: "inner" còn, "outer" mất.
	 */
	@Transactional
	public void writeThenFailAfterRequiresNew() {
		auditLogs.save(new T1AuditLog("outer"));
		inner.writeAuditInNewTransaction("inner");
		throw new IllegalStateException("outer lỗi sau khi inner đã commit");
	}

	/** G11-S03: A tải product (MANAGED trong PC của A) rồi hỏi B (REQUIRES_NEW) xem có cùng instance không. */
	@Transactional
	public String loadProductThenAskInnerRequiresNew(Long productId) {
		T1Product productInOuter = em.find(T1Product.class, productId);
		return inner.describeProductInNewTransaction(productInOuter);
	}
}
