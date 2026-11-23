package com.example.hibernatelab.support;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.REMOVED;
import static com.example.hibernatelab.support.LifecycleInspector.State.TRANSIENT;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.support.selftest.SelfTestItem;

/**
 * Tự kiểm thử {@link LifecycleInspector}.
 *
 * <p>Câu hỏi: helper có trả đúng cả 4 trạng thái TRANSIENT / MANAGED / DETACHED / REMOVED không?
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class LifecycleInspectorSelfTest extends AbstractScenarioTest {

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { "support_selftest" };
	}

	@Test
	@DisplayName("SELFTEST | Đi qua đủ 4 trạng thái: TRANSIENT → MANAGED → REMOVED, rồi DETACHED sau khi tx kết thúc")
	void s01_walksThroughAllFourStates() {
		// ── WHEN: một item đi từ new → persist → remove, tất cả trong một transaction ──
		SelfTestItem item = inTxReturning(() -> {
			SelfTestItem newItem = new SelfTestItem("L-1", null);
			// [LIFECYCLE] newItem: TRANSIENT (mới new, chưa có id, chưa nằm trong persistence context)
			assertState(em, newItem, TRANSIENT);

			em.persist(newItem);
			// [LIFECYCLE] newItem: TRANSIENT → MANAGED (persistence context đang theo dõi)
			assertState(em, newItem, MANAGED);

			em.remove(newItem);
			// [LIFECYCLE] newItem: MANAGED → REMOVED (đánh dấu xoá, DELETE sẽ chạy lúc flush)
			assertState(em, newItem, REMOVED);
			return newItem;
		});

		// [TX END] persistence context đóng → entity đã rời khỏi persistence context, còn id → DETACHED
		assertState(em, item, DETACHED);
	}

	@Test
	@DisplayName("SELFTEST | em.detach() chuyển MANAGED → DETACHED ngay trong transaction")
	void s02_detachMakesManagedEntityDetached() {
		inTx(() -> {
			SelfTestItem item = new SelfTestItem("L-2", null);
			em.persist(item);
			// [LIFECYCLE] item: TRANSIENT → MANAGED
			assertState(em, item, MANAGED);

			// [FLUSH] phải flush TRƯỚC khi detach: gỡ entity còn INSERT chờ chạy khỏi persistence context
			// làm commit hỏng ("possible non-threadsafe access to session").
			em.flush();
			em.detach(item);
			// [LIFECYCLE] item: MANAGED → DETACHED (bị gỡ khỏi persistence context, id vẫn còn)
			assertState(em, item, DETACHED);
		});
	}

	@Test
	@DisplayName("SELFTEST | assertState sai thì ném lỗi nêu rõ trạng thái mong đợi")
	void s03_wrongExpectationFailsWithClearMessage() {
		inTx(() -> {
			SelfTestItem item = new SelfTestItem("L-3", null);
			assertThatThrownBy(() -> assertState(em, item, MANAGED))
				.as("item mới new chưa persist thì không thể MANAGED; thông báo lỗi phải nói rõ mong đợi gì")
				.hasMessageContaining("MANAGED");
		});
	}
}
