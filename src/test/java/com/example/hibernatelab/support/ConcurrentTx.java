package com.example.hibernatelab.support;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.awaitility.Awaitility;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hạ tầng test SONG SONG cho G11 (quyết định E6). Chạy một transaction ở THREAD NỀN, trên connection riêng,
 * để test dựng được tình huống "hai transaction cùng chạm một dòng" một cách TẤT ĐỊNH.
 *
 * <p>Quy tắc để test không chập chờn:
 * <ul>
 *   <li><b>Không dùng {@code Thread.sleep}</b>. Thứ tự các bước do {@link Gate} (cổng) quyết định: thread này
 *       {@code await()} đến khi thread kia {@code open()}.</li>
 *   <li><b>Mọi chỗ chờ đều có timeout</b> ({@link #TIMEOUT}); quá hạn thì test ĐỎ (AssertionError) chứ không treo vô hạn.</li>
 *   <li>Muốn biết một transaction đang "bị chặn bởi khoá" thì hỏi chính Postgres ({@code pg_stat_activity}) qua
 *       {@link #awaitSomeoneWaitingForLock()}, không đoán bằng thời gian.</li>
 * </ul>
 *
 * <p>Lưu ý: test song song KHÔNG được gắn {@code @Transactional} (cũng như mọi test của lab), vì mỗi thread cần
 * transaction và connection riêng.
 */
public class ConcurrentTx {

	/** Hạn chót cho mọi lần chờ. Đủ rộng cho CI chậm, đủ hẹp để lỗi hiện ra nhanh. */
	public static final Duration TIMEOUT = Duration.ofSeconds(15);

	private final PlatformTransactionManager transactionManager;
	private final JdbcTemplate jdbc;

	// Thread daemon + có tên để đọc log; cached pool vì mỗi test chỉ cần vài thread.
	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "g11-concurrent-tx");
		thread.setDaemon(true);
		return thread;
	});

	public ConcurrentTx(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
		this.transactionManager = transactionManager;
		this.jdbc = jdbc;
	}

	/** Cổng đồng bộ một lần: {@code open()} mở cổng, {@code await()} chờ (có timeout) cho đến khi cổng mở. */
	public static final class Gate {

		private final String name;
		private final CountDownLatch latch = new CountDownLatch(1);

		public Gate(String name) {
			this.name = name;
		}

		public void open() {
			latch.countDown();
		}

		public void await() {
			try {
				if (!latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
					throw new AssertionError("Cổng \"" + name + "\" không mở sau " + TIMEOUT + " (test sẽ treo nếu không có timeout)");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Bị ngắt khi chờ cổng \"" + name + "\"", e);
			}
		}
	}

	/** Kết quả của một transaction đang chạy nền. */
	public static final class Running<T> {

		private final CompletableFuture<T> future;

		private Running(CompletableFuture<T> future) {
			this.future = future;
		}

		/** Chờ transaction kết thúc (có timeout) và trả kết quả; nếu nó ném exception thì ném lại ĐÚNG exception đó. */
		public T join() {
			try {
				return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (cause instanceof RuntimeException runtime) {
					throw runtime;
				}
				if (cause instanceof Error error) {
					throw error;
				}
				throw new IllegalStateException(cause);
			} catch (TimeoutException e) {
				throw new AssertionError("Transaction nền không kết thúc sau " + TIMEOUT, e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Bị ngắt khi chờ transaction nền", e);
			}
		}

		/** Chờ transaction kết thúc và trả exception nó đã ném, hoặc {@code null} nếu nó chạy thành công. */
		public Throwable joinAndGetFailure() {
			try {
				join();
				return null;
			} catch (RuntimeException | Error failure) {
				return failure;
			}
		}
	}

	/** Chạy {@code work} trong một transaction MỚI ở thread nền; trả về ngay, dùng {@link Running#join()} để lấy kết quả. */
	public <T> Running<T> start(Supplier<T> work) {
		CompletableFuture<T> future = CompletableFuture.supplyAsync(
			() -> new TransactionTemplate(transactionManager).execute(status -> work.get()), executor);
		return new Running<>(future);
	}

	/** Như {@link #start(Supplier)} cho việc không trả giá trị. */
	public Running<Void> start(Runnable work) {
		return start(() -> {
			work.run();
			return null;
		});
	}

	/**
	 * Chờ (có timeout, thăm dò bằng Awaitility chứ không phải {@code Thread.sleep} rải rác) đến khi Postgres báo
	 * có ít nhất một session đang CHỜ KHOÁ ({@code wait_event_type = 'Lock'}). Dùng để chắc chắn transaction thứ hai
	 * thực sự đã bị chặn trước khi transaction thứ nhất nhả khoá.
	 */
	public void awaitSomeoneWaitingForLock() {
		Awaitility.await("có session chờ khoá trong Postgres")
			.atMost(TIMEOUT)
			.until(() -> {
				Integer waiting = jdbc.queryForObject(
					"select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()",
					Integer.class);
				return waiting != null && waiting > 0;
			});
	}
}
