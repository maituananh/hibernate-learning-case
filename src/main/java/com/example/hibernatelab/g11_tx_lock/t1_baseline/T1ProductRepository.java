package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

/**
 * Repository của {@link T1Product}. Là {@code @Repository} (qua Spring Data) nên exception của JPA được Spring DỊCH
 * sang {@code DataAccessException} (dùng ở G11-S08, S10). Gọi thẳng {@code em} thì KHÔNG được dịch.
 */
public interface T1ProductRepository extends JpaRepository<T1Product, Long> {

	/** {@code PESSIMISTIC_WRITE}: khoá ghi, người khác muốn khoá/sửa dòng này phải chờ (G11-S06b, S07). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from G11T1_Product p where p.id = :id")
	Optional<T1Product> findByIdForUpdate(Long id);

	/** {@code PESSIMISTIC_READ}: khoá đọc (share), nhiều người cùng giữ được nhưng chặn người ghi (G11-S07). */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select p from G11T1_Product p where p.id = :id")
	Optional<T1Product> findByIdForShare(Long id);

	/**
	 * Khoá ghi với timeout 0 = NOWAIT: dòng đang bị người khác khoá thì báo lỗi NGAY thay vì chờ (G11-S08).
	 * Hint {@code jakarta.persistence.lock.timeout} tính bằng mili-giây; 0 nghĩa là không chờ.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
	@Query("select p from G11T1_Product p where p.id = :id")
	Optional<T1Product> findByIdForUpdateNoWait(Long id);

	/** Cách sửa thứ ba của lost update: để Postgres tự trừ nguyên tử, KHÔNG đọc stock về Java (G11-S06c). */
	@Modifying
	@Query("update G11T1_Product p set p.stock = p.stock - :quantity where p.id = :id")
	int decreaseStockInDatabase(Long id, int quantity);
}
