package com.example.hibernatelab.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;

import jakarta.persistence.EntityManager;

/**
 * Cho biết một entity đang ở trạng thái vòng đời nào: TRANSIENT / MANAGED / DETACHED / REMOVED.
 *
 * <p><b>Đây là heuristic, chỉ để học.</b> JPA không có API hỏi "entity này đang TRANSIENT hay DETACHED".
 * Cách phân biệt ở đây:
 * <ul>
 *   <li>REMOVED: persistence context có entry của entity và entry đó ở trạng thái DELETED
 *       (dùng Hibernate SPI nội bộ, không phải API ổn định).</li>
 *   <li>MANAGED: {@code em.contains(entity)} là true.</li>
 *   <li>Còn lại (không nằm trong persistence context): id {@code null} → TRANSIENT, id khác {@code null} → DETACHED.</li>
 * </ul>
 * Hệ quả: một entity MỚI nhưng id do người dùng tự gán (ví dụ {@code SkuProduct} có {@code String sku})
 * sẽ bị báo DETACHED dù thật ra là TRANSIENT, vì không thể phân biệt bằng id.
 *
 * <p><b>Chỉ gọi bên trong {@code inTx(...)}</b>, với {@link EntityManager} đang gắn vào transaction đó.
 * Ngoài transaction, EntityManager dùng chung của Spring mở một persistence context mới và rỗng
 * cho mỗi lần gọi, nên {@code contains()} luôn false: kết quả chỉ có thể là TRANSIENT hoặc DETACHED
 * (hợp lệ để kiểm tra "sau khi transaction kết thúc entity là DETACHED").
 */
public final class LifecycleInspector {

	public enum State {
		TRANSIENT, MANAGED, DETACHED, REMOVED
	}

	private LifecycleInspector() {
	}

	/** Trạng thái hiện tại của entity (xem các lưu ý heuristic ở Javadoc lớp). */
	public static State stateOf(EntityManager em, Object entity) {
		if (em.isOpen() && isDeletedInPersistenceContext(em, entity)) {
			return State.REMOVED;
		}
		if (em.contains(entity)) {
			return State.MANAGED;
		}
		Object id = em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
		return id == null ? State.TRANSIENT : State.DETACHED;
	}

	/**
	 * Assert trạng thái và in một dòng {@code [LIFECYCLE]} ra console.
	 * Dùng ngay sau comment {@code // [LIFECYCLE] ...}: {@code assertState(em, order, MANAGED)}.
	 */
	public static void assertState(EntityManager em, Object entity, State expected) {
		State actual = stateOf(em, entity);
		System.out.println("[LIFECYCLE] " + entity.getClass().getSimpleName() + " → " + actual);
		assertThat(actual)
			.as("Trạng thái vòng đời của " + entity.getClass().getSimpleName() + " phải là " + expected)
			.isEqualTo(expected);
	}

	private static boolean isDeletedInPersistenceContext(EntityManager em, Object entity) {
		// Unwrap thẳng ra SessionImplementor (không phải Session): em dùng chung của Spring là một proxy,
		// unwrap(Session.class) trả về chính proxy đó chứ không phải session thật của Hibernate.
		// Ngoài transaction, Spring mở một session tạm rỗng nên sẽ không thấy entry nào (đúng ý ta).
		SessionImplementor session;
		try {
			session = em.unwrap(SessionImplementor.class);
		} catch (IllegalStateException | jakarta.persistence.PersistenceException exception) {
			return false;
		}
		EntityEntry entry = session.getPersistenceContextInternal().getEntry(entity);
		return entry != null && (entry.getStatus() == Status.DELETED || entry.getStatus() == Status.GONE);
	}
}
