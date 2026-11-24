package com.example.hibernatelab.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Đếm CHÍNH XÁC số lô JDBC mà Hibernate đã thực thi (G13), bằng cách nghe log DEBUG của
 * {@code org.hibernate.orm.jdbc.batch} (dòng "Executing JDBC batch (N / M) - [sql]": N dòng đã gom, tối đa M).
 *
 * <p>Vì sao cần: {@link SqlCaptor} chỉ thấy câu SQL lúc Hibernate PREPARE statement, mà một batch dùng lại cùng một
 * PreparedStatement cho nhiều lô, nên nó chỉ thấy 1 câu INSERT dù có 5 lô. {@code Statistics} cũng không có bộ đếm lô.
 * Đọc log của chính Hibernate là cách gọn nhất, không cần thêm thư viện (E4: chỉ thêm datasource-proxy nếu cách này không đủ).
 *
 * <p>Dùng: {@code try (BatchLogCapture batches = BatchLogCapture.start()) { ... batches.sizes() }}. Đóng là trả log về như cũ.
 */
public final class BatchLogCapture implements AutoCloseable {

	private static final String LOGGER_NAME = "org.hibernate.orm.jdbc.batch";

	private final Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
	private final Level previousLevel = logger.getLevel();
	private final List<String> messages = new CopyOnWriteArrayList<>();
	private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
		@Override
		protected void append(ILoggingEvent event) {
			messages.add(event.getFormattedMessage());
		}
	};

	private BatchLogCapture() {
		appender.start();
		logger.addAppender(appender);
		logger.setLevel(Level.TRACE); // các dòng "Executing JDBC batch" của Hibernate ở mức TRACE
	}

	public static BatchLogCapture start() {
		return new BatchLogCapture();
	}

	/** Mọi dòng log đã nghe được (để in ra khi quan sát). */
	public List<String> messages() {
		return new ArrayList<>(messages);
	}

	/** Kích thước từng lô đã thực thi, theo thứ tự (ví dụ [20, 20, 20, 20, 20]). */
	public List<Integer> sizes() {
		List<Integer> sizes = new ArrayList<>();
		for (String message : messages) {
			if (message.startsWith("Executing JDBC batch (")) {
				String afterParen = message.substring("Executing JDBC batch (".length());
				sizes.add(Integer.parseInt(afterParen.substring(0, afterParen.indexOf(' '))));
			}
		}
		return sizes;
	}

	@Override
	public void close() {
		logger.detachAppender(appender);
		appender.stop();
		logger.setLevel(previousLevel);
	}
}
