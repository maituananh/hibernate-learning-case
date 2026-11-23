package com.example.hibernatelab.g6_one_to_one;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.TRANSIENT;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.persistence.EntityExistsException;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.bootfail.g6s13.MapsIdWithGeneratedValueProfile;
import com.example.hibernatelab.g6_one_to_one.v5_mapsid_uni.V5Customer;
import com.example.hibernatelab.g6_one_to_one.v5_mapsid_uni.V5CustomerProfile;
import com.example.hibernatelab.g6_one_to_one.v5_mapsid_uni.V5CustomerProfileRepository;
import com.example.hibernatelab.g6_one_to_one.v6_mapsid_bidi.V6Customer;
import com.example.hibernatelab.g6_one_to_one.v6_mapsid_bidi.V6CustomerProfile;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.MappingBootstrap;
import com.example.hibernatelab.support.SchemaInspector.ForeignKey;

/**
 * Nhóm G6, phần {@code @MapsId} (chia sẻ khoá chính): {@code CustomerProfile} không có id riêng,
 * id của nó CHÍNH LÀ id của customer. S06 (DDL, persist, Spring Data), S07 (profile thứ hai),
 * S08 (V5 vs V6 và vấn đề LAZY của S03), S13 ({@code [BOOT-FAIL]} thêm {@code @GeneratedValue}).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G6MapsIdTest extends AbstractScenarioTest {

	private static final String V5 = "g6_v5_mapsid_uni";
	private static final String V6 = "g6_v6_mapsid_bidi";

	@Autowired
	private V5CustomerProfileRepository profileRepository;

	@Autowired
	private DataSource dataSource;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { V5, V6 };
	}

	/**
	 * G6-S06a — V5: DDL của {@code @MapsId}
	 *
	 * <p>Mapping: {@code CustomerProfile: @Id Long id} (không {@code @GeneratedValue}) +
	 * {@code @OneToOne(fetch = LAZY) @MapsId @JoinColumn(name = "customer_id") Customer customer}.
	 * <p><b>Câu hỏi</b>: bảng customer_profile trông thế nào khi dùng {@code @MapsId}?
	 * <p><b>Kết luận</b>: bảng chỉ có MỘT cột khoá là {@code customer_id}, vừa là PRIMARY KEY vừa là FOREIGN KEY → customer.
	 * Không có cột {@code id} riêng và không có sequence riêng cho profile (schema chỉ có sequence của customer).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S06a | V5: customer_id vừa là PK vừa là FK, không có sequence riêng cho profile")
	void s06a_v5ProfilePrimaryKeyIsTheForeignKey() {
		// ── THEN: DDL ──
		schema.printDdl(V5);
		assertThat(schema.primaryKeyOf(V5, "customer_profile"))
			.as("PK của profile chính là cột customer_id (id được 'map' từ quan hệ)").containsExactly("customer_id");
		ForeignKey fk = schema.foreignKeyOnColumn(V5, "customer_profile", "customer_id");
		assertThat(fk.targetTable()).as("cột PK đó đồng thời là FK trỏ sang customer").isEqualTo(V5 + ".customer");
		assertThat(schema.hasColumn(V5, "customer_profile", "id")).as("không có cột id riêng").isFalse();
		assertThat(schema.sequencesOf(V5))
			.as("chỉ customer có sequence; profile không cần vì id lấy từ customer")
			.containsExactly("customer_seq");
	}

	/**
	 * G6-S06b — V5: persist profile rồi {@code find} theo id của customer
	 *
	 * <p>Mapping: V5.
	 * <p><b>Câu hỏi</b>: id của profile do đâu mà có? tìm profile bằng id nào?
	 * <p><b>Kết luận</b>: id được SUY RA từ customer lúc {@code persist} (null trước đó, bằng id customer sau đó).
	 * {@code find(CustomerProfile.class, customerId)} chỉ cần 1 SELECT trên customer_profile, không join customer
	 * (customer là LAZY).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S06b | V5: persist profile → id == customer.id; find theo id customer, không join")
	void s06b_v5PersistDerivesIdFromCustomer() {
		// ── GIVEN: customer An đã có trong DB ──
		V5Customer customerAn = new V5Customer("An");
		Long customerId = inTxReturning(() -> {
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN: persist profile của An ──
		V5CustomerProfile profileOfAn = inTxReturning(() -> {
			V5Customer managedCustomer = em.find(V5Customer.class, customerId);
			V5CustomerProfile profile = new V5CustomerProfile(managedCustomer, "Thích cà phê");
			assertThat(profile.getId()).as("trước persist: id còn null").isNull();
			assertState(em, profile, TRANSIENT); // [LIFECYCLE] TRANSIENT: chưa có id, chưa quản lý
			em.persist(profile);
			assertState(em, profile, MANAGED); // [LIFECYCLE] TRANSIENT → MANAGED, id được lấy từ customer
			assertThat(profile.getId()).as("@MapsId: id của profile = id của customer").isEqualTo(customerId);
			return profile;
		});
		assertState(em, profileOfAn, DETACHED); // [TX END]

		// ── THEN: DB thật ──
		db.print(V5 + ".customer_profile");
		assertThat(db.rowsOf(V5 + ".customer_profile")).as("có đúng 1 profile").hasSize(1);
		assertThat(db.rowsOf(V5 + ".customer_profile").get(0).get("customer_id"))
			.as("cột PK/FK customer_id mang id của An").isEqualTo(customerId);

		// ── WHEN: tìm profile theo id của customer ──
		sql.reset();
		inTx(() -> {
			V5CustomerProfile found = em.find(V5CustomerProfile.class, customerId);
			assertThat(found.getBio()).as("tìm thấy đúng profile").isEqualTo("Thích cà phê");
			assertThat(Hibernate.isInitialized(found.getCustomer())).as("customer là LAZY nên chưa tải").isFalse();
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer_profile", 1);
		sql.assertSelects("customer", 0);
		sql.assertTotal(1);
	}

	/**
	 * G6-S06c — V5 🔬: Spring Data {@code save(profile)} khi id còn null
	 *
	 * <p>Mapping: V5; {@code V5CustomerProfileRepository extends JpaRepository}.
	 * <p><b>Câu hỏi</b>: {@code save()} chọn persist hay merge khi {@code id == null} nhưng id sắp được suy ra từ customer?
	 * <p><b>Kết luận</b>: đi theo PERSIST: INSERT thẳng, không có SELECT customer_profile nào trước đó (merge mới có).
	 * Spring Data coi entity là "mới" vì {@code id == null}; id của profile được điền sau, lúc persist, từ customer.
	 * <p>Xác minh: Hibernate 7.4.15.Final, Spring Data JPA đi kèm Spring Boot
	 */
	@Test
	@DisplayName("G6-S06c | V5 🔬: repository.save(profile) với id null đi theo persist (không SELECT trước khi INSERT)")
	void s06c_v5SpringDataSaveWithNullIdGoesThroughPersist() {
		// ── GIVEN ──
		V5Customer customerAn = new V5Customer("An");
		Long customerId = inTxReturning(() -> {
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			V5Customer managedCustomer = em.find(V5Customer.class, customerId);
			profileRepository.save(new V5CustomerProfile(managedCustomer, "Thích cà phê"));
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("customer_profile", 1);
		sql.assertSelects("customer_profile", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(V5 + ".customer_profile")).as("profile đã được lưu").isEqualTo(1);
	}

	/**
	 * G6-S07a — V5 🔬: profile thứ hai cho cùng customer, TRONG CÙNG persistence context
	 *
	 * <p>Mapping: V5.
	 * <p><b>Câu hỏi</b>: persist hai profile cho một customer trong một transaction thì lỗi ở đâu?
	 * <p><b>Kết luận</b>: {@code EntityExistsException} ngay ở lần {@code persist} thứ hai. Cả hai profile có CÙNG id (= id customer)
	 * nên persistence context (một entity một id) từ chối trước khi đụng tới DB; rollback, DB không có profile nào.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S07a | V5 🔬: hai profile cho một customer trong cùng persistence context bị Hibernate từ chối")
	void s07a_v5SecondProfileInSamePersistenceContextIsRejected() {
		// ── GIVEN ──
		V5Customer customerAn = new V5Customer("An");
		Long customerId = inTxReturning(() -> {
			em.persist(customerAn);
			return customerAn.getId();
		});

		// ── WHEN: persist hai profile cho cùng customer trong một transaction ──
		Throwable failure = catchThrowable(() -> inTx(() -> {
			V5Customer managedCustomer = em.find(V5Customer.class, customerId);
			em.persist(new V5CustomerProfile(managedCustomer, "Profile 1"));
			em.persist(new V5CustomerProfile(managedCustomer, "Profile 2")); // cùng id → xung đột trong persistence context
		}));

		// ── THEN ──
		System.out.println("[S07a] " + (failure == null ? "(không lỗi)" : failure.getClass().getName() + ": " + failure.getMessage()));
		assertThat(failure).as("hai profile cùng id không thể cùng MANAGED trong một persistence context")
			.isInstanceOf(EntityExistsException.class);
		assertThat(db.countOf(V5 + ".customer_profile")).as("rollback: không có profile nào").isZero();
	}

	/**
	 * G6-S07b — V5 🔬: profile thứ hai cho cùng customer, KHÁC persistence context
	 *
	 * <p>Mapping: V5.
	 * <p><b>Câu hỏi</b>: profile thứ nhất đã commit, giờ persist profile thứ hai ở transaction khác thì sao?
	 * <p><b>Kết luận</b>: persistence context mới không biết gì về profile cũ nên cho qua, rồi chính DB từ chối:
	 * INSERT trùng PRIMARY KEY {@code customer_id} → {@code DataIntegrityViolationException}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S07b | V5 🔬: profile thứ hai ở transaction khác vi phạm PK customer_id tại DB")
	void s07b_v5SecondProfileInAnotherPersistenceContextViolatesPrimaryKey() {
		// ── GIVEN: An đã có profile thứ nhất ──
		V5Customer customerAn = new V5Customer("An");
		Long customerId = inTxReturning(() -> {
			em.persist(customerAn);
			return customerAn.getId();
		});
		inTx(() -> em.persist(new V5CustomerProfile(em.find(V5Customer.class, customerId), "Profile 1")));

		// ── WHEN: ở một transaction khác, persist profile thứ hai ──
		sql.reset();
		Throwable failure = catchThrowable(() -> inTx(() -> {
			V5Customer managedCustomer = em.find(V5Customer.class, customerId);
			em.persist(new V5CustomerProfile(managedCustomer, "Profile 2"));
		}));

		// ── THEN ──
		System.out.println("[S07b] " + (failure == null ? "(không lỗi)" : failure.getClass().getName() + ": " + failure.getMessage()));
		assertThat(failure).as("DB từ chối INSERT trùng PRIMARY KEY customer_id")
			.isInstanceOf(DataIntegrityViolationException.class);
		sql.print();
		assertThat(db.rowsOf(V5 + ".customer_profile")).as("rollback: chỉ còn profile thứ nhất").hasSize(1);
		assertThat(db.rowsOf(V5 + ".customer_profile").get(0).get("bio")).as("profile còn lại là Profile 1").isEqualTo("Profile 1");
	}

	/**
	 * G6-S08a — V5 🔬: không map ngược thì {@code find(customer)} không tải profile
	 *
	 * <p>Mapping: V5, Customer KHÔNG có property về profile.
	 * <p><b>Câu hỏi</b>: dùng {@code @MapsId} một chiều có tránh được SELECT thừa của G6-S03 không?
	 * <p><b>Kết luận</b>: có. {@code find(customer)} chỉ 1 SELECT customer. Muốn lấy profile thì {@code find(CustomerProfile.class, customerId)}
	 * (id chung nên không cần join hay truy vấn ngược).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S08a | V5 🔬: find(customer) chỉ 1 SELECT; lấy profile bằng find(Profile, customerId)")
	void s08a_v5FindCustomerLoadsNoProfile() {
		// ── GIVEN ──
		V5Customer customerAn = new V5Customer("An");
		Long customerId = inTxReturning(() -> {
			em.persist(customerAn);
			return customerAn.getId();
		});
		inTx(() -> em.persist(new V5CustomerProfile(em.find(V5Customer.class, customerId), "Thích cà phê")));

		// ── WHEN: find customer ──
		sql.reset();
		inTx(() -> em.find(V5Customer.class, customerId));

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 1);
		sql.assertSelects("customer_profile", 0);
		sql.assertTotal(1);

		// ── WHEN: cần profile thì tìm theo CÙNG id ──
		sql.reset();
		inTx(() -> {
			V5CustomerProfile profile = em.find(V5CustomerProfile.class, customerId);
			assertThat(profile.getBio()).as("profile tìm bằng id của customer").isEqualTo("Thích cà phê");
		});

		// ── THEN: SQL ──
		sql.assertSelects("customer_profile", 1);
		sql.assertTotal(1);
	}

	/**
	 * G6-S08b — V6 🔬: map ngược {@code mappedBy} có còn SELECT thừa không?
	 *
	 * <p>Mapping: V6 = V5 + {@code Customer.profile @OneToOne(mappedBy = "customer", fetch = LAZY)}.
	 * <p><b>Câu hỏi</b>: khi profile dùng {@code @MapsId}, phía inverse của Customer có LAZY được không?
	 * <p><b>Kết luận</b>: KHÔNG tránh được: V6 vẫn SELECT customer_profile ngay lúc {@code find(customer)} (LAZY bị bỏ qua),
	 * nguyên nhân gốc như S03: customer không có cột FK nên không biết có profile hay không nếu không hỏi bảng bên kia.
	 * Bài học: ưu tiên V5 (một chiều) và {@code find(CustomerProfile.class, customerId)}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S08b | V6 🔬: map ngược mappedBy vẫn SELECT customer_profile ngay lúc find(customer)")
	void s08b_v6InverseSideStillLoadsProfileEagerly() {
		// ── GIVEN ──
		V6Customer customerAn = new V6Customer("An");
		Long customerId = inTxReturning(() -> {
			em.persist(customerAn);
			return customerAn.getId();
		});
		inTx(() -> em.persist(new V6CustomerProfile(em.find(V6Customer.class, customerId), "Thích cà phê")));

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			V6Customer managedCustomer = em.find(V6Customer.class, customerId);

			// ── THEN ──
			sql.print();
			sql.assertSelects("customer", 1);
			sql.assertSelects("customer_profile", 1);
			assertThat(Hibernate.isInitialized(managedCustomer.getProfile()))
				.as("inverse side của @OneToOne: LAZY vẫn bị bỏ qua dù profile dùng @MapsId").isTrue();
		});
	}

	/**
	 * G6-S13a — [BOOT-FAIL] 🔬 {@code @MapsId} kết hợp {@code @GeneratedValue} trên cùng id: lúc khởi động
	 *
	 * <p>Mapping: entity sai {@code bootfail/g6s13/MapsIdWithGeneratedValueProfile}.
	 * <p><b>Câu hỏi</b>: id vừa "lấy từ customer" vừa "do DB sinh" thì Hibernate khởi động được không?
	 * <p><b>Kết luận</b>: KHÔNG có lỗi lúc khởi động (giả thuyết "[BOOT-FAIL]" của tên tình huống đã SAI): Hibernate chấp nhận
	 * mapping mâu thuẫn. Chạy thật ra sao thì xem S13b.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S13a | [BOOT-FAIL] 🔬 @MapsId + @GeneratedValue trên cùng id: Hibernate vẫn khởi động được")
	void s13a_mapsIdTogetherWithGeneratedValueStillBoots() {
		// ── WHEN: dựng SessionFactory riêng chỉ gồm entity sai (+ V5Customer mà nó trỏ tới) ──
		Throwable failure = MappingBootstrap.tryBuild(MapsIdWithGeneratedValueProfile.class, V5Customer.class);

		// ── THEN ──
		System.out.println("[BOOT-FAIL] " + (failure == null ? "(không lỗi)" : failure.getClass().getName() + ": " + failure.getMessage()));
		assertThat(failure).as("Hibernate 7 không phát hiện mâu thuẫn ở bước dựng metadata nên không ném lỗi").isNull();
	}

	/**
	 * G6-S13b — 🔬 chạy thật với mapping của S13a
	 *
	 * <p>Mapping: như S13a, bảng {@code customer_profile_generated(customer_id PK, bio)} tạo tay (entity này không nằm trong
	 * entity scan nên Spring không sinh DDL cho nó); SessionFactory dựng riêng, dùng chung DataSource của test.
	 * <p><b>Câu hỏi</b>: {@code @GeneratedValue} bị bỏ qua hay id bị sinh sai?
	 * <p><b>Kết luận</b>: chạy được, không lỗi. {@code @MapsId} thắng: dòng được INSERT với {@code customer_id} = id của customer,
	 * {@code @GeneratedValue} không sinh ra giá trị nào khác. Mâu thuẫn bị bỏ qua trong im lặng, nên đây là bẫy: đọc code
	 * tưởng id tự sinh, thực tế id do customer quyết định.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S13b | 🔬 @MapsId + @GeneratedValue: persist thật cho thấy id lấy từ đâu")
	void s13b_mapsIdTogetherWithGeneratedValueAtRuntime() {
		// ── GIVEN: bảng tạo tay + một customer (insert bằng JDBC, không qua entity) ──
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("drop table if exists " + V5 + ".customer_profile_generated");
		jdbc.execute("create table " + V5 + ".customer_profile_generated (customer_id bigint primary key, bio varchar(255))");
		jdbc.update("insert into " + V5 + ".customer (id, name) values (777, 'An')");
		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
			.applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
			.applySetting("jakarta.persistence.nonJtaDataSource", dataSource)
			.applySetting("jakarta.persistence.schema-generation.database.action", "none")
			.build();
		Throwable failure = null;
		try (SessionFactory factory = new MetadataSources(registry)
			.addAnnotatedClass(MapsIdWithGeneratedValueProfile.class)
			.addAnnotatedClass(V5Customer.class)
			.buildMetadata().buildSessionFactory()) {
			// ── WHEN ──
			failure = catchThrowable(() -> factory.inTransaction(session -> {
				V5Customer customerAn = session.find(V5Customer.class, 777L);
				session.persist(new MapsIdWithGeneratedValueProfile(customerAn, "Thích cà phê"));
			}));
		} finally {
			StandardServiceRegistryBuilder.destroy(registry);
		}

		// ── THEN ──
		System.out.println("[S13b] " + (failure == null ? "(không lỗi)" : failure.getClass().getName() + ": " + failure.getMessage()));
		List<Map<String, Object>> rows = jdbc.queryForList("select * from " + V5 + ".customer_profile_generated");
		System.out.println("[S13b] " + rows);
		jdbc.execute("drop table if exists " + V5 + ".customer_profile_generated");
		assertThat(failure).as("Hibernate không phàn nàn: @MapsId và @GeneratedValue cùng tồn tại").isNull();
		assertThat(rows).as("có đúng 1 dòng profile").hasSize(1);
		assertThat(rows.get(0).get("customer_id")).as("id lấy từ customer (777), @GeneratedValue không có tác dụng").isEqualTo(777L);
	}
}
