package com.example.hibernatelab.g13_performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.hibernate.engine.spi.SelfDirtinessTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.hibernatelab.g6_one_to_one.v3_bidi_owner_card.V3Customer;

/**
 * Rào chắn của G13b: project gốc KHÔNG bao giờ bị bytecode enhancement.
 *
 * <p>Plugin {@code org.hibernate.orm} áp lên MỌI entity trong source set của project nó được khai. Nếu ai đó lỡ khai plugin
 * ở project gốc, entity của G0–G12 sẽ đổi hành vi (ví dụ G6-S03a: LAZY phía inverse bỗng chạy được) và nhiều test cũ sai âm thầm.
 * Test này không cần Spring hay DB: chỉ nhìn vào chính file .class đã biên dịch.
 */
class G13EnhancementGuardTest {

	/**
	 * G13-S11d — Guard: entity của G6-V3 (baseline của S11) không bị enhance
	 *
	 * <p><b>Câu hỏi</b>: làm sao bảo đảm bytecode enhancement chỉ nằm ở subproject {@code lab-enhanced}?
	 * <p><b>Kết luận</b>: {@code V3Customer} không implements {@code PersistentAttributeInterceptable} hay {@code SelfDirtinessTracker};
	 * hai interface này chỉ do plugin chèn vào. Test sẽ đỏ nếu plugin bị bật nhầm ở project gốc.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S11d | Guard: entity G6-V3 ở project gốc không bị bytecode enhancement")
	void s11d_rootProjectEntitiesAreNotEnhanced() {
		// ── THEN ──
		assertThat(PersistentAttributeInterceptable.class.isAssignableFrom(V3Customer.class))
			.as("không enhancement thì class không có interceptor; có nghĩa là G6-S03a (LAZY bị bỏ qua) vẫn đúng").isFalse();
		assertThat(SelfDirtinessTracker.class.isAssignableFrom(V3Customer.class))
			.as("không enhancement thì entity không tự theo dõi thay đổi").isFalse();
	}
}
